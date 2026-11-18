package com.example.expensetracker.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Una regola è associata alla PRIMA spesa salvata (sourceExpenseId).
 * nextIndex = numero della prossima occorrenza ancora da registrare.
 * La prima spesa (indice 0) è già in `expenses`.
 */
@Entity(tableName = "recurring_rules")
data class RecurringRule(
    @PrimaryKey val sourceExpenseId: Long,
    val intervalCode: String,
    val firstDateEpochDay: Long,
    val nextIndex: Long = 1L,
    // Scadenza relativa alla prima data (es. 2 mesi dopo l'inizio).
    val expiryAfterMonths: Int? = null,
    // Scadenza assoluta, in alternativa a expiryAfterMonths.
    val endDateEpochDay: Long? = null
) {
    fun interval(): RecurrenceInterval = RecurrenceInterval.valueOf(intervalCode)

    fun expiryDate(): java.time.LocalDate? = when {
        expiryAfterMonths != null ->
            java.time.LocalDate.ofEpochDay(firstDateEpochDay)
                .plusMonths(expiryAfterMonths.toLong())
        endDateEpochDay != null -> java.time.LocalDate.ofEpochDay(endDateEpochDay)
        else -> null
    }
}
