package com.example.expensetracker.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.execSQL
import androidx.sqlite.driver.AndroidSQLiteDriver

/** Migrazione non distruttiva: le spese già presenti restano intatte. */
private val MIGRATION_1_2 = Migration(1, 2) { connection ->
    connection.execSQL(
        """CREATE TABLE IF NOT EXISTS `recurring_rules` (
            `sourceExpenseId` INTEGER NOT NULL,
            `intervalCode` TEXT NOT NULL,
            `firstDateEpochDay` INTEGER NOT NULL,
            `nextIndex` INTEGER NOT NULL,
            PRIMARY KEY(`sourceExpenseId`)
        )""".trimIndent()
    )
}

/** Aggiunge le categorie personalizzate senza toccare spese o ricorrenze. */
private val MIGRATION_2_3 = Migration(2, 3) { connection ->
    connection.execSQL(
        """CREATE TABLE IF NOT EXISTS `custom_categories` (
            `name` TEXT NOT NULL,
            `colorArgb` INTEGER NOT NULL,
            `iconKey` TEXT NOT NULL,
            PRIMARY KEY(`name`)
        )""".trimIndent()
    )
}

/** Archiviazione logica: nessun dato storico viene eliminato o riassegnato. */
private val MIGRATION_3_4 = Migration(3, 4) { connection ->
    connection.execSQL(
        "ALTER TABLE `custom_categories` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0"
    )
}

/** Nuove rate collegate alla serie: nessuna spesa storica viene rimossa. */
private val MIGRATION_4_5 = Migration(4, 5) { connection ->
    connection.execSQL("ALTER TABLE `expenses` ADD COLUMN `recurringSourceId` INTEGER")
    connection.execSQL("ALTER TABLE `expenses` ADD COLUMN `recurringOccurrenceIndex` INTEGER")
    connection.execSQL(
        """CREATE UNIQUE INDEX IF NOT EXISTS
        `index_expenses_recurringSourceId_recurringOccurrenceIndex`
        ON `expenses` (`recurringSourceId`, `recurringOccurrenceIndex`)""".trimIndent()
    )
}

/** Scadenze opzionali delle ricorrenze: i dati delle spese restano invariati. */
private val MIGRATION_5_6 = Migration(5, 6) { connection ->
    connection.execSQL("ALTER TABLE `recurring_rules` ADD COLUMN `expiryAfterMonths` INTEGER")
    connection.execSQL("ALTER TABLE `recurring_rules` ADD COLUMN `endDateEpochDay` INTEGER")
}

@Database(
    entities = [ExpenseEntity::class, RecurringRule::class, CustomCategory::class],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder<AppDatabase>(
                    context.applicationContext, "expenses.db"
                )
                    .setDriver(AndroidSQLiteDriver())
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
