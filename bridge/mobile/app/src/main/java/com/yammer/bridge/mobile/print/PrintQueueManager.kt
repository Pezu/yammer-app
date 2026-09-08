package com.yammer.bridge.mobile.print

import com.yammer.bridge.mobile.dto.InfoReceiptRequest
import com.yammer.bridge.mobile.dto.ReceiptRequest
import com.yammer.bridge.mobile.dto.ReceiptResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Serializes print jobs per device: one single-threaded queue per device key,
 * created lazily. Fiscal devices don't tolerate concurrent connections, so jobs
 * for the same device run one at a time; different devices are independent.
 *
 * All fiscal jobs share the USB register's queue (there is a single register
 * attached to the phone); thermal printers queue by their LAN IP.
 */
class PrintQueueManager(
    private val fiscalPrinterService: FiscalPrinterService,
    private val thermalService: EscPosThermalService,
) {

    private val executors = ConcurrentHashMap<String, ExecutorService>()

    fun submitReceipt(request: ReceiptRequest): CompletableFuture<ReceiptResult> {
        val device = if (request.fiscal) FiscalPrinterService.DEVICE_USB else request.printerIp
        return CompletableFuture.supplyAsync(
            {
                if (request.fiscal) fiscalPrinterService.print(request)
                else thermalService.printReceipt(request)
            },
            executorFor(device)
        )
    }

    fun submitInfo(request: InfoReceiptRequest): CompletableFuture<ReceiptResult> =
        CompletableFuture.supplyAsync({ thermalService.print(request) }, executorFor(request.printerIp))

    private fun executorFor(deviceKey: String?): ExecutorService {
        val key = deviceKey?.trim().takeUnless { it.isNullOrEmpty() } ?: NO_DEVICE_KEY
        return executors.computeIfAbsent(key) { k ->
            Executors.newSingleThreadExecutor { r -> Thread(r, "print-queue-$k") }
        }
    }

    fun shutdown() {
        executors.values.forEach { it.shutdown() }
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30)
        for (executor in executors.values) {
            try {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0 || !executor.awaitTermination(remaining, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow()
                }
            } catch (e: InterruptedException) {
                executor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }

    companion object {
        private const val NO_DEVICE_KEY = "<no-device>"
    }
}
