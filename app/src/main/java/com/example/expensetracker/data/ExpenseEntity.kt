package com.example.expensetracker.data

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["dateEpochDay"])]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val amountCents: Long,

    val category: String,

    val dateEpochDay: Long,

    val merchant: String = "",

    val paymentMethod: String = "Non specificato",

    val note: String = "",

    val isRecurring: Boolean = false,

    val receiptUri: String? = null
)
