package com.example.expensetracker.recurrence

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.expensetracker.data.RecurringExpenseManager

/** Verifica giornaliera offline: l'orario di esecuzione non è esatto. */
class RecurringExpenseWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        RecurringExpenseManager.materializeDue(applicationContext)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
