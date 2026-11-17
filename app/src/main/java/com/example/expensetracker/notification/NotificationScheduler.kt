package com.example.expensetracker.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.expensetracker.data.SummaryNotificationSettings
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Una verifica giornaliera, approssimativamente alle 09:00, che invia riepiloghi
 * solo per periodi conclusi non ancora notificati. Non richiede rete.
 */
object NotificationScheduler {
    private const val WORK_NAME = "expense_summary_daily_check"

    fun sync(context: Context, settings: SummaryNotificationSettings) {
        val manager = WorkManager.getInstance(context.applicationContext)

        if (!settings.weeklyEnabled && !settings.monthlyEnabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }

        val now = ZonedDateTime.now()
        var nextNine = now.withHour(9).withMinute(0).withSecond(0).withNano(0)
        if (!nextNine.isAfter(now)) nextNine = nextNine.plusDays(1)
        val delayMillis = Duration.between(now, nextNine).toMillis().coerceAtLeast(1)

        val request = PeriodicWorkRequestBuilder<ExpenseNotificationWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }
}
