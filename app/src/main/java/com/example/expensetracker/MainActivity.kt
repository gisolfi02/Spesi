package com.example.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.ui.screens.HomeScreen
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme
import com.example.expensetracker.widget.SpesiWidgetUpdater
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current

            // Quando Room segnala variazioni nelle spese o categorie,
            // aggiorna il widget anche senza attendere il refresh periodico.
            LaunchedEffect(context) {
                val dao = AppDatabase.getDatabase(context).expenseDao()
                combine(
                    dao.observeAllExpenses(),
                    dao.observeCustomCategories()
                ) { _, _ -> Unit }.collectLatest {
                    try {
                        SpesiWidgetUpdater.refresh(context)
                    } catch (_: Exception) {
                        // Un errore del widget non deve interrompere l'app.
                    }
                }
            }

            ExpenseTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen()
                }
            }
        }
    }
}
