package com.yammer.bridge.mobile.ws

import android.content.Context
import android.util.Log
import com.yammer.bridge.mobile.dto.ReceiptResult
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/**
 * Idempotency guard for fiscal receipts. The backend re-sends PENDING RECEIPTs
 * on reconnect (at-least-once), so the bridge must never print the same
 * requestId twice — a known id returns its cached RECEIPT_RESULT instead.
 *
 * Backed by an append-only JSONL file in the app's private storage so dedup
 * survives an app restart (double-printing a fiscal receipt is a legal problem).
 */
class ProcessedReceiptStore(context: Context) {

    private val file = File(context.filesDir, "processed-receipts.jsonl")
    private val processed = ConcurrentHashMap<String, ReceiptResult>()

    /** Request ids with a journaled print INTENT not yet superseded by an OK result or a clear. */
    private val openIntents = ConcurrentHashMap.newKeySet<String>()

    init {
        load()
    }

    fun get(requestId: String?): ReceiptResult? =
        if (requestId == null) null else processed[requestId]

    @Synchronized
    fun put(requestId: String?, result: ReceiptResult?) {
        if (requestId == null || result == null) return
        processed[requestId] = result
        openIntents.remove(requestId)
        try {
            val entry = JSONObject()
                .put("at", Instant.now().toString())
                .put("requestId", requestId)
                .put("result", result.toJson())
            appendDurably(entry.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist processed receipt $requestId: ${e.message}")
        }
    }

    /**
     * Journal the intent to print, BEFORE the fiscal receipt is opened. Fail-closed: an
     * exception means the intent could not be made durable and the caller must NOT print.
     */
    @Synchronized
    fun markIntent(requestId: String) {
        val entry = JSONObject()
            .put("at", Instant.now().toString())
            .put("requestId", requestId)
            .put("kind", "INTENT")
        appendDurably(entry.toString())
        openIntents.add(requestId)
    }

    /** Clear an intent after a clean failure (receipt provably NOT fiscalized). */
    @Synchronized
    fun clearIntent(requestId: String) {
        openIntents.remove(requestId)
        try {
            val entry = JSONObject()
                .put("at", Instant.now().toString())
                .put("requestId", requestId)
                .put("kind", "CLEAR")
            appendDurably(entry.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist intent-clear for $requestId: ${e.message}")
        }
    }

    /** An INTENT with no OK result and no clear — a previous attempt's outcome is unknown. */
    fun hasOpenIntent(requestId: String?): Boolean =
        requestId != null && openIntents.contains(requestId)

    /** Append one JSONL line and fsync it — durability is what the de-dup guarantee rests on. */
    private fun appendDurably(line: String) {
        java.io.FileOutputStream(file, true).use { out ->
            out.write((line + "\n").toByteArray())
            out.fd.sync()
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val cutoff = Instant.now().minus(TTL)
            var loaded = 0
            file.readLines().forEach { line ->
                if (line.isBlank()) return@forEach
                try {
                    val o = JSONObject(line)
                    if (!Instant.parse(o.getString("at")).isAfter(cutoff)) return@forEach
                    val requestId = o.getString("requestId")
                    when (o.optString("kind")) {
                        "INTENT" -> openIntents.add(requestId)
                        "CLEAR" -> openIntents.remove(requestId)
                        else -> {
                            processed[requestId] = ReceiptResult.fromJson(o.getJSONObject("result"))
                            openIntents.remove(requestId)
                            loaded++
                        }
                    }
                } catch (bad: Exception) {
                    Log.d(TAG, "Skipping unparseable processed-receipt line: ${bad.message}")
                }
            }
            // A result supersedes its intent regardless of line order in the file.
            openIntents.removeAll(processed.keys)
            if (openIntents.isNotEmpty()) {
                Log.w(TAG, "${openIntents.size} receipt(s) have an unresolved print INTENT — " +
                    "resends answer UNKNOWN until resolved: $openIntents")
            }
            Log.i(TAG, "Loaded $loaded processed receipt(s) for idempotency from $file")
        } catch (e: Exception) {
            Log.w(TAG, "Could not load processed-receipt store $file: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ProcessedReceiptStore"
        private val TTL: Duration = Duration.ofDays(7)
    }
}
