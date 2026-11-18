package com.example.expensetracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.example.expensetracker.MainActivity
import com.example.expensetracker.R
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.ui.components.ExpenseCategoryCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Aggiorna tutte le istanze del widget: usa solo Room, senza rete. */
object SpesiWidgetUpdater {
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, SpesiMonthlyWidgetProvider::class.java)
        val widgetIds = manager.getAppWidgetIds(component)
        if (widgetIds.isEmpty()) return@withContext

        val month = YearMonth.now()
        val start = month.atDay(1).toEpochDay()
        val endExclusive = month.plusMonths(1).atDay(1).toEpochDay()
        val dao = AppDatabase.getDatabase(appContext).expenseDao()
        val expenses = dao.observeMonthlyExpenses(start, endExclusive).first()
        val customCategories = dao.getCustomCategories()
        val total = expenses.sumOf { it.amountCents }
        val totals = expenses.groupBy { it.category }
            .mapValues { (_, rows) -> rows.sumOf { it.amountCents } }
            .filterValues { it > 0L }
        val ordered = totals.entries.sortedByDescending { it.value }
        val coloredSegments = ordered.map { (name, amount) ->
            ExpenseCategoryCatalog.style(name, customCategories).color.toArgb() to amount
        }
        val donut = SpesiDonutRenderer.render(coloredSegments, ordered.size)
        val euroFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.ITALY))
        val totalLabel = "€${euroFormat.format(total / 100.0)}"
        val monthLabel = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN))
            .replaceFirstChar { it.titlecase(Locale.ITALIAN) }

        val top = ordered.firstOrNull()
        val topText = if (top == null) {
            "Nessuna spesa registrata"
        } else {
            val percent = if (total > 0) (top.value * 100.0 / total).toInt() else 0
            "● ${top.key} · $percent%"
        }
        val topColor = if (top == null) {
            android.graphics.Color.rgb(112, 117, 143)
        } else {
            ExpenseCategoryCatalog.style(top.key, customCategories).color.toArgb()
        }

        val views = RemoteViews(appContext.packageName, R.layout.widget_spesi_month)
        views.setTextViewText(R.id.widget_month, monthLabel)
        views.setTextViewText(R.id.widget_total, totalLabel)
        views.setTextViewText(R.id.widget_top_category, topText)
        views.setTextColor(R.id.widget_top_category, topColor)
        views.setImageViewBitmap(R.id.widget_chart, donut)

        val openApp = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        // Aggiornamento completo: evita l'accumulo di bitmap nei widget.
        manager.updateAppWidget(widgetIds, views)
    }
}
