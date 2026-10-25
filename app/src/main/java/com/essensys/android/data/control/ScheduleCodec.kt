package com.essensys.android.data.control

/**
 * Planning hebdomadaire d'une zone : 7 jours × 24 heures, valeurs brutes sur 3 bits (spec heating-control).
 * Index linéaire = jour × 24 + heure, lundi = jour 0.
 */
data class ScheduleGrid(val slots: List<Int>) {
    init {
        require(slots.size == DAYS * HOURS) { "grille de ${slots.size} créneaux" }
    }

    operator fun get(day: Int, hour: Int): Int = slots[day * HOURS + hour]

    fun with(day: Int, hour: Int, value: Int): ScheduleGrid =
        ScheduleGrid(slots.toMutableList().also { it[day * HOURS + hour] = value })

    /** « Appliquer [jour] à toute la semaine » (`copyDayToAll`). */
    fun copyDayToAll(sourceDay: Int): ScheduleGrid {
        val row = slots.subList(sourceDay * HOURS, (sourceDay + 1) * HOURS)
        return ScheduleGrid(List(DAYS) { row }.flatten())
    }

    companion object {
        const val DAYS = 7
        const val HOURS = 24
        val EMPTY = ScheduleGrid(List(DAYS * HOURS) { 0 })
        val DAY_LABELS = listOf("Lu", "Ma", "Me", "Je", "Ve", "Sa", "Di")
    }
}

/**
 * Codage du planning, porté de `essensys-user-portal-frontend/src/heating/scheduleCodec.ts` :
 * un octet = 2 heures, heure paire en bits 0–2, heure impaire en bits 4–6. Verrouillé par NR-android-13.
 */
object ScheduleCodec {

    fun decodeByte(byte: Int): Pair<Int, Int> = (byte and 0x07) to ((byte shr 4) and 0x07)

    fun encodeByte(even: Int, odd: Int): Int = (even and 0x07) or ((odd and 0x07) shl 4)

    /** Indice de l'octet qui porte le créneau (jour, heure) d'une zone. */
    fun indexOf(scheduleStart: Int, day: Int, hour: Int): Int =
        scheduleStart + day * IndexTable.SCHEDULE_BYTES_PER_DAY + hour / 2

    /** Grille depuis les valeurs lues ; un octet absent ou illisible est décodé OFF. */
    fun decode(values: Map<Int, String>, scheduleStart: Int): ScheduleGrid {
        val slots = MutableList(ScheduleGrid.DAYS * ScheduleGrid.HOURS) { 0 }
        for (byteIndex in 0 until IndexTable.SCHEDULE_BYTES) {
            val (even, odd) = decodeByte(values[scheduleStart + byteIndex]?.trim()?.toIntOrNull() ?: 0)
            slots[byteIndex * 2] = even
            slots[byteIndex * 2 + 1] = odd
        }
        return ScheduleGrid(slots)
    }

    /**
     * Référence après un envoi partiel : les octets [sent] prennent la valeur de [grid], les autres
     * gardent celle de [baseline] (ils restent « modifiés » et seront renvoyés).
     */
    fun acceptSent(baseline: ScheduleGrid, grid: ScheduleGrid, scheduleStart: Int, sent: List<Injection>): ScheduleGrid {
        val slots = baseline.slots.toMutableList()
        sent.forEach { injection ->
            val byteIndex = injection.k - scheduleStart
            if (byteIndex in 0 until IndexTable.SCHEDULE_BYTES) {
                slots[byteIndex * 2] = grid.slots[byteIndex * 2]
                slots[byteIndex * 2 + 1] = grid.slots[byteIndex * 2 + 1]
            }
        }
        return ScheduleGrid(slots)
    }

    /** Injections des seuls octets dont l'un des deux créneaux a changé, dans l'ordre des indices. */
    fun diff(grid: ScheduleGrid, baseline: ScheduleGrid, scheduleStart: Int): List<Injection> =
        (0 until IndexTable.SCHEDULE_BYTES).mapNotNull { byteIndex ->
            val even = grid.slots[byteIndex * 2]
            val odd = grid.slots[byteIndex * 2 + 1]
            if (even == baseline.slots[byteIndex * 2] && odd == baseline.slots[byteIndex * 2 + 1]) null
            else Injection(scheduleStart + byteIndex, encodeByte(even, odd).toString())
        }
}
