package com.yammer.bridge.mobile

import com.yammer.bridge.mobile.ws.BridgeWebSocketClient

/**
 * Handle to the live WebSocket client hosted by the foreground service, so the UI
 * (e.g. the Comenzi screen's retry button) can re-dispatch a stored frame through
 * the same print pipeline. Null while the service is not running.
 */
object BridgeRuntime {
    @Volatile
    var client: BridgeWebSocketClient? = null
}
