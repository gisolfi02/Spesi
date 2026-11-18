package com.example.expensetracker.data

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

data class CategoryTotal(
    val category: String,
    val totalCents: Long
)

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses ORDER BY dateEpochDay DESC, id DESC")
    fun observeAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("""
        SELECT * FROM expenses
        WHERE dateEpochDay >= :startDay
        AND dateEpochDay < :endDayExclusive
        ORDER BY dateEpochDay DESC, id DESC
    """)
    fun observeMonthlyExpenses(
        startDay: Long,
        endDayExclusive: Long
    ): Flow<List<ExpenseEntity>>

    @Query("""
        SELECT COALESCE(SUM(amountCents), 0)
        FROM expenses
        WHERE dateEpochDay >= :startDay
        AND dateEpochDay < :endDayExclusive
    """)
    fun observeMonthlyTotal(
        startDay: Long,
        endDayExclusive: Long
    ): Flow<Long>

    @Query("""
        SELECT category, SUM(amountCents) AS totalCents
        FROM expenses
        WHERE dateEpochDay >= :startDay
        AND dateEpochDay < :endDayExclusive
        GROUP BY category
        ORDER BY totalCents DESC
    """)
    fun observeCategoryTotals(
        startDay: Long,
        endDayExclusive: Long
    ): Flow<List<CategoryTotal>>

    // Nuove operazioni utilizzate SOLO per il ripristino dei backup.
    // Chiamarle insieme all'interno di AppDatabase.withWriteTransaction { ... }.
    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(expenses: List<ExpenseEntity>)


    // Ricorrenze. Una regola fa riferimento alla spesa originale.
    @Query("SELECT * FROM recurring_rules")
    suspend fun getRecurringRules(): List<RecurringRule>

    @Query("SELECT * FROM recurring_rules WHERE sourceExpenseId = :sourceId LIMIT 1")
    suspend fun getRecurringRule(sourceId: Long): RecurringRule?

    @Insert(onConflict = androidx.room3.OnConflictStrategy.REPLACE)
    suspend fun putRecurringRule(rule: RecurringRule)

    @Query("UPDATE recurring_rules SET nextIndex = :index WHERE sourceExpenseId = :sourceId")
    suspend fun updateNextIndex(sourceId: Long, index: Long)

    @Query("DELETE FROM recurring_rules WHERE sourceExpenseId = :sourceId")
    suspend fun deleteRecurringRule(sourceId: Long)

    @Query("DELETE FROM recurring_rules")
    suspend fun deleteAllRecurringRules()

    @Insert
    suspend fun insertAllRecurringRules(rules: List<RecurringRule>)

    // Occorrenze generate dalla versione 5: collegate alla spesa sorgente.
    @Query("DELETE FROM expenses WHERE recurringSourceId = :sourceId")
    suspend fun deleteTrackedOccurrences(sourceId: Long): Int

    // Elimina SOLO le rate ancora collegate alla serie e successive alla scadenza.
    // Le rate modificate individualmente sono scollegate e non vengono toccate.
    @Query("DELETE FROM expenses WHERE recurringSourceId = :sourceId AND dateEpochDay > :lastAllowedDay")
    suspend fun deleteTrackedOccurrencesAfter(sourceId: Long, lastAllowedDay: Long): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE recurringSourceId = :sourceId")
    suspend fun countTrackedOccurrences(sourceId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE recurringSourceId = :sourceId AND recurringOccurrenceIndex = :occurrenceIndex)")
    suspend fun trackedOccurrenceExists(sourceId: Long, occurrenceIndex: Long): Boolean

    @Insert(onConflict = androidx.room3.OnConflictStrategy.IGNORE)
    suspend fun insertTrackedOccurrence(expense: ExpenseEntity): Long

    // Categorie create dall'utente (mantengono nome, colore e icona).
    @Query("SELECT * FROM custom_categories ORDER BY name COLLATE NOCASE ASC")
    fun observeCustomCategories(): Flow<List<CustomCategory>>

    @Query("SELECT * FROM custom_categories ORDER BY name COLLATE NOCASE ASC")
    suspend fun getCustomCategories(): List<CustomCategory>

    @Insert
    suspend fun insertCustomCategory(category: CustomCategory)

    // Eliminazione sicura delle categorie personalizzate.
    @Query("SELECT COUNT(*) FROM expenses WHERE category = :name")
    suspend fun countExpensesInCategory(name: String): Int

    // Archivia, senza toccare la categoria delle spese già registrate.
    @Query("UPDATE custom_categories SET isArchived = 1 WHERE name = :name AND isArchived = 0")
    suspend fun archiveCustomCategory(name: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM custom_categories WHERE name = :name AND isArchived = 1)")
    suspend fun isCustomCategoryArchived(name: String): Boolean

    // Interrompi le regole future; nessuna rata già generata viene modificata.
    @Query("""
        UPDATE expenses SET isRecurring = 0
        WHERE id IN (
            SELECT r.sourceExpenseId FROM recurring_rules r
            INNER JOIN expenses e ON e.id = r.sourceExpenseId
            WHERE e.category = :name
        )
    """)
    suspend fun stopRecurringExpenseSourcesInCategory(name: String): Int

    @Query("""
        DELETE FROM recurring_rules
        WHERE sourceExpenseId IN (SELECT id FROM expenses WHERE category = :name)
    """)
    suspend fun deleteRecurringRulesForCategory(name: String): Int

    // Operazioni usate nel ripristino atomico dei backup.
    @Query("DELETE FROM custom_categories")
    suspend fun deleteAllCustomCategories()

    @Insert
    suspend fun insertAllCustomCategories(categories: List<CustomCategory>)
}
