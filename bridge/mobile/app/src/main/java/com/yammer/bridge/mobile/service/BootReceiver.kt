package com.yammer.bridge.mobile.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the bridge automatically after a device reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, BridgeForegroundService::class.java))
        }
    }
}
