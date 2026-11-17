package com.example.expensetracker.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Solo categorie create dall'utente. Le sei categorie iniziali sono sempre
 * disponibili e definite nel catalogo dell'interfaccia.
 * Il nome rimane la chiave usata dalle spese, senza alterare ExpenseEntity.
 */
@Entity(tableName = "custom_categories")
data class CustomCategory(
    @PrimaryKey val name: String,
    val colorArgb: Long,
    val iconKey: String,
    // La categoria rimane nel DB per conservare colore e icona delle vecchie spese.
    @ColumnInfo(defaultValue = "0")
    val isArchived: Boolean = false
)
