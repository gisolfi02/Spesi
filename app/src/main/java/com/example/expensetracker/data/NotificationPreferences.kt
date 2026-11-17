package com.example.expensetracker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

// Una sola istanza di DataStore per processo, condivisa fra schermata e Worker.
private val Context.notificationDataStore by preferencesDataStore(name = "expense_notification_settings")

data class SummaryNotificationSettings(
    val weeklyEnabled: Boolean = false,
    val monthlyEnabled: Boolean = false,
    val lastWeeklyPeriod: String = "",
    val lastMonthlyPeriod: String = ""
)

class NotificationPreferences(context: Context) {
    private val appContext = context.applicationContext

    private object Keys {
        val WEEKLY = booleanPreferencesKey("weekly_enabled")
        val MONTHLY = booleanPreferencesKey("monthly_enabled")
        val LAST_WEEK = stringPreferencesKey("last_weekly_period")
        val LAST_MONTH = stringPreferencesKey("last_monthly_period")
    }

    val settingsFlow: Flow<SummaryNotificationSettings> =
        appContext.notificationDataStore.data.map { values ->
            SummaryNotificationSettings(
                weeklyEnabled = values[Keys.WEEKLY] ?: false,
                monthlyEnabled = values[Keys.MONTHLY] ?: false,
                lastWeeklyPeriod = values[Keys.LAST_WEEK].orEmpty(),
                lastMonthlyPeriod = values[Keys.LAST_MONTH].orEmpty()
            )
        }

    suspend fun current(): SummaryNotificationSettings = settingsFlow.first()

    suspend fun setWeeklyEnabled(enabled: Boolean) {
        appContext.notificationDataStore.edit { values ->
            val wasEnabled = values[Keys.WEEKLY] ?: false
            if (enabled && !wasEnabled) {
                // Non inviamo notifiche retroattive quando l'utente attiva il servizio.
                values[Keys.LAST_WEEK] = alreadyDueWeeklyPeriod()
            }
            values[Keys.WEEKLY] = enabled
        }
    }

    suspend fun setMonthlyEnabled(enabled: Boolean) {
        appContext.notificationDataStore.edit { values ->
            val wasEnabled = values[Keys.MONTHLY] ?: false
            if (enabled && !wasEnabled) {
                values[Keys.LAST_MONTH] = alreadyDueMonthlyPeriod()
            }
            values[Keys.MONTHLY] = enabled
        }
    }

    suspend fun markWeeklyReported(period: String) {
        appContext.notificationDataStore.edit { values ->
            if (values[Keys.WEEKLY] == true) values[Keys.LAST_WEEK] = period
        }
    }

    suspend fun markMonthlyReported(period: String) {
        appContext.notificationDataStore.edit { values ->
            if (values[Keys.MONTHLY] == true) values[Keys.LAST_MONTH] = period
        }
    }

    private fun alreadyDueWeeklyPeriod(): String {
        val now = LocalDateTime.now()
        val currentMonday = now.toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lastWeekMonday = currentMonday.minusWeeks(1)
        // Lunedì prima delle 09:00: la settimana passata non è ancora 'dovuta'.
        val lastAlreadyDue = if (
            now.dayOfWeek == DayOfWeek.MONDAY &&
            now.toLocalTime().isBefore(LocalTime.of(9, 0))
        ) lastWeekMonday.minusWeeks(1) else lastWeekMonday
        return lastAlreadyDue.toString()
    }

    private fun alreadyDueMonthlyPeriod(): String {
        val now = LocalDateTime.now()
        val previousMonth = YearMonth.from(now).minusMonths(1)
        val lastAlreadyDue = if (
            now.dayOfMonth == 1 &&
            now.toLocalTime().isBefore(LocalTime.of(9, 0))
        ) previousMonth.minusMonths(1) else previousMonth
        return lastAlreadyDue.toString()
    }
}
