package com.yammer.bridge.mobile

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.yammer.bridge.mobile.fiscal.DatecsProtocol
import com.yammer.bridge.mobile.service.BridgeForegroundService
import com.yammer.bridge.mobile.store.FailedOrderStore
import com.yammer.bridge.mobile.usb.UsbRegisterManager
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

/**
 * Single-activity UI with a navigation drawer:
 *  - "Status"  — connection status, settings, USB actions and the live journal
 *  - "Comenzi" — the failed (unprinted) orders from [FailedOrderStore]
 *
 * The actual bridge work happens in [BridgeForegroundService].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var usb: UsbRegisterManager

    private lateinit var drawer: DrawerLayout
    private lateinit var statusContainer: View
    private lateinit var ordersContainer: View
    private lateinit var wsStatus: TextView
    private lateinit var usbStatus: TextView
    private lateinit var logView: TextView
    private lateinit var ordersEmpty: TextView
    private lateinit var ordersList: ListView

    private val timeFmt = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault())

    private val stateListener: () -> Unit = { runOnUiThread { render() } }

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == UsbRegisterManager.ACTION_USB_PERMISSION) {
                BridgeState.log("Permisiune USB: ${if (granted(intent)) "acordata" else "refuzata"}")
                refreshUsbStatus()
            }
        }

        private fun granted(intent: Intent): Boolean =
            intent.getBooleanExtra("permission", false) ||
                intent.getBooleanExtra(android.hardware.usb.UsbManager.EXTRA_PERMISSION_GRANTED, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        usb = UsbRegisterManager(this)

        drawer = findViewById(R.id.drawerLayout)
        statusContainer = findViewById(R.id.statusContainer)
        ordersContainer = findViewById(R.id.ordersContainer)
        wsStatus = findViewById(R.id.wsStatus)
        usbStatus = findViewById(R.id.usbStatus)
        logView = findViewById(R.id.logView)
        ordersEmpty = findViewById(R.id.ordersEmpty)
        ordersList = findViewById(R.id.ordersList)

        setupDrawer()
        setupStatusScreen()

        // Device id: shown for pairing in backoffice (Integrations → USB device); tap to copy.
        val deviceIdView = findViewById<TextView>(R.id.deviceId)
        deviceIdView.text = "Device ID: ${prefs.deviceId} (atinge pt. copiere)"
        deviceIdView.setOnClickListener {
            val clipboard = getSystemService(android.content.ClipboardManager::class.java)
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("deviceId", prefs.deviceId)
            )
            BridgeState.log("Device ID copiat in clipboard.")
        }
        findViewById<Button>(R.id.ordersClear).setOnClickListener {
            FailedOrderStore.clear()
            render()
        }

        ContextCompat.registerReceiver(
            this, usbPermissionReceiver,
            IntentFilter(UsbRegisterManager.ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        requestNotificationPermissionIfNeeded()
        startForegroundService(Intent(this, BridgeForegroundService::class.java))
        refreshUsbStatus()
    }

    private fun setupDrawer() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
            this, drawer, toolbar, R.string.drawer_open, R.string.drawer_close
        )
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        val navView = findViewById<NavigationView>(R.id.navView)
        navView.setCheckedItem(R.id.nav_status)
        navView.setNavigationItemSelectedListener { item ->
            showScreen(item.itemId)
            drawer.closeDrawers()
            true
        }
    }

    private fun showScreen(itemId: Int) {
        val showOrders = itemId == R.id.nav_orders
        statusContainer.visibility = if (showOrders) View.GONE else View.VISIBLE
        ordersContainer.visibility = if (showOrders) View.VISIBLE else View.GONE
        supportActionBar?.title = if (showOrders) "Comenzi nereusite" else "Yammer Bridge"
        render()
    }

    private fun setupStatusScreen() {
        val nickname = findViewById<EditText>(R.id.nickname)
        val serverUrl = findViewById<EditText>(R.id.serverUrl)
        val apiKey = findViewById<EditText>(R.id.apiKey)
        val baudRate = findViewById<EditText>(R.id.baudRate)
        val tillNumber = findViewById<EditText>(R.id.tillNumber)
        val exemptGroup = findViewById<EditText>(R.id.exemptGroup)

        nickname.setText(prefs.nickname)
        serverUrl.setText(prefs.serverUrl)
        apiKey.setText(prefs.apiKey)
        baudRate.setText(prefs.baudRate.toString())
        tillNumber.setText(prefs.tillNumber)
        exemptGroup.setText(prefs.exemptTaxGroup.toString())

        findViewById<Button>(R.id.saveRestart).setOnClickListener {
            prefs.nickname = nickname.text.toString()
            prefs.serverUrl = serverUrl.text.toString()
            prefs.apiKey = apiKey.text.toString()
            prefs.baudRate = baudRate.text.toString().toIntOrNull() ?: 115_200
            prefs.tillNumber = tillNumber.text.toString().ifBlank { "1" }
            prefs.exemptTaxGroup = exemptGroup.text.toString().toIntOrNull()?.coerceIn(1, 8) ?: 6
            BridgeState.log("Setari salvate — repornesc serviciul.")
            stopService(Intent(this, BridgeForegroundService::class.java))
            startForegroundService(Intent(this, BridgeForegroundService::class.java))
        }

        findViewById<Button>(R.id.usbConnect).setOnClickListener { connectUsb() }
        findViewById<Button>(R.id.usbTest).setOnClickListener { testRegister() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Launched by USB_DEVICE_ATTACHED — the system already granted permission.
        refreshUsbStatus()
    }

    override fun onStart() {
        super.onStart()
        BridgeState.addListener(stateListener)
        render()
    }

    override fun onStop() {
        BridgeState.removeListener(stateListener)
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(usbPermissionReceiver)
        super.onDestroy()
    }

    private fun connectUsb() {
        // Diagnostic dump first: exactly what the phone enumerates, register or not.
        val attached = usb.listAttached()
        if (attached.isEmpty()) {
            BridgeState.log("Niciun dispozitiv USB atasat.")
        } else {
            attached.forEach { BridgeState.log("USB atasat: $it") }
        }
        val driver = usb.findDriver()
        if (driver == null) {
            BridgeState.log("Niciun dispozitiv USB serial gasit. Verificati cablul OTG.")
            refreshUsbStatus()
            return
        }
        if (!usb.hasPermission(driver.device)) {
            BridgeState.log("Cer permisiune USB pentru ${usb.describe(driver)}...")
            usb.requestPermission(driver.device)
        } else {
            BridgeState.log("USB pregatit: ${usb.describe(driver)}")
        }
        refreshUsbStatus()
    }

    /** Safe diagnostic: opens the port and sends cmd 60 (cancel) — prints nothing. */
    private fun testRegister() {
        thread(name = "usb-test") {
            try {
                usb.open(prefs.baudRate).use { conn ->
                    val fp = DatecsProtocol(conn.input, conn.output)
                    fp.cancelFiscalCheck()
                    BridgeState.log("✓ Test casa OK — dispozitivul raspunde pe USB.")
                    // Read-only: shows which code carries which rate, so the mapping can be checked.
                    try {
                        BridgeState.log("Cote TVA casa (cmd 50): ${fp.readTaxRates().replace("\t", " | ")}")
                    } catch (ex: Exception) {
                        BridgeState.log("Cote TVA casa: necitite (${ex.message})")
                    }
                }
            } catch (ex: Exception) {
                BridgeState.log("✗ Test casa esuat: ${ex.message}")
            }
        }
    }

    private fun refreshUsbStatus() {
        val driver = usb.findDriver()
        when {
            driver == null -> BridgeState.setUsb("neconectat")
            !usb.hasPermission(driver.device) -> BridgeState.setUsb("fara permisiune: ${usb.describe(driver)}")
            else -> BridgeState.setUsb("pregatit: ${usb.describe(driver)}")
        }
    }

    private fun render() {
        wsStatus.text = "Server: ${BridgeState.wsStatus}"
        usbStatus.text = "USB: ${BridgeState.usbStatus}"
        logView.text = BridgeState.logText()
        renderOrders()
    }

    /** Orders whose item list is expanded (keyed by requestId). */
    private val expandedOrders = mutableSetOf<String>()

    private fun renderOrders() {
        if (ordersContainer.visibility != View.VISIBLE) return
        val orders = FailedOrderStore.list()
        ordersEmpty.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
        ordersList.adapter = object : ArrayAdapter<FailedOrderStore.FailedOrder>(
            this, R.layout.row_failed_order, orders
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = convertView
                    ?: layoutInflater.inflate(R.layout.row_failed_order, parent, false)
                val o = orders[position]

                val pay = o.paymentMethod ?: o.kind
                val total = o.total?.let { "%.2f RON".format(it) } ?: ""
                view.findViewById<TextView>(R.id.orderTime).text = timeFmt.format(o.at)
                view.findViewById<TextView>(R.id.orderHeader).text =
                    listOf(pay, total).filter { it.isNotBlank() }.joinToString(" · ")
                view.findViewById<TextView>(R.id.orderError).text =
                    "${o.errorCode ?: "EROARE"}: ${o.errorMessage ?: "-"}"

                val itemsView = view.findViewById<TextView>(R.id.orderItems)
                if (expandedOrders.contains(o.requestId)) {
                    itemsView.text = orderItems(o)
                    itemsView.visibility = View.VISIBLE
                } else {
                    itemsView.visibility = View.GONE
                }

                view.setOnClickListener {
                    if (!expandedOrders.remove(o.requestId)) {
                        expandedOrders.add(o.requestId)
                    }
                    renderOrders()
                }

                // Always clickable (a disabled button would let the tap fall through to the
                // row and expand/collapse it); entries without the stored frame just explain.
                val retry = view.findViewById<Button>(R.id.orderRetry)
                retry.alpha = if (o.raw != null) 1f else 0.4f
                retry.setOnClickListener {
                    val client = BridgeRuntime.client
                    when {
                        o.raw == null -> BridgeState.log(
                            "✗ Bonul ${o.requestId.take(8)}… nu are datele originale salvate — reincearca din backoffice.")
                        client == null -> BridgeState.log("✗ Serviciul bridge nu ruleaza — nu pot reincerca.")
                        else -> {
                            BridgeState.log("Reincerc bonul ${o.requestId.take(8)}…")
                            client.retryLocal(o.raw)
                        }
                    }
                }
                return view
            }
        }
    }

    /** The order's items, one per line, parsed from the stored original frame. */
    private fun orderItems(o: FailedOrderStore.FailedOrder): String {
        val raw = o.raw ?: return "(detalii indisponibile)"
        return try {
            val node = org.json.JSONObject(raw)
            val lines = node.optJSONArray("lines") ?: return "(fara produse)"
            (0 until lines.length()).joinToString("\n") { i ->
                val l = lines.getJSONObject(i)
                val qty = l.opt("quantity")?.toString()?.removeSuffix(".0") ?: "?"
                val name = l.optString("name", "?")
                val price = l.opt("unitPrice") ?: l.opt("lineTotal")
                val vat = if (l.has("vat") && !l.isNull("vat")) " (TVA ${l.get("vat")}%)" else ""
                "$qty x $name" + (price?.let { " — $it RON" } ?: "") + vat
            }.ifEmpty { "(fara produse)" }
        } catch (ex: Exception) {
            "(detalii indisponibile: ${ex.message})"
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
