package com.example.expensetracker.data

import java.time.LocalDate

/** Frequenze stabili: il nome dell'enum viene memorizzato nel database e nel backup. */
enum class RecurrenceInterval(val label: String, val explanation: String) {
    WEEKLY("Ogni settimana", "Ogni 7 giorni"),
    BIWEEKLY("Ogni 2 settimane", "Ogni 14 giorni"),
    MONTHLY("Ogni mese", "Stesso giorno di ogni mese"),
    BIMONTHLY("Ogni 2 mesi", "Ogni due mesi"),
    QUARTERLY("Ogni 3 mesi", "Ogni trimestre"),
    SEMIANNUAL("Ogni 6 mesi", "Due volte all'anno"),
    ANNUAL("Ogni anno", "Una volta all'anno");

    fun occurrenceDate(firstDate: LocalDate, occurrenceIndex: Long): LocalDate {
        require(occurrenceIndex >= 0) { "Indice di ricorrenza non valido" }
        return when (this) {
            WEEKLY -> firstDate.plusWeeks(occurrenceIndex)
            BIWEEKLY -> firstDate.plusWeeks(Math.multiplyExact(occurrenceIndex, 2L))
            MONTHLY -> firstDate.plusMonths(occurrenceIndex)
            BIMONTHLY -> firstDate.plusMonths(Math.multiplyExact(occurrenceIndex, 2L))
            QUARTERLY -> firstDate.plusMonths(Math.multiplyExact(occurrenceIndex, 3L))
            SEMIANNUAL -> firstDate.plusMonths(Math.multiplyExact(occurrenceIndex, 6L))
            ANNUAL -> firstDate.plusYears(occurrenceIndex)
        }
    }
}
