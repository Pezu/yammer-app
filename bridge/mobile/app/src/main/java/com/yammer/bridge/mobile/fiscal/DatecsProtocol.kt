package com.yammer.bridge.mobile.fiscal

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset

/**
 * DATECS DP-25MX protocol (the 4-nibble LEN/CMD variant), port of the desktop
 * `DatecsDPMXProtocol`. Framing is identical over TCP and USB-serial — the TCP
 * port on the register is just the serial protocol behind a socket — so this
 * class only needs an [InputStream]/[OutputStream] pair.
 *
 * SEND:    01 [LEN4] [SEQ1] [CMD4] [DATA] 05 [BCC4] 03
 * RECEIVE: 01 [LEN4] [SEQ1] [CMD4] [DATA] 04 [STATUS8] 05 [BCC4] 03
 *
 * LEN4  = 4 nibbles (each + 0x30) of (0x2A + len(DATA))
 * BCC   = byte sum from LEN4[0] through the 0x05 postamble (preamble excluded)
 */
class DatecsProtocol(
    private val input: InputStream,
    private val output: OutputStream,
    jobBudgetMillis: Long = 0L,
) {

    /** The register stayed busy past the job budget — the job must give up and answer. */
    class BusyTimeoutException(message: String) : IOException(message)

    /**
     * Absolute deadline for the whole job, or 0 for none. A paper-out register emits SYN
     * forever and the read timeout only bounds the gap BETWEEN bytes — without a total
     * budget such a job runs forever and outlives the backend's result deadline.
     */
    private val deadlineAt: Long =
        if (jobBudgetMillis <= 0L) 0L else System.currentTimeMillis() + jobBudgetMillis

    private var seq = 0x20 // SEQ starts at 0x20
    private val rxFrame = ByteArrayOutputStream()
    private var lastStatus = ByteArray(STATUS_BYTES)

    class ReceiptInfo {
        var docNumber: String = ""
        var rawResponse: String = ""
    }

    // ─── fiscal API ──────────────────────────────────────────────────────────

    /**
     * Cmd 48 — open fiscal receipt. The UNS parameter is accepted but NOT sent
     * (the device answers -112001 to the UNS syntax — same behavior as desktop).
     *
     * @return AllReceipt — total receipts issued since the last Z report,
     *         used to re-sync the UNS counter.
     */
    fun openFiscalCheck(opCode: String, opPass: String, till: String, uns: String): Long {
        val data = "$opCode\t$opPass\t$till\t"
        val resp = send(48, data)
        checkOk(48, resp)

        var allReceipt = 0L
        val parts = resp.split("\t")
        if (parts.size >= 2) {
            allReceipt = parts[1].trim().toLongOrNull() ?: 0L
        }
        Log.i(TAG, "Bon fiscal deschis (UNS=$uns, AllReceipt=$allReceipt). Raspuns: [${resp.trim()}]")
        return allReceipt
    }

    /** Cmd 49 — add an item. taxGroup: 1=A(21%), 2=B(11%), 3=C(0%). */
    fun sell(name: String, taxGroup: Int, price: Double, qty: Double) {
        val data = name + "\t" + taxGroup + "\t" +
            "%.2f".format(price) + "\t" +
            "%.3f".format(qty) + "\t\t\t1\tBUC.\t"
        val resp = send(49, data)
        checkOk(49, resp)
        Log.i(TAG, "Articol adaugat: '$name' taxGrp=$taxGroup pret=${"%.2f".format(price)} qty=${"%.3f".format(qty)}")
    }

    /** Cmd 54 — print fiscal text (e.g. thank-you note). */
    fun printFiscalText(text: String) {
        val resp = send(54, "$text\t0\t0\t0\t0\t1\t")
        checkOk(54, resp)
    }

    /** Cmd 60 — cancel any open fiscal receipt. Never throws — used for recovery. */
    fun cancelFiscalCheck() {
        try {
            val resp = send(60, "")
            Log.i(TAG, "cancelFiscalCheck raspuns: [${resp.trim()}]")
        } catch (ex: Exception) {
            Log.w(TAG, "cancelFiscalCheck ignorat (probabil nu era bon deschis): ${ex.message}")
        }
    }

    /**
     * Cmd 53 — register the payment and total the receipt.
     * payMode: 0=CASH, 1=CARD, 2=CHECK; amount 0 = exact amount (auto).
     */
    fun total(payMode: Int, amount: Double = 0.0): String {
        val amountStr = if (amount > 0) "%.2f".format(amount) else ""
        val resp = send(53, "$payMode\t$amountStr\t")
        checkOk(53, resp)
        Log.i(TAG, "Plata inregistrata payMode=$payMode amount=${if (amount > 0) "%.2f".format(amount) else "auto"}")
        return resp
    }

    /** Cmd 56 — close the fiscal receipt; returns the document number. */
    fun closeFiscalCheck(): ReceiptInfo {
        Log.i(TAG, "Inchid bonul (tiparire in progres, poate dura ~2s)...")
        val resp = send(56, "")
        checkOk(56, resp)
        Log.i(TAG, "Bon inchis. Raspuns: [${resp.trim()}]")

        return ReceiptInfo().apply {
            rawResponse = resp.trim()
            docNumber = parseDocNumber(resp)
        }
    }

    private fun parseDocNumber(resp: String): String {
        val parts = resp.split("\t")
        // parts[0] = error code ("0" = OK), parts[1] = docNum (if present)
        return if (parts.size >= 2 && parts[1].isNotBlank()) parts[1].trim() else ""
    }

    // ─── transport ───────────────────────────────────────────────────────────

    /** Sends a command and returns the DATA portion of the response. Retries on NACK. */
    fun send(cmd: Int, data: String): String {
        val dataBytes = data.toByteArray(CP1252)
        val lenVal = 0x2A + dataBytes.size

        // Packet: 01 LEN4 SEQ CMD4 DATA 05 BCC4 03
        val pkt = ByteArray(1 + 4 + 1 + 4 + dataBytes.size + 1 + 4 + 1)
        var off = 0
        pkt[off++] = 0x01
        encNibbles(pkt, off, lenVal); off += 4
        pkt[off++] = seq.toByte()
        encNibbles(pkt, off, cmd); off += 4
        System.arraycopy(dataBytes, 0, pkt, off, dataBytes.size); off += dataBytes.size
        pkt[off++] = 0x05

        var crc = 0
        for (i in 1 until off) crc += pkt[i].toInt() and 0xFF
        encNibbles(pkt, off, crc); off += 4
        pkt[off++] = 0x03

        Log.i(TAG, "→ CR cmd=$cmd seq=0x${Integer.toHexString(seq)} [${off}B]")

        repeat(MAX_RETRIES) { retry ->
            checkDeadline(cmd)
            output.write(pkt, 0, off)
            output.flush()

            // Skip SYN (0x16) / NUL bytes while the register is busy. The job deadline is
            // checked HERE: at paper-out the register emits SYN endlessly and the read
            // timeout (between bytes) never fires.
            var b: Int
            var waitBytes = 0
            do {
                checkDeadline(cmd)
                b = input.read()
                if (b < 0) throw IOException("Conexiunea inchisa de imprimanta")
                if (b == 0x16 || b == 0x00) waitBytes++
            } while (b == 0x16 || b == 0x00)
            if (waitBytes > 0) {
                Log.i(TAG, "← CR cmd=$cmd busy: $waitBytes octeti SYN/NUL inainte de raspuns")
            }

            when (b) {
                0x15 -> Log.w(TAG, "← CR cmd=$cmd NACK (0x15), retry ${retry + 1}/$MAX_RETRIES")
                0x01 -> {
                    rxFrame.reset()
                    rxFrame.write(0x01)
                    val result = receivePacket(cmd)
                    Log.i(TAG, "← CR cmd=$cmd data=[${result.trim()}]")
                    return result
                }
                else -> throw IOException("Byte neasteptat de la imprimanta: 0x" + Integer.toHexString(b))
            }
        }
        throw IOException("Cmd $cmd: max retry-uri atinse, fara raspuns valid")
    }

    /** Parses the rest of a response packet (the 0x01 preamble was already consumed). */
    private fun receivePacket(expectedCmd: Int): String {
        val lenVal = decNibbles(readFully(4))
        val dataLen = lenVal - 0x33
        if (dataLen < 0) throw IOException("LEN raspuns invalid: $lenVal")

        val seqByte = readByte()
        if (seqByte != seq) {
            Log.w(TAG, "SEQ mismatch: asteptat 0x${Integer.toHexString(seq)} primit 0x${Integer.toHexString(seqByte)}")
        }

        val respCmd = decNibbles(readFully(4))
        if (respCmd != expectedCmd) {
            Log.w(TAG, "CMD mismatch: asteptat $expectedCmd primit $respCmd")
        }

        val dataBytes = readFully(dataLen)

        val sep = readByte()
        if (sep != 0x04) throw IOException("Separator asteptat 0x04, primit 0x" + Integer.toHexString(sep))

        lastStatus = readFully(STATUS_BYTES)

        val post = readByte()
        if (post != 0x05) throw IOException("Postamble asteptat 0x05, primit 0x" + Integer.toHexString(post))

        readFully(4) // BCC — not verified

        val term = readByte()
        if (term != 0x03) throw IOException("Terminator asteptat 0x03, primit 0x" + Integer.toHexString(term))

        seq++
        if (seq > 0x7F) seq = 0x20

        val st = DatecsStatusDecoder.decode(lastStatus)
        when {
            st.hasErrors() -> Log.e(TAG, "Status casa (cmd $expectedCmd): ERORI=[${st.summary()}] | ${DatecsStatusDecoder.toHex(lastStatus)}")
            st.hasWarnings() -> Log.w(TAG, "Status casa (cmd $expectedCmd): avertismente=[${st.warnings.joinToString("; ")}]")
        }

        return String(dataBytes, CP1252)
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun checkOk(cmd: Int, resp: String) {
        if (resp.isEmpty() || resp[0] != '0') {
            val st = DatecsStatusDecoder.decode(lastStatus)
            val statusInfo = if (st.hasErrors()) " | stare casa: " + st.summary() else ""
            throw IOException("Eroare cmd $cmd: [${resp.trim()}]$statusInfo")
        }
    }

    private fun encNibbles(buf: ByteArray, off: Int, value: Int) {
        buf[off] = (((value shr 12) and 0xF) + 0x30).toByte()
        buf[off + 1] = (((value shr 8) and 0xF) + 0x30).toByte()
        buf[off + 2] = (((value shr 4) and 0xF) + 0x30).toByte()
        buf[off + 3] = ((value and 0xF) + 0x30).toByte()
    }

    private fun decNibbles(b4: ByteArray): Int =
        ((b4[0] - 0x30) shl 12) or ((b4[1] - 0x30) shl 8) or
            ((b4[2] - 0x30) shl 4) or (b4[3] - 0x30).toInt()

    private fun readByte(): Int {
        val b = input.read()
        if (b < 0) throw IOException("EOF la citire byte")
        rxFrame.write(b)
        return b
    }

    private fun readFully(len: Int): ByteArray {
        val buf = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = input.read(buf, read, len - read)
            if (n < 0) throw IOException("EOF la citire $len bytes")
            read += n
        }
        rxFrame.write(buf, 0, read)
        return buf
    }

    private fun checkDeadline(cmd: Int) {
        if (deadlineAt > 0 && System.currentTimeMillis() > deadlineAt) {
            throw BusyTimeoutException(
                "Cmd $cmd: casa de marcat ocupata peste bugetul de timp al jobului" +
                    " (probabil fara hartie sau blocata)"
            )
        }
    }

    companion object {
        private const val TAG = "DatecsProtocol"
        private const val STATUS_BYTES = 8
        private const val MAX_RETRIES = 3
        private val CP1252: Charset = Charset.forName("windows-1252")

        const val PAY_CASH = 0
        const val PAY_CARD = 1
        const val PAY_CHECK = 2
    }
}
