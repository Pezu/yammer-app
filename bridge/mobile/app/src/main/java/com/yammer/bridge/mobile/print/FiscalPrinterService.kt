package com.yammer.bridge.mobile.print

import android.util.Log
import com.yammer.bridge.mobile.BridgeState
import com.yammer.bridge.mobile.Prefs
import com.yammer.bridge.mobile.dto.ReceiptRequest
import com.yammer.bridge.mobile.dto.ReceiptResult
import com.yammer.bridge.mobile.fiscal.DatecsErrorMapper
import com.yammer.bridge.mobile.fiscal.DatecsProtocol
import com.yammer.bridge.mobile.usb.UsbRegisterManager
import com.yammer.bridge.mobile.ws.ProcessedReceiptStore
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.InetSocketAddress
import java.net.Socket
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Prints a fiscal receipt on the DATECS DP-25MX. Port of the desktop
 * `FiscalPrinterService`, with the transport swapped: the register attached
 * over **USB** is preferred; when no USB device is present the service falls
 * back to the request's `cashRegister` LAN IP over TCP (desktop behavior).
 */
class FiscalPrinterService(
    private val prefs: Prefs,
    private val usb: UsbRegisterManager,
    private val processedStore: ProcessedReceiptStore,
) {

    /** Unique-Number-of-Sale counter per device key ("usb" or the LAN IP). */
    private val unsSeqPerDevice = ConcurrentHashMap<String, AtomicLong>()

    fun print(request: ReceiptRequest): ReceiptResult {
        Log.i(TAG, "Fiscal print: requestId=${request.requestId} cashRegister=${request.cashRegister} " +
            "method=${request.paymentMethod} lines=${request.lines.size}")

        val useUsb = usb.findDriver() != null
        if (!useUsb && request.cashRegister.isNullOrBlank()) {
            return ReceiptResult.error(
                request.requestId, request.paymentMethod,
                "NO_DEVICE", "Nicio casa de marcat pe USB si niciun IP in cerere."
            )
        }
        val deviceKey = if (useUsb) DEVICE_USB else request.cashRegister!!.trim()

        // Exactly-once guards. (1) Already printed → cached result, never re-print.
        // (2) Unresolved print INTENT (bridge died mid-print) → ambiguous, answer UNKNOWN.
        processedStore.get(request.requestId)?.let { cached ->
            Log.i(TAG, "Receipt requestId=${request.requestId} already printed — returning cached result.")
            return cached
        }
        if (request.clearIntent && processedStore.hasOpenIntent(request.requestId)) {
            // Operator's verdict from the backend: the ambiguous attempt did not print. The
            // cached-result check above still wins — a mistaken verdict cannot cause a
            // duplicate when this bridge has the OK on record.
            Log.w(TAG, "Receipt requestId=${request.requestId}: operator authorized reprint — clearing intent.")
            processedStore.clearIntent(request.requestId)
        }
        if (processedStore.hasOpenIntent(request.requestId)) {
            Log.w(TAG, "Receipt requestId=${request.requestId} has an unresolved print INTENT — UNKNOWN.")
            return ReceiptResult.unknown(
                request.requestId, request.paymentMethod,
                "PRIOR_ATTEMPT_UNCONFIRMED",
                "O incercare anterioara s-a intrerupt in timpul tiparirii — bonul poate fi deja emis. " +
                    "Verificati pe casa de marcat si rezolvati manual din aplicatie.",
            )
        }

        return try {
            openTransport(useUsb, request.cashRegister).use { t ->
                val fp = DatecsProtocol(t.input, t.output, JOB_BUDGET_MS)

                // Write-ahead intent, fail-closed: not durable → do NOT print.
                try {
                    processedStore.markIntent(request.requestId)
                } catch (journal: Exception) {
                    Log.e(TAG, "Could not journal print intent for ${request.requestId}: ${journal.message}")
                    return ReceiptResult.error(
                        request.requestId, request.paymentMethod,
                        "JOURNAL_ERROR",
                        "Jurnalul de bonuri nu poate fi scris — tiparirea a fost refuzata " +
                            "pentru a exclude un bon dublu.",
                    )
                }

                val closeAttempted = booleanArrayOf(false)
                val allReceipt = longArrayOf(0L)
                try {
                    val result = doPrint(fp, request, deviceKey, closeAttempted, allReceipt)
                    // Journal the OK here so every caller gets the de-dup guarantee.
                    processedStore.put(request.requestId, result)
                    result
                } catch (ex: Exception) {
                    if (closeAttempted[0]) {
                        // Close was sent — the register may have fiscalized even though the
                        // confirmation was lost. Ask the register itself before giving up.
                        Log.e(TAG, "Fiscal print AMBIGUOUS requestId=${request.requestId}: ${ex.message}", ex)
                        return settleLostClose(fp, request, deviceKey, allReceipt[0], ex)
                    }
                    // Failure before close: not fiscalized (recovery voids any half-open receipt).
                    processedStore.clearIntent(request.requestId)
                    throw ex
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Fiscal print failed requestId=${request.requestId}: ${ex.message}", ex)
            val err = DatecsErrorMapper.map(ex, deviceKey)
            ReceiptResult.error(request.requestId, request.paymentMethod, err.code, err.message)
        }
    }

    private interface Transport : Closeable {
        val input: InputStream
        val output: OutputStream
    }

    private fun openTransport(useUsb: Boolean, host: String?): Transport =
        if (useUsb) {
            val conn = usb.open(prefs.baudRate)
            object : Transport {
                override val input = conn.input
                override val output = conn.output
                override fun close() = conn.close()
            }
        } else {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, prefs.tcpPort), CONNECT_TIMEOUT_MS)
            socket.soTimeout = 15_000
            object : Transport {
                override val input = socket.getInputStream()
                override val output = socket.getOutputStream()
                override fun close() = socket.close()
            }
        }

    /**
     * The close confirmation was lost. Read the register's current-receipt info (cmd 76):
     *  - a receipt is still OPEN → the close never happened: void it, clear the intent and
     *    report a retryable error (the resend prints a fresh receipt);
     *  - no receipt open and the reported number is the one we expected → the close DID
     *    happen: journal it and report OK with that receipt number;
     *  - anything else (register unreachable, unexpected layout/number) → UNKNOWN, intent kept,
     *    an operator settles it in the backoffice as before.
     */
    private fun settleLostClose(
        fp: DatecsProtocol,
        request: ReceiptRequest,
        deviceKey: String,
        allReceipt: Long,
        cause: Exception,
    ): ReceiptResult {
        val raw = try {
            fp.readCurrentReceipt()
        } catch (probe: Exception) {
            Log.w(TAG, "Lost close for ${request.requestId}: register unreachable for verification (${probe.message})")
            BridgeState.log("⚠ Bon ${request.requestId}: confirmarea inchiderii s-a pierdut si casa nu raspunde la verificare.")
            return closeUnconfirmed(request, "${cause.message}; verificare imposibila: ${probe.message}")
        }
        BridgeState.log("Verificare bon ${request.requestId} (cmd 76): ${raw.replace("\t", " | ")}")
        val parts = raw.split("\t")
        val isOpen = parts.getOrNull(1)?.trim()
        val number = parts.getOrNull(2)?.trim()?.toLongOrNull()
        if (isOpen == "1") {
            // The receipt is still open on the register: nothing was fiscalized. Void it so the
            // next job starts clean, and let the backend retry with a fresh print.
            fp.cancelFiscalCheck()
            processedStore.clearIntent(request.requestId)
            BridgeState.log("✗ Bon ${request.requestId}: inchiderea NU s-a facut — bonul deschis a fost anulat.")
            return ReceiptResult.error(
                request.requestId, request.paymentMethod,
                "CLOSE_FAILED_VOIDED",
                "Inchiderea bonului a esuat (${cause.message}); bonul deschis a fost anulat pe casa. Reincercati.",
            )
        }
        val expected = if (allReceipt > 0) setOf(allReceipt, allReceipt + 1) else emptySet()
        if (isOpen == "0" && number != null && number in expected) {
            val result = ReceiptResult(
                status = ReceiptResult.OK,
                requestId = request.requestId,
                receiptNumber = number.toString(),
                fiscalReceiptId = number.toString(),
                cashRegisterSerial = prefs.serialNumber.ifEmpty { deviceKey },
                issuedAt = LocalDateTime.now(),
                totalAmount = calculateTotal(request),
                paymentMethod = request.paymentMethod,
            )
            processedStore.put(request.requestId, result)
            BridgeState.log("✓ Bon ${request.requestId}: casa confirma bonul nr=$number (inchiderea reusise).")
            return result
        }
        return closeUnconfirmed(request, "${cause.message}; cmd 76: $raw (asteptat nr ${expected.joinToString("/")})")
    }

    private fun closeUnconfirmed(request: ReceiptRequest, detail: String): ReceiptResult =
        ReceiptResult.unknown(
            request.requestId, request.paymentMethod,
            "CLOSE_UNCONFIRMED",
            "Confirmarea inchiderii bonului s-a pierdut — bonul poate fi emis. " +
                "Verificati pe casa de marcat si rezolvati manual din aplicatie. ($detail)",
        )

    private fun doPrint(
        fp: DatecsProtocol,
        request: ReceiptRequest,
        deviceKey: String,
        closeAttempted: BooleanArray,
        allReceiptOut: LongArray,
    ): ReceiptResult {
        // 1. Recovery — cancel a previously left-open receipt (cmd 60).
        fp.cancelFiscalCheck()

        // 2. Open the fiscal receipt with a per-register unique sale number (cmd 48).
        val uns = generateUns(deviceKey)
        val allReceipt = fp.openFiscalCheck(prefs.operatorCode, prefs.operatorPass, prefs.tillNumber, uns)
        allReceiptOut[0] = allReceipt
        syncUnsCounter(deviceKey, allReceipt)

        // 3. Lines (cmd 49).
        for (line in request.lines) {
            fp.sell(line.name, resolveTaxGroup(line), line.unitPrice.toDouble(), line.quantity)
        }

        // 4. Fiscal footer text (cmd 54).
        fp.printFiscalText("Multumim pentru vizita!")

        // 5. Total with the payment method (cmd 53).
        fp.total(resolvePayMode(request.paymentMethod))

        // 6. Close the receipt and read the document number (cmd 56). From here the outcome
        // is ambiguous on failure: the register may fiscalize even if the response is lost.
        closeAttempted[0] = true
        val info = fp.closeFiscalCheck()
        val docNumber = info.docNumber.ifEmpty { null }

        val result = ReceiptResult(
            status = ReceiptResult.OK,
            requestId = request.requestId,
            receiptNumber = docNumber,
            fiscalReceiptId = docNumber,
            cashRegisterSerial = prefs.serialNumber.ifEmpty { deviceKey },
            issuedAt = LocalDateTime.now(),
            totalAmount = calculateTotal(request),
            paymentMethod = request.paymentMethod,
        )
        Log.i(TAG, "Fiscal print OK: requestId=${result.requestId} receiptNumber=${result.receiptNumber} " +
            "total=${result.totalAmount}")
        return result
    }

    private fun generateUns(deviceKey: String): String {
        val seq = unsSeqPerDevice.computeIfAbsent(deviceKey) { AtomicLong(0) }.incrementAndGet()
        return "%s-%s-%07d".format(LocalDate.now().format(DATE_FMT), formatTill(prefs.tillNumber), seq)
    }

    private fun syncUnsCounter(deviceKey: String, allReceipt: Long) {
        if (allReceipt <= 0L) return
        val counter = unsSeqPerDevice.computeIfAbsent(deviceKey) { AtomicLong(0) }
        val prev = counter.getAndSet(allReceipt)
        if (prev != allReceipt) {
            Log.i(TAG, "UNS sync [$deviceKey]: $prev -> $allReceipt (AllReceipt from device)")
        }
    }

    private fun formatTill(till: String): String =
        till.trim().toLongOrNull()?.let { "%04d".format(it) } ?: (till + "0000").substring(0, 4)

    /** Exempt lines (tips) → the register's "scutit" code; everything else by VAT percentage. */
    private fun resolveTaxGroup(line: ReceiptRequest.Line): Int {
        if (line.exempt) return prefs.exemptTaxGroup
        val vat = line.vat ?: return 1
        return VAT_TO_TAX_GROUP[vat.setScale(0, RoundingMode.HALF_UP).toInt()] ?: 1
    }

    private fun resolvePayMode(paymentMethod: String?): Int =
        PAYMENT_TO_PAYMODE[paymentMethod?.uppercase()] ?: DatecsProtocol.PAY_CASH

    private fun calculateTotal(request: ReceiptRequest): BigDecimal =
        request.lines.fold(BigDecimal.ZERO) { acc, l ->
            acc + l.unitPrice.multiply(BigDecimal.valueOf(l.quantity)).setScale(2, RoundingMode.HALF_UP)
        }

    companion object {
        private const val TAG = "FiscalPrinterService"
        const val DEVICE_USB = "usb"
        private const val CONNECT_TIMEOUT_MS = 8_000

        /**
         * Total budget for one print job. Must stay strictly below the backend's
         * bridge.fiscal-result-timeout-seconds (180s) — see the desktop bridge.
         */
        private const val JOB_BUDGET_MS = 90_000L
        private val DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE

        /** VAT% → DATECS tax code: 1=A (21%), 2=B (11%), 3=C (0%). Exempt lines use Prefs.exemptTaxGroup (6). */
        private val VAT_TO_TAX_GROUP = mapOf(21 to 1, 11 to 2, 0 to 3)

        private val PAYMENT_TO_PAYMODE = mapOf(
            "CASH" to DatecsProtocol.PAY_CASH,
            "CARD" to DatecsProtocol.PAY_CARD,
            "CHECK" to DatecsProtocol.PAY_CHECK,
            "CEC" to DatecsProtocol.PAY_CHECK,
        )
    }
}
