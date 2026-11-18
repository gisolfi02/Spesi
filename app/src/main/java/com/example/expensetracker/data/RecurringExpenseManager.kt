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
     * La data della spesa originale è l'ancora della serie. Dopo un cambio di
     * data o frequenza, sostituiamo SOLO le occorrenze sicuramente tracciate.
     * Le vecchie rate v4 prive di legame restano intatte per sicurezza.
     */
    suspend fun saveExpense(
        context: Context,
        expense: ExpenseEntity,
        interval: RecurrenceInterval?,
        expiryAfterMonths: Int? = null,
        endDateEpochDay: Long? = null
    ): ExpenseEntity = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.expenseDao()
        val stored = db.withWriteTransaction {
            val previous = if (expense.id != 0L) dao.getById(expense.id) else null
            // Una rata modificata singolarmente diventa autonoma; una modifica
            // successiva alla serie non deve cancellare una spesa personalizzata.
            val detachedOccurrence = expense.recurringSourceId != null
            val newValue = expense.copy(
                isRecurring = interval != null && !detachedOccurrence,
                recurringSourceId = if (detachedOccurrence) null else expense.recurringSourceId,
                recurringOccurrenceIndex = if (detachedOccurrence) null else expense.recurringOccurrenceIndex
            )
            val archived = dao.isCustomCategoryArchived(newValue.category)
            require(!archived || (newValue.id != 0L && interval == null)) {
                "Categoria archiviata: seleziona una categoria attiva per le nuove ricorrenze"
            }
            val actual = if (newValue.id == 0L) {
                newValue.copy(id = dao.insert(newValue))
            } else {
                dao.update(newValue)
                newValue
            }

            val oldRule = dao.getRecurringRule(actual.id)
            if (interval == null || detachedOccurrence) {
                // Comportamento esistente: disattivare la serie non cancella
                // lo storico delle rate già generate.
                dao.deleteRecurringRule(actual.id)
            } else {
                require(!(expiryAfterMonths != null && endDateEpochDay != null)) {
                    "Scegli un solo tipo di scadenza"
                }
                require(expiryAfterMonths == null || expiryAfterMonths in 1..1200) {
                    "La durata deve essere compresa tra 1 e 1200 mesi"
                }
                val start = LocalDate.ofEpochDay(actual.dateEpochDay)
                val fixedEnd = endDateEpochDay?.let(LocalDate::ofEpochDay)
                require(fixedEnd == null ||
                        (!fixedEnd.isBefore(start) && !fixedEnd.isAfter(start.plusYears(100)))) {
                    "La scadenza deve essere successiva all'inizio (massimo 100 anni)"
                }

                val scheduleChanged = oldRule == null ||
                        oldRule.intervalCode != interval.name ||
                        oldRule.firstDateEpochDay != actual.dateEpochDay ||
                        previous?.dateEpochDay != actual.dateEpochDay
                val expiryChanged = oldRule?.expiryAfterMonths != expiryAfterMonths ||
                        oldRule?.endDateEpochDay != endDateEpochDay

                val rule = RecurringRule(
                    sourceExpenseId = actual.id,
                    intervalCode = interval.name,
                    firstDateEpochDay = actual.dateEpochDay,
                    nextIndex = if (scheduleChanged) 1L else oldRule!!.nextIndex,
                    expiryAfterMonths = expiryAfterMonths,
                    endDateEpochDay = endDateEpochDay
                )
                if (scheduleChanged) {
                    // Cambiando data o frequenza, rigeneriamo SOLO le rate
                    // sicuramente automatiche (a partire dal database v5).
                    dao.deleteTrackedOccurrences(actual.id)
                } else if (expiryChanged && rule.expiryDate() != null) {
                    val cutoff = rule.expiryDate()!!
                    dao.deleteTrackedOccurrencesAfter(actual.id, cutoff.toEpochDay())
                }

                // Se la scadenza viene anticipata, torniamo all'indice
                // della prima rata rimossa. Un successivo prolungamento
                // potrà ricrearla senza saltare date o duplicare spese.
                val nextIndex = if (!scheduleChanged && expiryChanged &&
                    rule.expiryDate() != null) {
                    minOf(rule.nextIndex, firstIndexAfterEnd(start, interval, rule.expiryDate()!!))
                } else rule.nextIndex
                dao.putRecurringRule(rule.copy(nextIndex = nextIndex))
            }
            actual
        }
        ensurePeriodicCheck(context)
        // Non attendiamo il Worker: eventuali scadenze pregresse vengono
        // materializzate immediatamente, anche dopo cambio data o frequenza.
        materializeDue(context)
        stored
    }

    /** Restituisce l'indice della prima rata oltre la data finale inclusiva. */
    private fun firstIndexAfterEnd(
        start: LocalDate,
        interval: RecurrenceInterval,
        end: LocalDate
    ): Long {
        var index = 1L
        while (!interval.occurrenceDate(start, index).isAfter(end)) index++
        return index
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
                val expiry = rule.expiryDate()
                while (generated < MAX_GENERATED_PER_CYCLE) {
                    val due = interval.occurrenceDate(first, next)
                    if (due.isAfter(today) || (expiry != null && due.isAfter(expiry))) break
                    val day = due.toEpochDay()
                    if (!dao.trackedOccurrenceExists(source.id, next)) {
                        val inserted = dao.insertTrackedOccurrence(
                            source.copy(
                                id = 0L,
                                dateEpochDay = day,
                                isRecurring = false,
                                receiptUri = null,
                                recurringSourceId = source.id,
                                recurringOccurrenceIndex = next
                            )
                        )
                        if (inserted != -1L) generated++
                    }
                    next += 1L
                }
                if (next != rule.nextIndex) dao.updateNextIndex(rule.sourceExpenseId, next)
                if (generated == MAX_GENERATED_PER_CYCLE) break
            }
            generated
        }
    }

}
