package com.yammer.bridge.mobile.dto

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire DTOs for the backend bridge WebSocket — same JSON shapes as the desktop
 * bridge (`com.yammer.bridge.dto`). Parsed/serialized by hand with org.json to
 * keep the payloads byte-compatible without a serialization framework.
 */

/** A receipt to print. `fiscal=true` → Datecs register; otherwise ESC/POS thermal printer. */
data class ReceiptRequest(
    val requestId: String,
    val fiscal: Boolean,
    val paymentMethod: String?,
    val cashRegister: String?,
    val printerIp: String?,
    /** Operator verified a previous ambiguous attempt did NOT print — clear the intent and print. */
    val clearIntent: Boolean = false,
    val lines: List<Line>,
) {
    data class Line(
        val name: String,
        val quantity: Double,
        val unitPrice: BigDecimal,
        val vat: BigDecimal?,
        /** Outside the VAT scope (tips): the register's exempt group, not the 0 % group. */
        val exempt: Boolean = false,
    )

    companion object {
        fun fromJson(o: JSONObject): ReceiptRequest = ReceiptRequest(
            requestId = o.getString("requestId"),
            fiscal = o.optBoolean("fiscal", false),
            paymentMethod = o.optStringOrNull("paymentMethod"),
            cashRegister = o.optStringOrNull("cashRegister"),
            printerIp = o.optStringOrNull("printerIp"),
            clearIntent = o.optBoolean("clearIntent", false),
            lines = o.optJSONArray("lines").mapObjects {
                Line(
                    name = it.optString("name", ""),
                    quantity = it.optDouble("quantity", 0.0),
                    unitPrice = it.optBigDecimal("unitPrice") ?: BigDecimal.ZERO,
                    vat = it.optBigDecimal("vat"),
                    exempt = it.optBoolean("exempt", false),
                )
            },
        )
    }
}

/** A non-fiscal proforma bill for the ESC/POS thermal printer. */
data class InfoReceiptRequest(
    val requestId: String,
    val printerIp: String?,
    val table: String?,
    val waiter: String?,
    val company: Company?,
    val orderNos: List<Int>,
    val lines: List<Line>,
    val total: BigDecimal?,
) {
    data class Line(
        val name: String,
        val quantity: Int?,
        val unitPrice: BigDecimal?,
        val lineTotal: BigDecimal?,
    )

    data class Company(
        val name: String?, val cui: String?, val regCom: String?,
        val address: String?, val phone: String?,
    )

    companion object {
        fun fromJson(o: JSONObject): InfoReceiptRequest = InfoReceiptRequest(
            requestId = o.getString("requestId"),
            printerIp = o.optStringOrNull("printerIp"),
            table = o.optStringOrNull("table"),
            waiter = o.optStringOrNull("waiter"),
            company = o.optJSONObject("company")?.let {
                Company(
                    it.optStringOrNull("name"), it.optStringOrNull("cui"),
                    it.optStringOrNull("regCom"), it.optStringOrNull("address"),
                    it.optStringOrNull("phone"),
                )
            },
            orderNos = o.optJSONArray("orderNos").let { arr ->
                if (arr == null) emptyList() else (0 until arr.length()).map { arr.getInt(it) }
            },
            lines = o.optJSONArray("lines").mapObjects {
                Line(
                    name = it.optString("name", ""),
                    quantity = if (it.has("quantity") && !it.isNull("quantity")) it.getInt("quantity") else null,
                    unitPrice = it.optBigDecimal("unitPrice"),
                    lineTotal = it.optBigDecimal("lineTotal"),
                )
            },
            total = o.optBigDecimal("total"),
        )
    }
}

/** Result of a print job, sent back to the backend as a `RECEIPT_RESULT` frame. */
/**
 * End-of-day "final report": one slip per waiter (card/cash takings + tips), the printer
 * cutting after each slip. Separate frame type — the proforma is untouched.
 */
data class WaiterReportRequest(
    val requestId: String,
    val printerIp: String?,
    val eventName: String?,
    val rows: List<Row>,
) {
    data class Row(
        val userName: String,
        val paidCard: BigDecimal?,
        val paidCash: BigDecimal?,
        val tipCard: BigDecimal?,
        val tipCash: BigDecimal?,
    )

    companion object {
        fun fromJson(o: JSONObject): WaiterReportRequest = WaiterReportRequest(
            requestId = o.getString("requestId"),
            printerIp = o.optStringOrNull("printerIp"),
            eventName = o.optStringOrNull("eventName"),
            rows = o.optJSONArray("rows").mapObjects {
                Row(
                    userName = it.optString("userName", ""),
                    paidCard = it.optBigDecimal("paidCard"),
                    paidCash = it.optBigDecimal("paidCash"),
                    tipCard = it.optBigDecimal("tipCard"),
                    tipCash = it.optBigDecimal("tipCash"),
                )
            },
        )
    }
}

data class ReceiptResult(
    val status: String,
    val requestId: String,
    val receiptNumber: String? = null,
    val fiscalReceiptId: String? = null,
    val cashRegisterSerial: String? = null,
    val issuedAt: LocalDateTime? = null,
    val totalAmount: BigDecimal? = null,
    val paymentMethod: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("status", status)
        put("requestId", requestId)
        putOpt("receiptNumber", receiptNumber)
        putOpt("fiscalReceiptId", fiscalReceiptId)
        putOpt("cashRegisterSerial", cashRegisterSerial)
        putOpt("issuedAt", issuedAt?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        putOpt("totalAmount", totalAmount)
        putOpt("paymentMethod", paymentMethod)
        putOpt("errorCode", errorCode)
        putOpt("errorMessage", errorMessage)
    }

    companion object {
        const val OK = "OK"
        const val ERROR = "ERROR"

        /** A print attempt may or may not have produced a fiscal document — operator must verify. */
        const val UNKNOWN = "UNKNOWN"

        fun error(requestId: String, paymentMethod: String?, code: String, message: String?) =
            ReceiptResult(
                status = ERROR,
                requestId = requestId,
                paymentMethod = paymentMethod,
                issuedAt = LocalDateTime.now(),
                errorCode = code,
                errorMessage = message,
            )

        /** Ambiguous outcome: the receipt may have printed but confirmation was lost. */
        fun unknown(requestId: String, paymentMethod: String?, code: String, message: String?) =
            ReceiptResult(
                status = UNKNOWN,
                requestId = requestId,
                paymentMethod = paymentMethod,
                issuedAt = LocalDateTime.now(),
                errorCode = code,
                errorMessage = message,
            )

        /** Re-hydrate a cached result (idempotency store). */
        fun fromJson(o: JSONObject): ReceiptResult = ReceiptResult(
            status = o.getString("status"),
            requestId = o.getString("requestId"),
            receiptNumber = o.optStringOrNull("receiptNumber"),
            fiscalReceiptId = o.optStringOrNull("fiscalReceiptId"),
            cashRegisterSerial = o.optStringOrNull("cashRegisterSerial"),
            issuedAt = o.optStringOrNull("issuedAt")?.let {
                runCatching { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull()
            },
            totalAmount = o.optBigDecimal("totalAmount"),
            paymentMethod = o.optStringOrNull("paymentMethod"),
            errorCode = o.optStringOrNull("errorCode"),
            errorMessage = o.optStringOrNull("errorMessage"),
        )
    }
}

// ─── org.json helpers ────────────────────────────────────────────────────────

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

internal fun JSONObject.optBigDecimal(key: String): BigDecimal? =
    if (has(key) && !isNull(key)) BigDecimal(get(key).toString()) else null

internal fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).map { transform(getJSONObject(it)) }
