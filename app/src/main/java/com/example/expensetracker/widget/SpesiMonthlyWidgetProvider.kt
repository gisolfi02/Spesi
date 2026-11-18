package com.example.expensetracker.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receiver richiamato dal launcher quando il widget viene aggiunto o aggiornato. */
class SpesiMonthlyWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                SpesiWidgetUpdater.refresh(context)
            } catch (error: Exception) {
                Log.e("SpesiWidget", "Impossibile aggiornare il widget", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
