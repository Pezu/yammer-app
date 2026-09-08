package com.yammer.bridge.mobile

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-wide observable state shared between the foreground service (writer)
 * and the UI (reader): connection statuses plus a bounded in-memory log.
 */
object BridgeState {

    @Volatile var wsStatus: String = "deconectat"
    @Volatile var usbStatus: String = "neconectat"

    private const val MAX_LINES = 400
    private val lines = ArrayDeque<String>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    fun log(message: String) {
        synchronized(lines) {
            lines.addLast("${timeFmt.format(Date())}  $message")
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
        notifyListeners()
    }

    fun logText(): String = synchronized(lines) { lines.joinToString("\n") }

    fun setWs(status: String) {
        wsStatus = status
        notifyListeners()
    }

    fun setUsb(status: String) {
        usbStatus = status
        notifyListeners()
    }

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)

    private fun notifyListeners() = listeners.forEach { it() }
}
