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
    val nextIndex: Long = 1L
) {
    fun interval(): RecurrenceInterval = RecurrenceInterval.valueOf(intervalCode)
}
