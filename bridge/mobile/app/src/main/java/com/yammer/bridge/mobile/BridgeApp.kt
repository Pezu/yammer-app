package com.yammer.bridge.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.yammer.bridge.mobile.store.FailedOrderStore

class BridgeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FailedOrderStore.init(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    companion object {
        const val CHANNEL_ID = "bridge"
    }
}
