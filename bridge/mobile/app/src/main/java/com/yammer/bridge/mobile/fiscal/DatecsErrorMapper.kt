package com.yammer.bridge.mobile.fiscal

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps raw DATECS errors and transport exceptions to readable error codes and
 * messages for `ReceiptResult`. Port of the desktop `DatecsErrorMapper`, plus a
 * USB-specific unreachable message (`device` is the USB label or the LAN IP).
 */
object DatecsErrorMapper {

    class MappedError(val code: String, val message: String)

    private val DATECS_CODE = Regex("\\[(-?\\d+)]")

    fun map(ex: Exception, device: String): MappedError {
        val rawMsg = ex.message ?: ""

        when (ex) {
            is ConnectException, is NoRouteToHostException -> return MappedError(
                "DATECS_UNREACHABLE",
                "Nu se poate conecta la casa de marcat [$device]. " +
                    "Verificati ca imprimanta este pornita si conectata (USB/retea)."
            )
            is DatecsProtocol.BusyTimeoutException -> return MappedError(
                "DATECS_BUSY_TIMEOUT",
                "Casa de marcat [$device] a ramas ocupata peste bugetul de timp " +
                    "(hartie lipsa sau blocata). Bonul NU a fost emis — remediati si reincercati.",
            )
            is SocketTimeoutException -> return MappedError(
                "DATECS_TIMEOUT",
                "Timeout la comunicarea cu casa de marcat [$device]. " +
                    "Imprimanta nu a raspuns in timp util."
            )
            is UnknownHostException -> return MappedError(
                "DATECS_UNKNOWN_HOST",
                "Adresa IP/hostname invalida pentru casa de marcat: $device"
            )
            is UsbNotAvailableException -> return MappedError(
                "DATECS_USB_UNAVAILABLE", rawMsg
            )
            else -> {}
        }

        val match = DATECS_CODE.find(rawMsg)
        if (match != null) {
            return mapDatecsCode(match.groupValues[1].toInt(), rawMsg)
        }
        return MappedError("COMM_ERROR", rawMsg)
    }

    private fun mapDatecsCode(code: Int, rawMsg: String): MappedError = when (code) {
        -112001 -> MappedError(
            "DATECS_INVALID_PARAMETER",
            "Imprimanta a respins un parametru la deschiderea bonului (cod: -112001). " +
                "Verificati formatul UNS sau ordinea campurilor din cmd 48."
        )
        -111024 -> MappedError(
            "DATECS_Z_REPORT_REQUIRED",
            "Casa de marcat necesita raport Z pentru ziua/zilele fiscale precedente " +
                "inainte de a putea emite un bon nou. Printati raportul Z de pe " +
                "tastatura casei de marcat si reincercati."
        )
        -111016 -> MappedError(
            "DATECS_NO_OPEN_RECEIPT",
            "Nu exista bon fiscal deschis pe casa de marcat. " +
                "Operatiunea de anulare/inchidere bon nu poate continua."
        )
        -111025 -> MappedError(
            "DATECS_RECEIPT_ALREADY_OPEN",
            "Exista deja un bon fiscal deschis pe casa de marcat. " +
                "Inchideti sau anulati bonul curent si reincercati."
        )
        -111008 -> MappedError(
            "DATECS_OPERATOR_ERROR",
            "Cod operator sau parola incorecta. Verificati setarile operatorului din aplicatie."
        )
        -111040 -> MappedError(
            "DATECS_INVALID_AMOUNT",
            "Suma sau cantitatea unui articol este invalida (zero sau negativa). " +
                "Verificati datele din cererea de bon."
        )
        -111033 -> MappedError(
            "DATECS_INVALID_TAX_GROUP",
            "Grup TVA invalid transmis casei de marcat. Valori acceptate: 1(A/21%), 2(B/11%), 3(C/0%)."
        )
        -111060, -111061 -> MappedError(
            "DATECS_FISCAL_MEMORY_FULL",
            "Memoria fiscala a casei de marcat este plina. Contactati service-ul autorizat DATECS."
        )
        -111070 -> MappedError(
            "DATECS_PRINTER_ERROR",
            "Eroare imprimanta: lipsa hartie sau capac deschis. " +
                "Verificati hartia termica si inchideti capacul imprimantei."
        )
        else ->
            if (code <= -111000 && code > -112000) {
                MappedError(
                    "DATECS_FISCAL_STATE_ERROR",
                    "Eroare de stare fiscala DATECS (cod: $code). Verificati starea casei " +
                        "de marcat si consultati manualul DATECS DP-25MX."
                )
            } else {
                MappedError("COMM_ERROR", rawMsg)
            }
    }
}

/** Thrown when a fiscal job needs the USB register but no usable USB device is available. */
class UsbNotAvailableException(message: String) : Exception(message)
