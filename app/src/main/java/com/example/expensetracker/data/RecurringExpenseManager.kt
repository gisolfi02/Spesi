package com.example.expensetracker.data

import android.content.Context
import androidx.room3.withWriteTransaction
import com.example.expensetracker.recurrence.RecurringExpenseWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Operazioni sulle ricorrenze; ogni modifica su due tabelle è atomica. */
object RecurringExpenseManager {
    private const val MAX_GENERATED_PER_CYCLE = 500

    fun ensurePeriodicCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<RecurringExpenseWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            "expensetracker_recurring_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Crea o modifica una spesa. Le eventuali occorrenze future leggono gli ultimi
     * valori della spesa originale. Le occorrenze già generate non vengono riscritte.
     */
    suspend fun saveExpense(
        context: Context,
        expense: ExpenseEntity,
        interval: RecurrenceInterval?
    ): ExpenseEntity = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.expenseDao()
        val stored = db.withWriteTransaction {
            val newValue = expense.copy(isRecurring = interval != null)
            val archived = dao.isCustomCategoryArchived(newValue.category)
            require(!archived || (newValue.id != 0L && interval == null)) {
                "Categoria archiviata: seleziona una categoria attiva per le nuove ricorrenze"
            }
            val actual = if (newValue.id == 0L) {
                val id = dao.insert(newValue)
                newValue.copy(id = id)
            } else {
                dao.update(newValue)
                newValue
            }
            val oldRule = dao.getRecurringRule(actual.id)
            if (interval == null) {
                dao.deleteRecurringRule(actual.id)
            } else {
                val sameRule = oldRule != null &&
                        oldRule.intervalCode == interval.name &&
                        oldRule.firstDateEpochDay == actual.dateEpochDay
                // Se l'utente cambia data/frequenza, si riparte dalla prossima
                // scadenza FUTURA, senza rigenerare rate passate già registrate.
                val next = when {
                    sameRule -> oldRule!!.nextIndex
                    oldRule != null -> firstFutureIndex(
                        LocalDate.ofEpochDay(actual.dateEpochDay), interval, LocalDate.now()
                    )
                    else -> 1L
                }
                dao.putRecurringRule(
                    RecurringRule(actual.id, interval.name, actual.dateEpochDay, next)
                )
            }
            actual
        }
        ensurePeriodicCheck(context)
        materializeDue(context)
        stored
    }

    suspend fun deleteExpense(context: Context, expense: ExpenseEntity) =
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.withWriteTransaction {
                db.expenseDao().deleteRecurringRule(expense.id)
                db.expenseDao().delete(expense)
            }
        }

    /**
     * Genera esclusivamente le rate scadute (non quelle future), una sola volta.
     * L'incremento di nextIndex è nella STESSA transazione degli inserimenti.
     * Se il lavoro viene interrotto o ritentato non duplica le spese.
     */
    suspend fun materializeDue(context: Context): Int = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val today = LocalDate.now()
        db.withWriteTransaction {
            val dao = db.expenseDao()
            var generated = 0
            for (rule in dao.getRecurringRules()) {
                val source = dao.getById(rule.sourceExpenseId)
                if (source == null || !source.isRecurring ||
                    dao.isCustomCategoryArchived(source.category)) {
                    dao.deleteRecurringRule(rule.sourceExpenseId)
                    continue
                }
                val interval = rule.interval()
                val first = LocalDate.ofEpochDay(rule.firstDateEpochDay)
                var next = rule.nextIndex
                while (generated < MAX_GENERATED_PER_CYCLE &&
                    !interval.occurrenceDate(first, next).isAfter(today)) {
                    val day = interval.occurrenceDate(first, next).toEpochDay()
                    dao.insert(
                        source.copy(
                            id = 0L,
                            dateEpochDay = day,
                            // La rata è una spesa autonoma, non una nuova regola.
                            isRecurring = false,
                            receiptUri = null
                        )
                    )
                    next += 1L
                    generated++
                }
                if (next != rule.nextIndex) dao.updateNextIndex(rule.sourceExpenseId, next)
                if (generated == MAX_GENERATED_PER_CYCLE) break
            }
            generated
        }
    }

    private fun firstFutureIndex(
        start: LocalDate, frequency: RecurrenceInterval, today: LocalDate
    ): Long {
        var index = 1L
        while (!frequency.occurrenceDate(start, index).isAfter(today)) {
            index++
            require(index < 100_000L) { "Intervallo di ricorrenza troppo lungo" }
        }
        return index
    }
}
