package com.yammer.bridge.mobile.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yammer.bridge.mobile.BridgeApp
import com.yammer.bridge.mobile.BridgeState
import com.yammer.bridge.mobile.MainActivity
import com.yammer.bridge.mobile.Prefs
import com.yammer.bridge.mobile.print.EscPosThermalService
import com.yammer.bridge.mobile.print.FiscalPrinterService
import com.yammer.bridge.mobile.print.PrintQueueManager
import com.yammer.bridge.mobile.usb.UsbRegisterManager
import com.yammer.bridge.mobile.usb.UsbThermalPrinterManager
import com.yammer.bridge.mobile.ws.BridgeWebSocketClient
import com.yammer.bridge.mobile.ws.ProcessedReceiptStore

/**
 * Foreground service that keeps the bridge alive: hosts the WebSocket client,
 * the print queues and the USB register connection while the app is backgrounded.
 */
class BridgeForegroundService : Service() {

    private var ws: BridgeWebSocketClient? = null
    private var queue: PrintQueueManager? = null
    private val stateListener: () -> Unit = { updateNotification() }

    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs(this)
        val usb = UsbRegisterManager(this)
        val store = ProcessedReceiptStore(this)
        val q = PrintQueueManager(FiscalPrinterService(prefs, usb, store), EscPosThermalService(UsbThermalPrinterManager(this)))
        queue = q
        ws = BridgeWebSocketClient(prefs, q, store)
        com.yammer.bridge.mobile.BridgeRuntime.client = ws
        BridgeState.addListener(stateListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        ws?.start()
        return START_STICKY
    }

    override fun onDestroy() {
        BridgeState.removeListener(stateListener)
        com.yammer.bridge.mobile.BridgeRuntime.client = null
        ws?.stop()
        queue?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, BridgeApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Yammer Bridge")
            .setContentText("Server: ${BridgeState.wsStatus} · USB: ${BridgeState.usbStatus}")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}
