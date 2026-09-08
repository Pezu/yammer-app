package com.yammer.bridge.mobile.print

import android.util.Log
import com.yammer.bridge.mobile.dto.InfoReceiptRequest
import com.yammer.bridge.mobile.dto.ReceiptRequest
import com.yammer.bridge.mobile.dto.ReceiptResult
import java.io.IOException
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

/**
 * Prints non-fiscal bills on an ESC/POS thermal printer over raw TCP to ip:9100.
 * Direct port of the desktop `EscPosThermalService` (same layout, byte for byte).
 */
class EscPosThermalService {

    fun print(payload: InfoReceiptRequest): ReceiptResult {
        val host = payload.printerIp
        Log.i(TAG, "Info print: requestId=${payload.requestId} printer=$host:$PORT table=${payload.table}")
        if (host.isNullOrBlank()) {
            return ReceiptResult.error(payload.requestId, null, "NO_DEVICE", "Lipseste IP-ul imprimantei")
        }

        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS)
                socket.tcpNoDelay = true
                val out = socket.getOutputStream()

                out.write(INIT)
                out.write(ALIGN_CENTER)

                out.write(BOLD_ON)
                out.write(DOUBLE_ON)
                writeLine(out, "RENDEZVOUS")
                out.write(DOUBLE_OFF)
                out.write(BOLD_OFF)
                writeLine(out, sep())

                out.write(BOLD_ON)
                writeLine(out, "PROFORMA")
                out.write(BOLD_OFF)
                writeLine(out, "NU ESTE BON FISCAL")
                if (!payload.table.isNullOrBlank()) writeLine(out, "Punct: ${payload.table}")
                if (!payload.waiter.isNullOrBlank()) writeLine(out, "Ospatar: ${payload.waiter}")

                out.write(ALIGN_LEFT)
                writeLine(out, sep())
                // products printed taller (bigger) than the rest
                out.write(TALL_ON)
                for (line in payload.lines) {
                    val qty = line.quantity ?: 0
                    writeLine(out, twoCols("${qty}x " + plain(line.name), money(line.lineTotal)))
                }
                out.write(DOUBLE_OFF)
                writeLine(out, sep())

                out.write(BOLD_ON)
                writeLine(out, twoCols("TOTAL", money(payload.total)))
                out.write(BOLD_OFF)

                // tip options — smaller font, the customer ticks one by pen
                writeLine(out, "")
                out.write(FONT_B)
                writeLine(out, "Tips:")
                writeLine(out, "[ ] 10%")
                writeLine(out, "[ ] 12%")
                writeLine(out, "[ ] 15%")
                val suma = "[ ] Suma: "
                writeLine(out, suma + "_".repeat(maxOf(0, FONT_B_WIDTH - suma.length)))
                out.write(FONT_A)

                out.write(ALIGN_CENTER)
                repeat(5) { writeLine(out, "") }
                out.write(BOLD_ON)
                out.write(DOUBLE_ON)
                writeLine(out, "Va multumim!")
                out.write(DOUBLE_OFF)
                out.write(BOLD_OFF)

                out.write(FEED_LINES)
                out.write(FEED_AND_CUT)
                out.flush()
                settle(socket)
                Log.i(TAG, "Info print OK: requestId=${payload.requestId} printer=$host")
                ReceiptResult(
                    status = ReceiptResult.OK,
                    requestId = payload.requestId,
                    totalAmount = payload.total,
                    issuedAt = LocalDateTime.now(),
                )
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Info print failed requestId=${payload.requestId} printer=$host: ${ex.message}", ex)
            ReceiptResult.error(payload.requestId, null, "PRINT_ERROR", ex.message)
        }
    }

    /** Non-fiscal receipt of a sale (fallback when the job is marked non-fiscal). */
    fun printReceipt(payload: ReceiptRequest): ReceiptResult {
        val host = payload.printerIp
        Log.i(TAG, "Non-fiscal receipt: requestId=${payload.requestId} printer=$host:$PORT method=${payload.paymentMethod}")
        if (host.isNullOrBlank()) {
            return ReceiptResult.error(payload.requestId, payload.paymentMethod, "NO_DEVICE", "Lipseste IP-ul imprimantei")
        }

        var total = BigDecimal.ZERO
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS)
                socket.tcpNoDelay = true
                val out = socket.getOutputStream()

                out.write(INIT)
                out.write(ALIGN_CENTER)
                out.write(BOLD_ON)
                writeLine(out, "BON")
                out.write(BOLD_OFF)
                writeLine(out, "NU ESTE BON FISCAL")

                out.write(ALIGN_LEFT)
                writeLine(out, sep())
                for (line in payload.lines) {
                    val lineTotal = line.unitPrice
                        .multiply(BigDecimal.valueOf(line.quantity))
                        .setScale(2, RoundingMode.HALF_UP)
                    total = total.add(lineTotal)
                    writeLine(out, twoCols(qtyLabel(line.quantity) + "x " + plain(line.name), money(lineTotal)))
                }
                writeLine(out, sep())

                out.write(BOLD_ON)
                writeLine(out, twoCols("TOTAL", money(total)))
                out.write(BOLD_OFF)
                payload.paymentMethod?.let { writeLine(out, twoCols("Plata", it)) }

                out.write(ALIGN_CENTER)
                writeLine(out, "")
                writeLine(out, "Va multumim!")

                out.write(FEED_LINES)
                out.write(FEED_AND_CUT)
                out.flush()
                settle(socket)
                Log.i(TAG, "Non-fiscal receipt OK: requestId=${payload.requestId} printer=$host total=$total")
                ReceiptResult(
                    status = ReceiptResult.OK,
                    requestId = payload.requestId,
                    totalAmount = total,
                    paymentMethod = payload.paymentMethod,
                    issuedAt = LocalDateTime.now(),
                )
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Non-fiscal receipt failed requestId=${payload.requestId} printer=$host: ${ex.message}", ex)
            ReceiptResult.error(payload.requestId, payload.paymentMethod, "PRINT_ERROR", ex.message)
        }
    }

    /** Whole quantities without the trailing ".0" (port of desktop `Qty`). */
    private fun qtyLabel(qty: Double): String =
        if (qty == Math.floor(qty)) qty.toLong().toString() else qty.toString()

    /**
     * Some thermal printers discard unprinted data on an abrupt disconnect. Half-close
     * the write side, then pause so the printer drains its buffer before the socket closes.
     */
    private fun settle(socket: Socket) {
        try {
            socket.shutdownOutput()
        } catch (_: IOException) {
        }
        try {
            Thread.sleep(600)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun writeLine(out: OutputStream, text: String) {
        out.write(transliterate(text).toByteArray(StandardCharsets.US_ASCII))
        out.write(0x0A)
    }

    private fun twoCols(leftIn: String, right: String, width: Int = LINE_WIDTH): String {
        var left = transliterate(leftIn)
        val r = transliterate(right)
        val pad = width - r.length
        if (left.length > pad - 1) {
            left = left.substring(0, maxOf(0, pad - 1))
        }
        val spaces = maxOf(1, width - left.length - r.length)
        return left + " ".repeat(spaces) + r
    }

    private fun sep(): String = "-".repeat(LINE_WIDTH)

    private fun money(v: BigDecimal?): String = "%.2f RON".format(v ?: BigDecimal.ZERO)

    /** Strip rich-text product names down to the plain title on one line. */
    private fun plain(html: String?): String {
        if (html == null) return ""
        val s = html
            .replace(Regex("(?is)<font[^>]*size=[\"']?1[\"']?[^>]*>.*?</font>"), "")
            .replace(Regex("(?is)<small\\b[^>]*>.*?</small>"), "")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|li)>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
        for (line in s.split("\n")) {
            val t = line.trim().replace(Regex("\\s+"), " ")
            if (t.isNotEmpty()) return t
        }
        return ""
    }

    /** Romanian diacritics → ASCII; any other non-ASCII becomes '?'. */
    private fun transliterate(input: String?): String {
        if (input == null) return ""
        return input
            .replace('ă', 'a').replace('Ă', 'A')
            .replace('â', 'a').replace('Â', 'A')
            .replace('î', 'i').replace('Î', 'I')
            .replace('ș', 's').replace('Ș', 'S').replace('ş', 's').replace('Ş', 'S')
            .replace('ț', 't').replace('Ț', 'T').replace('ţ', 't').replace('Ţ', 'T')
            .replace('‘', '\'').replace('’', '\'')
            .replace('‚', '\'').replace('′', '\'')
            .replace('“', '"').replace('”', '"')
            .replace('–', '-').replace('—', '-')
            .replace(' ', ' ')
            .replace("…", "...")
            .replace(Regex("[^\\x20-\\x7E]"), "?")
    }

    companion object {
        private const val TAG = "EscPosThermal"
        private const val PORT = 9100
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val LINE_WIDTH = 48
        private const val FONT_B_WIDTH = 64

        private val INIT = byteArrayOf(0x1B, 0x40)
        private val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
        private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
        private val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
        private val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
        private val DOUBLE_ON = byteArrayOf(0x1D, 0x21, 0x11)
        private val DOUBLE_OFF = byteArrayOf(0x1D, 0x21, 0x00)
        private val TALL_ON = byteArrayOf(0x1D, 0x21, 0x01)
        private val FONT_A = byteArrayOf(0x1B, 0x4D, 0x00)
        private val FONT_B = byteArrayOf(0x1B, 0x4D, 0x01)
        private val FEED_LINES = byteArrayOf(0x1B, 0x64, 0x06)
        private val FEED_AND_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x03)
    }
}
