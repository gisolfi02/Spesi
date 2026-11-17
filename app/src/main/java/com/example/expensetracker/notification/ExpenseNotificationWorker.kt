package com.example.expensetracker.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.expensetracker.MainActivity
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.data.NotificationPreferences
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import com.example.expensetracker.R

class ExpenseNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val preferences = NotificationPreferences(context)
            val settings = preferences.current()
            if (!settings.weeklyEnabled && !settings.monthlyEnabled) return Result.success()

            // Le 09:00 sono indicative: WorkManager può eseguire in ritardo.
            val now = LocalDateTime.now()
            if (now.toLocalTime().isBefore(LocalTime.of(9, 0))) return Result.success()
            if (!ExpenseNotifications.canPost(context)) return Result.success()

            val dao = AppDatabase.getDatabase(context).expenseDao()

            if (settings.weeklyEnabled) {
                val mondayThisWeek = now.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val mondayLastWeek = mondayThisWeek.minusWeeks(1)
                val key = mondayLastWeek.toString()

                if (settings.lastWeeklyPeriod != key) {
                    val total = dao.observeMonthlyTotal(
                        mondayLastWeek.toEpochDay(), mondayThisWeek.toEpochDay()
                    ).first()
                    val end = mondayThisWeek.minusDays(1)
                    val title = "Riepilogo settimanale"
                    val detail = "Dal ${mondayLastWeek.format(DATE_FORMAT)} al ${end.format(DATE_FORMAT)} " +
                        "hai speso ${ExpenseNotifications.euro(total)}."
                    if (ExpenseNotifications.post(context, 1101, title, detail)) {
                        preferences.markWeeklyReported(key)
                    }
                }
            }

            if (settings.monthlyEnabled) {
                val previousMonth = YearMonth.from(now).minusMonths(1)
                val key = previousMonth.toString()

                if (settings.lastMonthlyPeriod != key) {
                    val total = dao.observeMonthlyTotal(
                        previousMonth.atDay(1).toEpochDay(),
                        previousMonth.plusMonths(1).atDay(1).toEpochDay()
                    ).first()
                    val monthName = previousMonth.format(
                        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
                    ).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString() }
                    val title = "Riepilogo mensile"
                    val detail = "A $monthName hai speso ${ExpenseNotifications.euro(total)}."
                    if (ExpenseNotifications.post(context, 1102, title, detail)) {
                        preferences.markMonthlyReported(key)
                    }
                }
            }
            Result.success()
        } catch (_: Exception) {
            // Un guasto temporaneo del database non deve annullare il lavoro periodico.
            Result.retry()
        }
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM", Locale.ITALIAN)
    }
}

/** Pubblicazione delle notifiche; usato anche dal pulsante di prova. */
object ExpenseNotifications {
    private const val CHANNEL_ID = "expense_periodic_summaries"

    fun euro(cents: Long): String {
        val formatter = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.ITALIAN))
        return "€${formatter.format(BigDecimal.valueOf(cents, 2))}"
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun sendTest(context: Context): Boolean = post(
        context,
        1103,
        "ExpenseTracker · Notifica di prova",
        "Le notifiche sono attive! I riepiloghi useranno le spese salvate sul telefono."
    )

    @SuppressLint("MissingPermission")
    fun post(context: Context, notificationId: Int, title: String, message: String): Boolean {
        if (!canPost(context)) return false

        return try {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Riepiloghi delle spese",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Totali settimanali e mensili delle spese"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_spesa)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
