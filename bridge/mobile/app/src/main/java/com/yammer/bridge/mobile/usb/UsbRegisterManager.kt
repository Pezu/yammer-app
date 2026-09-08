package com.yammer.bridge.mobile.usb

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.yammer.bridge.mobile.BridgeState
import com.yammer.bridge.mobile.fiscal.UsbNotAvailableException
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Finds, authorizes and opens the Datecs cash register attached over USB.
 *
 * The DP-25MX enumerates as a USB-serial device; detection first uses the
 * library's default prober (FTDI/CP210x/CH34x/PL2303/CDC) and falls back to
 * forcing a CDC-ACM driver for any device exposing a CDC interface.
 */
class UsbRegisterManager(private val context: Context) {

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    /** The best-match serial driver for an attached register, or null when nothing is plugged in. */
    fun findDriver(): UsbSerialDriver? {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isNotEmpty()) {
            return drivers.first()
        }
        // Fallback: unknown VID/PID but the device exposes a CDC-ACM (serial) interface.
        // Subclass must be ACM (0x02): USB Ethernet adapters (e.g. Realtek RTL8153) also
        // expose USB_CLASS_COMM but as ECM/NCM — grabbing those gives a device that can
        // never speak the fiscal protocol.
        for (device in usbManager.deviceList.values) {
            if (hasCdcAcmInterface(device)) {
                return CdcAcmSerialDriver(device)
            }
        }
        return null
    }

    private fun hasCdcAcmInterface(device: UsbDevice): Boolean =
        (0 until device.interfaceCount).any {
            val i = device.getInterface(it)
            i.interfaceClass == UsbConstants.USB_CLASS_COMM && i.interfaceSubclass == CDC_SUBCLASS_ACM
        }

    /** Every attached USB device, for the diagnostic log: name, VID/PID and interface classes. */
    fun listAttached(): List<String> = usbManager.deviceList.values.map { d ->
        val ifaces = (0 until d.interfaceCount).joinToString("+") {
            val i = d.getInterface(it)
            "%02X.%02X".format(i.interfaceClass, i.interfaceSubclass)
        }
        "%s (VID %04X PID %04X, interfete %s)".format(
            d.productName ?: "dispozitiv necunoscut", d.vendorId, d.productId, ifaces)
    }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    /** Fires the system USB-permission dialog; the result lands in [MainActivity]'s receiver. */
    @SuppressLint("MutableImplicitPendingIntent")
    fun requestPermission(device: UsbDevice) {
        val intent = PendingIntent.getBroadcast(
            context, 0,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            PendingIntent.FLAG_MUTABLE
        )
        usbManager.requestPermission(device, intent)
    }

    /** Human label for the status UI: "FTDI FT232R (VID 0403)" etc. */
    fun describe(driver: UsbSerialDriver): String {
        val d = driver.device
        val name = d.productName ?: driver.javaClass.simpleName.removeSuffix("SerialDriver")
        return "$name (VID %04X PID %04X)".format(d.vendorId, d.productId)
    }

    /**
     * Opens the register's serial port and exposes it as blocking streams for
     * [com.yammer.bridge.mobile.fiscal.DatecsProtocol]. Caller must [Connection.close].
     */
    fun open(baudRate: Int): Connection {
        val driver = findDriver()
            ?: throw UsbNotAvailableException("Nicio casa de marcat conectata pe USB.")
        if (!hasPermission(driver.device)) {
            requestPermission(driver.device)
            throw UsbNotAvailableException(
                "Fara permisiune USB pentru ${describe(driver)} — acordati permisiunea si reincercati."
            )
        }
        val connection = usbManager.openDevice(driver.device)
            ?: throw UsbNotAvailableException("Nu se poate deschide dispozitivul USB ${describe(driver)}.")

        val port = driver.ports.first()
        port.open(connection)
        port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        // Some CDC bridges hold TX until the host asserts the modem lines.
        runCatching { port.setDTR(true) }
        runCatching { port.setRTS(true) }

        BridgeState.setUsb("conectat: ${describe(driver)}")
        Log.i(TAG, "USB port deschis: ${describe(driver)} @ $baudRate")
        return Connection(port)
    }

    /** A serial port wrapped as blocking streams with the protocol's 15s read budget. */
    class Connection(private val port: UsbSerialPort) : Closeable {

        val input: InputStream = object : InputStream() {
            private val buf = ByteArray(4096)
            private var pos = 0
            private var count = 0

            override fun read(): Int {
                if (pos >= count && !fill()) return -1
                return buf[pos++].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= count && !fill()) return -1
                val n = minOf(len, count - pos)
                System.arraycopy(buf, pos, b, off, n)
                pos += n
                return n
            }

            /** Blocks until at least one byte arrives or the overall timeout elapses. */
            private fun fill(): Boolean {
                val deadline = System.currentTimeMillis() + READ_TIMEOUT_MS
                while (System.currentTimeMillis() < deadline) {
                    val n = port.read(buf, POLL_TIMEOUT_MS)
                    if (n > 0) {
                        pos = 0
                        count = n
                        return true
                    }
                }
                throw IOException("Timeout la citirea de pe portul USB (${READ_TIMEOUT_MS / 1000}s)")
            }
        }

        val output: OutputStream = object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

            override fun write(b: ByteArray, off: Int, len: Int) {
                val chunk = if (off == 0 && len == b.size) b else b.copyOfRange(off, off + len)
                port.write(chunk, WRITE_TIMEOUT_MS)
            }
        }

        override fun close() {
            runCatching { port.close() }
        }
    }

    companion object {
        private const val TAG = "UsbRegisterManager"
        const val ACTION_USB_PERMISSION = "com.yammer.bridge.mobile.USB_PERMISSION"

        /** CDC subclass Abstract Control Model — the serial flavour of USB_CLASS_COMM. */
        private const val CDC_SUBCLASS_ACM = 2

        // Mirrors the desktop protocol's 15s SoTimeout.
        private const val READ_TIMEOUT_MS = 15_000L
        private const val POLL_TIMEOUT_MS = 200
        private const val WRITE_TIMEOUT_MS = 5_000
    }
}
