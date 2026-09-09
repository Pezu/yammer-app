package com.yammer.bridge.mobile

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.UUID

/** Bridge settings, persisted in SharedPreferences (mirrors the desktop application.yml). */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("bridge", Context.MODE_PRIVATE)

    /**
     * Stable device id announced in the HELLO frame — the backend routes receipts for
     * USB-bound registers to this id. Generated once per install, never changes.
     */
    val deviceId: String
        get() = sp.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
            sp.edit().putString("deviceId", it).apply()
        }

    /**
     * Human-readable device name announced in HELLO and shown in the backoffice device
     * picker (Peripherals → USB device): the operator's nickname, else "manufacturer model".
     */
    val deviceName: String
        get() = nickname.ifBlank { "${Build.MANUFACTURER} ${Build.MODEL}".trim() }

    /** Operator-chosen name for this bridge, e.g. "Bar terasa" (blank = the phone's model). */
    var nickname: String
        get() = sp.getString("nickname", "")!!
        set(v) = sp.edit().putString("nickname", v.trim()).apply()

    var serverUrl: String
        get() = sp.getString("serverUrl", DEFAULT_SERVER_URL)!!
        set(v) = sp.edit().putString("serverUrl", v.trim()).apply()

    /** A blank saved key means "use the build's default" (a save with the field empty must not lock the app out). */
    var apiKey: String
        get() = sp.getString("apiKey", "")!!.ifBlank { DEFAULT_API_KEY }
        set(v) = sp.edit().putString("apiKey", v.trim()).apply()

    /** Serial baud rate for the USB link to the Datecs register. */
    var baudRate: Int
        get() = sp.getInt("baudRate", 115_200)
        set(v) = sp.edit().putInt("baudRate", v).apply()

    var operatorCode: String
        get() = sp.getString("operatorCode", "1")!!
        set(v) = sp.edit().putString("operatorCode", v.trim()).apply()

    var operatorPass: String
        get() = sp.getString("operatorPass", "0001")!!
        set(v) = sp.edit().putString("operatorPass", v.trim()).apply()

    var tillNumber: String
        get() = sp.getString("tillNumber", "1")!!
        set(v) = sp.edit().putString("tillNumber", v.trim()).apply()

    /**
     * DATECS tax code of the EXEMPT group ("scutit de TVA"). The RO firmware numbers the
     * codes 1..7: 1..5 = the programmable rates A..E, 6 = exempt, 7 = "alte taxe".
     */
    var exemptTaxGroup: Int
        get() = sp.getInt("exemptTaxGroup", 6)
        set(v) = sp.edit().putInt("exemptTaxGroup", v).apply()

    var serialNumber: String
        get() = sp.getString("serialNumber", "")!!
        set(v) = sp.edit().putString("serialNumber", v.trim()).apply()

    /** TCP port of the register, used only for the LAN fallback when no USB device is attached. */
    var tcpPort: Int
        get() = sp.getInt("tcpPort", 3999)
        set(v) = sp.edit().putInt("tcpPort", v).apply()

    companion object {
        // The api's custom domain (Cloud Run domain mapping — WebSocket upgrades verified to pass).
        const val DEFAULT_SERVER_URL = "wss://api.yammer.ro/ws/bridge"

        // Baked in at build time from local.properties (bridge.apiKey) — blank in a plain build,
        // in which case the key is typed in once on the device.
        val DEFAULT_API_KEY: String = BuildConfig.DEFAULT_API_KEY
    }
}
