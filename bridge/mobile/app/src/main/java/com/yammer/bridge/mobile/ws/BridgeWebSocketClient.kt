package com.yammer.bridge.mobile.ws

import android.util.Log
import com.yammer.bridge.mobile.BridgeState
import com.yammer.bridge.mobile.Prefs
import com.yammer.bridge.mobile.dto.InfoReceiptRequest
import com.yammer.bridge.mobile.dto.ReceiptRequest
import com.yammer.bridge.mobile.dto.ReceiptResult
import com.yammer.bridge.mobile.dto.WaiterReportRequest
import com.yammer.bridge.mobile.print.PrintQueueManager
import com.yammer.bridge.mobile.store.FailedOrderStore
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Raw WebSocket client to the yammer backend — the Android counterpart of the
 * desktop `BridgeWebSocketClient`. Same protocol: newline-free JSON text frames
 * tagged with `type` (`RECEIPT` / `INFO_RECEIPT` in, `RECEIPT_RESULT` out,
 * `HELLO` on connect), authenticated with the `X-Bridge-Key` header.
 *
 * OkHttp's `pingInterval` doubles as the heartbeat: a missed pong fails the
 * connection, which lands in `onFailure` and schedules a reconnect.
 */
class BridgeWebSocketClient(
    private val prefs: Prefs,
    private val queue: PrintQueueManager,
    private val processedStore: ProcessedReceiptStore,
) : WebSocketListener() {

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "bridge-ws-reconnect")
    }

    /** Receipts handed to the printer but not yet finished — closes the de-dup window during printing. */
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Results the backend has not received because the socket was down when the print finished.
     * Flushed right after the next HELLO — the backend's status transitions are idempotent, so a
     * late or duplicate result is harmless, while a lost one leaves the payment to the sweeper.
     */
    private val unsentResults = ConcurrentLinkedQueue<ReceiptResult>()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var running = false

    // ─── lifecycle ───────────────────────────────────────────────────────────

    fun start() {
        if (running) return
        running = true
        connect()
    }

    fun stop() {
        running = false
        webSocket?.close(1000, "bridge stopping")
        webSocket = null
        BridgeState.setWs("oprit")
    }

    private fun connect() {
        if (!running) return
        val url = prefs.serverUrl
        if (prefs.apiKey.isBlank()) {
            BridgeState.log("⚠ Cheia API nu este setata — backend-ul va refuza conexiunea.")
        }
        BridgeState.setWs("se conecteaza...")
        BridgeState.log("Conectare la $url")

        val request = Request.Builder()
            .url(url)
            .header(HEADER_API_KEY, prefs.apiKey)
            .build()
        client.newWebSocket(request, this)
    }

    private fun scheduleReconnect() {
        if (!running) return
        BridgeState.setWs("deconectat — reconectare in ${RECONNECT_DELAY_S}s")
        scheduler.schedule({ connect() }, RECONNECT_DELAY_S, TimeUnit.SECONDS)
    }

    // ─── WebSocketListener ───────────────────────────────────────────────────

    override fun onOpen(webSocket: WebSocket, response: Response) {
        this.webSocket = webSocket
        BridgeState.setWs("conectat")
        BridgeState.log("✓ Conectat la backend (device ${prefs.deviceId.take(8)}…).")
        val hello = JSONObject()
            .put("type", "HELLO")
            .put("deviceId", prefs.deviceId)
            .put("deviceName", prefs.deviceName)
        webSocket.send(hello.toString())
        flushUnsentResults()
    }

    private fun flushUnsentResults() {
        var n = 0
        while (true) {
            val r = unsentResults.poll() ?: break
            sendResult(r)
            n++
        }
        if (n > 0) BridgeState.log("Am retrimis $n rezultat(e) ramase din timpul deconectarii.")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            val node = JSONObject(text)
            when (node.optString("type")) {
                TYPE_RECEIPT -> handleReceipt(node)
                TYPE_INFO -> handleInfo(node)
                TYPE_REPORT -> handleReport(node)
                else -> Log.d(TAG, "Ignoring message of type '${node.optString("type")}'")
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to handle inbound message: ${ex.message}", ex)
            BridgeState.log("✗ Mesaj invalid de la backend: ${ex.message}")
        }
    }

    /**
     * Re-dispatch a stored frame (Comenzi → Reincearca) through the exact pipeline an
     * inbound frame takes — same queueing, idempotency and result reporting. Safe to
     * press repeatedly: an already-printed requestId answers from the processed cache.
     */
    fun retryLocal(raw: String) {
        try {
            val node = JSONObject(raw)
            when (node.optString("type")) {
                TYPE_RECEIPT -> handleReceipt(node)
                TYPE_INFO -> handleInfo(node)
                else -> BridgeState.log("✗ Reincercare imposibila: tip necunoscut.")
            }
        } catch (ex: Exception) {
            BridgeState.log("✗ Reincercare esuata: ${ex.message}")
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.w(TAG, "WebSocket failure: ${t.message}")
        BridgeState.log("✗ Conexiune pierduta: ${t.message}")
        this.webSocket = null
        scheduleReconnect()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.w(TAG, "WebSocket closed: $code $reason")
        BridgeState.log("Conexiune inchisa: $code $reason")
        this.webSocket = null
        scheduleReconnect()
    }

    // ─── inbound messages ────────────────────────────────────────────────────

    private fun handleReceipt(node: JSONObject) {
        val request = ReceiptRequest.fromJson(node)

        // Idempotency: the backend re-sends PENDING receipts on reconnect. If we already
        // printed this requestId, return the cached result instead of printing again.
        processedStore.get(request.requestId)?.let { cached ->
            Log.i(TAG, "Duplicate receipt requestId=${request.requestId} — returning cached result.")
            BridgeState.log("Bon duplicat ${request.requestId} — trimit rezultatul din cache.")
            sendResult(cached)
            return
        }
        // Already printing this requestId — don't print it twice, but ALWAYS answer: a
        // silently dropped frame leaves the backend waiting for a result that never comes.
        if (!inFlight.add(request.requestId)) {
            Log.i(TAG, "Receipt requestId=${request.requestId} already in flight — answering IN_FLIGHT.")
            sendResult(ReceiptResult.error(request.requestId, request.paymentMethod,
                "IN_FLIGHT", "Bonul este deja in curs de tiparire pe acest bridge."))
            return
        }

        val kind = if (request.fiscal) "FISCAL" else "NON-FISCAL"
        BridgeState.log("Bon $kind primit: ${request.requestId} (${request.lines.size} linii, ${request.paymentMethod})")

        queue.submitReceipt(request)
            .thenAccept { result ->
                // OK results are journaled by FiscalPrinterService itself.
                if (ReceiptResult.OK.equals(result.status, ignoreCase = true)) {
                    FailedOrderStore.resolve(request.requestId)
                    BridgeState.log("✓ Bon tiparit: ${request.requestId} nr=${result.receiptNumber ?: "-"}")
                } else {
                    FailedOrderStore.record(
                        FailedOrderStore.FailedOrder(
                            at = Instant.now(),
                            requestId = request.requestId,
                            kind = if (request.fiscal) "FISCAL" else "NON-FISCAL",
                            paymentMethod = request.paymentMethod,
                            total = request.lines.fold(BigDecimal.ZERO) { acc, l ->
                                acc + l.unitPrice.multiply(BigDecimal.valueOf(l.quantity))
                                    .setScale(2, RoundingMode.HALF_UP)
                            },
                            table = null,
                            errorCode = result.errorCode,
                            errorMessage = result.errorMessage,
                            raw = node.toString(),
                        )
                    )
                    BridgeState.log("✗ Bon esuat: ${request.requestId} ${result.errorCode}: ${result.errorMessage}")
                }
                // keep failed ones re-servable so a later resend can retry the print
                inFlight.remove(request.requestId)
                sendResult(result)
            }
            .exceptionally { ex ->
                inFlight.remove(request.requestId)
                Log.e(TAG, "Async print failed requestId=${request.requestId}: ${ex.message}", ex)
                // Always answer — an unreported crash would leave the payment PENDING
                // until the backend deadline instead of FAILED right away.
                sendResult(ReceiptResult.error(request.requestId, request.paymentMethod,
                    "BRIDGE_INTERNAL", "Eroare interna in bridge la tiparire."))
                null
            }
    }

    private fun handleInfo(node: JSONObject) {
        val request = InfoReceiptRequest.fromJson(node)
        BridgeState.log("Proforma primita: ${request.requestId} masa=${request.table} total=${request.total}")
        queue.submitInfo(request)
            .thenAccept { result ->
                if (ReceiptResult.OK.equals(result.status, ignoreCase = true)) {
                    FailedOrderStore.resolve(request.requestId)
                } else {
                    FailedOrderStore.record(
                        FailedOrderStore.FailedOrder(
                            at = Instant.now(),
                            requestId = request.requestId,
                            kind = "PROFORMA",
                            paymentMethod = null,
                            total = request.total,
                            table = request.table,
                            errorCode = result.errorCode,
                            errorMessage = result.errorMessage,
                            raw = node.toString(),
                        )
                    )
                    BridgeState.log("✗ Proforma esuata: ${request.requestId} ${result.errorCode}: ${result.errorMessage}")
                }
                sendResult(result)
            }
            .exceptionally { ex ->
                Log.e(TAG, "Async print failed requestId=${request.requestId}: ${ex.message}", ex)
                null
            }
    }

    private fun handleReport(node: JSONObject) {
        val request = WaiterReportRequest.fromJson(node)
        BridgeState.log("Raport final primit: ${request.requestId} (${request.rows.size} ospatari)")
        queue.submitReport(request)
            .thenAccept { result ->
                if (ReceiptResult.OK.equals(result.status, ignoreCase = true)) {
                    BridgeState.log("✓ Raport final tiparit: ${request.rows.size} bon(uri).")
                } else {
                    BridgeState.log("✗ Raport final esuat: ${result.errorCode}: ${result.errorMessage}")
                }
                sendResult(result)
            }
            .exceptionally { ex ->
                Log.e(TAG, "Async report print failed requestId=${request.requestId}: ${ex.message}", ex)
                null
            }
    }

    // ─── outbound result ─────────────────────────────────────────────────────

    private fun sendResult(result: ReceiptResult) {
        val ws = webSocket
        if (ws == null) {
            park(result, "fara conexiune")
            return
        }
        try {
            val out = result.toJson().put("type", TYPE_RESULT)
            if (ws.send(out.toString())) {
                Log.i(TAG, "Sent RECEIPT_RESULT requestId=${result.requestId} status=${result.status}")
            } else {
                park(result, "socket inchis")
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to send result requestId=${result.requestId}: ${ex.message}", ex)
            park(result, ex.message ?: "eroare")
        }
    }

    /** Keep a result for the next connection instead of losing it. */
    private fun park(result: ReceiptResult, why: String) {
        if (unsentResults.size >= MAX_UNSENT) unsentResults.poll()
        unsentResults.add(result)
        Log.w(TAG, "Result for ${result.requestId} parked ($why) — will be resent after reconnect.")
        BridgeState.log("⚠ Rezultatul pentru ${result.requestId} se trimite dupa reconectare ($why).")
    }

    companion object {
        private const val TAG = "BridgeWsClient"
        private const val HEADER_API_KEY = "X-Bridge-Key"
        private const val TYPE_RECEIPT = "RECEIPT"
        private const val TYPE_INFO = "INFO_RECEIPT"
        private const val TYPE_REPORT = "WAITER_REPORT"
        private const val TYPE_RESULT = "RECEIPT_RESULT"
        private const val RECONNECT_DELAY_S = 3L
        private const val MAX_UNSENT = 200
    }
}
