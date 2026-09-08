package com.yammer.bridge.mobile.fiscal

/**
 * Decodes the 8 status bytes the DATECS DP-25MX ("X" protocol) returns in every
 * response frame. Port of the desktop `DatecsStatusDecoder` — same bit map,
 * same Romanian messages.
 */
object DatecsStatusDecoder {

    enum class Severity { ERROR, WARNING, INFO }

    private data class Bit(val byteIndex: Int, val bit: Int, val severity: Severity, val message: String)

    private val BITS = listOf(
        // S0 — general
        Bit(0, 0, Severity.ERROR, "Eroare de sintaxa in comanda"),
        Bit(0, 1, Severity.ERROR, "Cod de comanda invalid"),
        Bit(0, 2, Severity.WARNING, "Data/ora nesincronizate"),
        Bit(0, 4, Severity.ERROR, "Eroare in mecanismul de tiparire"),
        Bit(0, 5, Severity.ERROR, "Eroare generala a casei de marcat"),
        Bit(0, 6, Severity.ERROR, "Capacul imprimantei este deschis"),
        // S1 — general
        Bit(1, 0, Severity.ERROR, "Depasire (overflow) la executarea comenzii"),
        Bit(1, 1, Severity.ERROR, "Comanda nepermisa in acest context"),
        // S2 — paper / electronic journal / receipt
        Bit(2, 0, Severity.ERROR, "Sfarsit de hartie"),
        Bit(2, 1, Severity.WARNING, "Aproape sfarsit de hartie"),
        Bit(2, 2, Severity.ERROR, "Jurnalul electronic (KLEN) este plin"),
        Bit(2, 3, Severity.INFO, "Bon fiscal deschis"),
        Bit(2, 4, Severity.WARNING, "Jurnalul electronic (KLEN) aproape plin"),
        Bit(2, 5, Severity.INFO, "Bon de serviciu (nefiscal) deschis"),
        // S4 — fiscal memory
        Bit(4, 0, Severity.ERROR, "Eroare la accesul memoriei fiscale"),
        Bit(4, 3, Severity.WARNING, "Spatiu pentru mai putin de 60 de inchideri fiscale"),
        Bit(4, 4, Severity.ERROR, "Memoria fiscala este plina"),
        Bit(4, 5, Severity.ERROR, "Eroare la memoria fiscala"),
        Bit(4, 6, Severity.ERROR, "Memoria fiscala lipseste sau este deteriorata"),
        // S5 — fiscal memory (informative)
        Bit(5, 3, Severity.INFO, "Aparatul este in regim fiscal"),
    )

    class Decoded {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val info = mutableListOf<String>()

        fun hasErrors() = errors.isNotEmpty()
        fun hasWarnings() = warnings.isNotEmpty()
        fun summary(): String = (errors + warnings).joinToString("; ")
    }

    fun decode(status: ByteArray?): Decoded {
        val d = Decoded()
        if (status == null) return d
        for (def in BITS) {
            if (def.byteIndex >= status.size) continue
            val b = status[def.byteIndex].toInt() and 0xFF
            if (b and (1 shl def.bit) == 0) continue
            when (def.severity) {
                Severity.ERROR -> d.errors.add(def.message)
                Severity.WARNING -> d.warnings.add(def.message)
                Severity.INFO -> d.info.add(def.message)
            }
        }
        return d
    }

    fun toHex(status: ByteArray?): String =
        status?.mapIndexed { i, b -> "S$i=%02X".format(b.toInt() and 0xFF) }?.joinToString(" ")
            ?: "(null)"
}
