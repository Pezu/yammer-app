package com.yammer.bridge.mobile

import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-wide observable state shared between the foreground service (writer)
 * and the UI (reader): connection statuses plus a bounded journal. The journal is
 * mirrored to `journal.log` in the app's files dir so it survives restarts and
 * crashes — what happened before a crash is exactly what you want to read after it.
 */
object BridgeState {

    @Volatile var wsStatus: String = "deconectat"
    @Volatile var usbStatus: String = "neconectat"
    @Volatile var printerStatus: String = "neconectata"

    /** True only while the WebSocket session is open and HELLO has been sent. */
    val wsConnected: Boolean get() = wsStatus == "conectat"
    /** True when the register is enumerated on USB and the app holds the permission. */
    val usbReady: Boolean get() = usbStatus.startsWith("pregatit")
    /** True when a printer-class USB device is attached and permitted. */
    val printerReady: Boolean get() = printerStatus.startsWith("pregatita")

    private const val MAX_LINES = 400
    private val lines = ArrayDeque<String>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    @Volatile private var journalFile: File? = null

    /** Load the tail of the persisted journal; call once from the Application. */
    fun init(dir: File) {
        val file = File(dir, "journal.log")
        journalFile = file
        try {
            if (file.exists()) {
                val tail = file.readLines().takeLast(MAX_LINES)
                synchronized(lines) {
                    lines.clear()
                    // stored lines carry the date; the screen shows only the time
                    tail.forEach { lines.addLast(it.removePrefix(it.take(11))) }
                }
            }
        } catch (_: Exception) {
            // unreadable journal: start empty
        }
    }

    fun log(message: String) {
        val now = Date()
        synchronized(lines) {
            lines.addLast("${timeFmt.format(now)}  $message")
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
        try {
            journalFile?.appendText("${dayFmt.format(now)}  $message\n")
        } catch (_: Exception) {
            // best-effort persistence; the in-memory journal still shows the line
        }
        notifyListeners()
    }

    /** "Goleste jurnalul": drop everything, on screen and on disk. */
    fun clear() {
        synchronized(lines) { lines.clear() }
        try {
            journalFile?.writeText("")
        } catch (_: Exception) {
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

    fun setPrinter(status: String) {
        printerStatus = status
        notifyListeners()
    }

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)

    private fun notifyListeners() = listeners.forEach { it() }
}
