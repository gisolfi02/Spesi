package com.example.expensetracker.data

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["dateEpochDay"]),
        Index(value = ["recurringSourceId", "recurringOccurrenceIndex"], unique = true)
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val category: String,
    val dateEpochDay: Long,
    val merchant: String = "",
    val paymentMethod: String = "Non specificato",
    val note: String = "",
    val isRecurring: Boolean = false,
    val receiptUri: String? = null,
    // Tracciamento delle rate generate a partire dalla versione 5 del database.
    // Le spese manuali e le vecchie rate non identificate restano NULL.
    val recurringSourceId: Long? = null,
    val recurringOccurrenceIndex: Long? = null
)
