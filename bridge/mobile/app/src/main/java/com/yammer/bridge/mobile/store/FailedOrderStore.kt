package com.yammer.bridge.mobile.store

import android.content.Context
import android.util.Log
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import org.json.JSONObject

/**
 * Persistent list of print jobs that failed (bonuri netiparite). Keyed by
 * requestId: a later successful retry (the backend re-sends PENDING receipts)
 * removes the entry, so the list always shows only the still-unresolved orders.
 *
 * Backed by a small JSON-lines file rewritten on every change.
 */
object FailedOrderStore {

    data class FailedOrder(
        val at: Instant,
        val requestId: String,
        val kind: String,           // FISCAL | NON-FISCAL | PROFORMA
        val paymentMethod: String?,
        val total: BigDecimal?,
        val table: String?,
        val errorCode: String?,
        val errorMessage: String?,
        /** The original wire frame — lets the app show the items and retry the print locally. */
        val raw: String? = null,
    )

    private const val TAG = "FailedOrderStore"

    private lateinit var file: File
    private val orders = LinkedHashMap<String, FailedOrder>()

    @Synchronized
    fun init(context: Context) {
        file = File(context.filesDir, "failed-receipts.jsonl")
        load()
    }

    @Synchronized
    fun record(order: FailedOrder) {
        orders[order.requestId] = order
        persist()
    }

    /** Called when a receipt eventually prints OK — it is no longer a failed order. */
    @Synchronized
    fun resolve(requestId: String) {
        if (orders.remove(requestId) != null) {
            persist()
        }
    }

    @Synchronized
    fun clear() {
        orders.clear()
        persist()
    }

    /** Newest first. */
    @Synchronized
    fun list(): List<FailedOrder> = orders.values.sortedByDescending { it.at }

    private fun persist() {
        try {
            file.writeText(orders.values.joinToString("\n") { toJson(it).toString() })
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist failed orders: ${e.message}")
        }
    }

    private fun load() {
        orders.clear()
        if (!file.exists()) return
        try {
            file.readLines().forEach { line ->
                if (line.isBlank()) return@forEach
                try {
                    val o = fromJson(JSONObject(line))
                    orders[o.requestId] = o
                } catch (bad: Exception) {
                    Log.d(TAG, "Skipping unparseable failed-order line: ${bad.message}")
                }
            }
            Log.i(TAG, "Loaded ${orders.size} failed order(s) from $file")
        } catch (e: Exception) {
            Log.w(TAG, "Could not load failed orders: ${e.message}")
        }
    }

    private fun toJson(o: FailedOrder): JSONObject = JSONObject().apply {
        put("at", o.at.toString())
        put("requestId", o.requestId)
        put("kind", o.kind)
        putOpt("paymentMethod", o.paymentMethod)
        putOpt("total", o.total)
        putOpt("table", o.table)
        putOpt("errorCode", o.errorCode)
        putOpt("errorMessage", o.errorMessage)
        putOpt("raw", o.raw)
    }

    private fun fromJson(o: JSONObject): FailedOrder = FailedOrder(
        at = Instant.parse(o.getString("at")),
        requestId = o.getString("requestId"),
        kind = o.optString("kind", "FISCAL"),
        paymentMethod = if (o.isNull("paymentMethod")) null else o.optString("paymentMethod"),
        total = if (o.isNull("total")) null else BigDecimal(o.get("total").toString()),
        table = if (o.isNull("table")) null else o.optString("table"),
        errorCode = if (o.isNull("errorCode")) null else o.optString("errorCode"),
        errorMessage = if (o.isNull("errorMessage")) null else o.optString("errorMessage"),
        raw = if (o.isNull("raw")) null else o.optString("raw"),
    )
}
