package com.yammer.bridge.mobile.usb

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream

/**
 * The ESC/POS thermal printer on the phone's USB (through the OTG hub, next to the
 * fiscal register). Printers enumerate with a USB "printer class" (7) interface that
 * has a bulk OUT endpoint; that is all ESC/POS needs — raw bytes, no driver.
 *
 * The fiscal register is a CDC-ACM serial device (class 2/10), so the two never
 * match each other's finder.
 */
class UsbThermalPrinterManager(private val context: Context) {

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    /** The first attached USB device exposing a printer-class interface, or null. */
    fun find(): UsbDevice? = usbManager.deviceList.values.firstOrNull { printerInterface(it) != null }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    @SuppressLint("MutableImplicitPendingIntent")
    fun requestPermission(device: UsbDevice) {
        val intent = PendingIntent.getBroadcast(
            context, 1,
            Intent(UsbRegisterManager.ACTION_USB_PERMISSION).setPackage(context.packageName),
            PendingIntent.FLAG_MUTABLE
        )
        usbManager.requestPermission(device, intent)
    }

    fun describe(device: UsbDevice): String =
        (device.productName ?: device.deviceName) +
            " (%04X:%04X)".format(device.vendorId, device.productId)

    /** Open the printer for writing; asks for the permission first when it is missing. */
    fun open(): Connection {
        val device = find() ?: throw IOException("Nicio imprimanta termica pe USB.")
        if (!hasPermission(device)) {
            requestPermission(device)
            throw IOException("Fara permisiune USB pentru imprimanta — acceptati dialogul si reincercati.")
        }
        val iface = printerInterface(device) ?: throw IOException("Imprimanta fara interfata de tip printer.")
        val endpoint = bulkOut(iface) ?: throw IOException("Imprimanta fara endpoint de scriere.")
        val conn = usbManager.openDevice(device) ?: throw IOException("Nu pot deschide imprimanta USB.")
        if (!conn.claimInterface(iface, true)) {
            conn.close()
            throw IOException("Interfata imprimantei este ocupata.")
        }
        return Connection(conn, iface, endpoint)
    }

    private fun printerInterface(device: UsbDevice): UsbInterface? =
        (0 until device.interfaceCount).map { device.getInterface(it) }
            .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_PRINTER && bulkOut(it) != null }

    private fun bulkOut(iface: UsbInterface): UsbEndpoint? =
        (0 until iface.endpointCount).map { iface.getEndpoint(it) }
            .firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }

    /** Bulk-OUT writer; chunked so any packet size works. */
    class Connection(
        private val conn: UsbDeviceConnection,
        private val iface: UsbInterface,
        private val endpoint: UsbEndpoint,
    ) : Closeable {

        val output: OutputStream = object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

            override fun write(b: ByteArray, off: Int, len: Int) {
                var pos = off
                val end = off + len
                while (pos < end) {
                    val n = minOf(CHUNK, end - pos)
                    val sent = conn.bulkTransfer(endpoint, b.copyOfRange(pos, pos + n), n, WRITE_TIMEOUT_MS)
                    if (sent < 0) throw IOException("Scriere USB esuata catre imprimanta.")
                    pos += sent
                }
            }
        }

        override fun close() {
            try {
                conn.releaseInterface(iface)
            } finally {
                conn.close()
            }
        }
    }

    companion object {
        private const val CHUNK = 4096
        private const val WRITE_TIMEOUT_MS = 5_000
    }
}
