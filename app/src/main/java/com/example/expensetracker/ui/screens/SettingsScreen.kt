package com.example.expensetracker.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.expensetracker.data.NotificationPreferences
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.data.ExpenseBackupManager
import com.example.expensetracker.data.ExpenseBackupPreview
import com.example.expensetracker.data.SummaryNotificationSettings
import com.example.expensetracker.notifications.NotificationScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val SettingsInk = Color(0xFF11132B)
private val SettingsMuted = Color(0xFF70758F)
private val SettingsPurple = Color(0xFF7138EB)

private data class PendingBackupImport(
    val backup: ExpenseBackupPreview,
    val currentExpenseCount: Int
)

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferences = remember(context) { NotificationPreferences(context.applicationContext) }
    val settings by preferences.settingsFlow.collectAsState(
        initial = SummaryNotificationSettings()
    )
    val scope = rememberCoroutineScope()
    var pendingAction by remember { mutableStateOf<String?>(null) }

    var backupBusy by remember { mutableStateOf(false) }
    var pendingBackup by remember { mutableStateOf<PendingBackupImport?>(null) }

    // Android mostra il selettore file di sistema: nessun permesso di archiviazione richiesto.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                backupBusy = true
                try {
                    val savedCount = ExpenseBackupManager.exportToUri(context, uri)
                    Toast.makeText(
                        context,
                        "Backup esportato: $savedCount spese",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Esportazione non riuscita: ${e.message ?: "errore sconosciuto"}",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    backupBusy = false
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                backupBusy = true
                try {
                    val preview = ExpenseBackupManager.inspectUri(context, uri)
                    val currentCount = withContext(Dispatchers.IO) {
                        AppDatabase.getDatabase(context)
                            .expenseDao().observeAllExpenses().first().size
                    }
                    pendingBackup = PendingBackupImport(preview, currentCount)
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "File non valido: ${e.message ?: "impossibile leggerlo"}",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    backupBusy = false
                }
            }
        }
    }

    fun changeWeekly(enabled: Boolean) {
        scope.launch {
            try {
                preferences.setWeeklyEnabled(enabled)
                NotificationScheduler.sync(context, preferences.current())
            } catch (_: Exception) {
                Toast.makeText(context, "Impossibile aggiornare le impostazioni", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun changeMonthly(enabled: Boolean) {
        scope.launch {
            try {
                preferences.setMonthlyEnabled(enabled)
                NotificationScheduler.sync(context, preferences.current())
            } catch (_: Exception) {
                Toast.makeText(context, "Impossibile aggiornare le impostazioni", Toast.LENGTH_LONG).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted) {
            when (action) {
                "weekly" -> changeWeekly(true)
                "monthly" -> changeMonthly(true)
            }
        } else {
            Toast.makeText(
                context,
                "Autorizza le notifiche per attivare i riepiloghi.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun hasRuntimePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

    fun askPermissionOr(action: String, grantedAction: () -> Unit) {
        if (hasRuntimePermission()) {
            grantedAction()
        } else {
            pendingAction = action
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
            .background(Color(0xFFF8F8FF))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF0EBFF))
                    .clickable(onClick = onBackClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro", tint = SettingsInk)
            }
            Spacer(Modifier.width(14.dp))
            Text("Impostazioni", fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, color = SettingsInk)
        }

        Card(
            shape = RoundedCornerShape(25.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFF0EAFE), Color(0xFFDCD5FE))
                        )
                    )
                    .padding(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Riepiloghi delle spese",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = SettingsInk
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Ricevi sul telefono il totale delle spese.",
                        fontSize = 13.sp,
                        color = SettingsMuted,
                        lineHeight = 19.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(68.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(SettingsPurple),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    "Notifiche automatiche",
                    fontSize = 21.sp, fontWeight = FontWeight.Bold,
                    color = SettingsInk
                )
                Spacer(Modifier.height(12.dp))

                NotificationToggleRow(
                    icon = Icons.Filled.Today,
                    title = "Riepilogo settimanale",
                    subtitle = "Lunedì, dalle 09:00 · settimana precedente",
                    checked = settings.weeklyEnabled,
                    onCheckedChange = { checked ->
                        if (!checked) changeWeekly(false)
                        else askPermissionOr("weekly") { changeWeekly(true) }
                    }
                )
                HorizontalDivider(color = Color(0xFFF0EDF8), thickness = 1.dp)
                NotificationToggleRow(
                    icon = Icons.Filled.CalendarMonth,
                    title = "Riepilogo mensile",
                    subtitle = "Il 1° del mese, dalle 09:00 · mese precedente",
                    checked = settings.monthlyEnabled,
                    onCheckedChange = { checked ->
                        if (!checked) changeMonthly(false)
                        else askPermissionOr("monthly") { changeMonthly(true) }
                    }
                )
            }
        }

        // Backup locale: creazione JSON / importazione con conferma esplicita.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF0EAFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.SaveAlt,
                            contentDescription = null,
                            tint = SettingsPurple,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Backup delle spese",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = SettingsInk
                        )
                        Text(
                            "Esporta e ripristina i tuoi dati",
                            fontSize = 12.sp,
                            color = SettingsMuted
                        )
                    }
                }
                Text(
                    "Salva tutte le transazioni in un file JSON.",
                    fontSize = 13.sp,
                    color = SettingsMuted,
                    lineHeight = 19.sp
                )
                Button(
                    enabled = !backupBusy,
                    onClick = {
                        val name = "ExpenseTracker_backup_${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json"
                        exportLauncher.launch(name)
                    },
                    modifier = Modifier.fillMaxWidth().height(51.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SettingsPurple, contentColor = Color(0xFFFFFFFF))
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Esporta backup", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    enabled = !backupBusy,
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth().height(51.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8C9FE)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SettingsPurple)
                ) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Importa backup", fontWeight = FontWeight.Bold)
                }
                if (backupBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = SettingsPurple
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Operazione in corso...", fontSize = 12.sp, color = SettingsMuted)
                    }
                }
                Text(
                    "Il ripristino sostituisce tutte le spese presenti sul telefono. " +
                            "Il backup include spese, ricorrenze e categorie personalizzate.",
                    fontSize = 12.sp,
                    color = SettingsMuted,
                    lineHeight = 17.sp
                )
            }
        }

    }

    pendingBackup?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (!backupBusy) pendingBackup = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            title = {
                Text(
                    "Conferma ripristino",
                    fontWeight = FontWeight.Bold,
                    color = SettingsInk
                )
            },
            text = {
                Text(
                    "Nel telefono ci sono ${candidate.currentExpenseCount} spese. " +
                            "Il backup ne contiene ${candidate.backup.expenses.size}.\n" +
                            "Categorie personalizzate nel backup: ${candidate.backup.customCategories.size}.\n\n" +
                            "Continuando, tutte le spese attuali saranno eliminate e " +
                            "sostituite da quelle del file. Anche le categorie personalizzate " +
                            "e le ricorrenze attuali verranno sostituite. " +
                            "I vecchi backup potrebbero non contenerle. " +
                            "L'operazione non può essere annullata.",
                    color = SettingsMuted,
                    lineHeight = 21.sp
                )
            },
            confirmButton = {
                Button(
                    enabled = !backupBusy,
                    onClick = {
                        scope.launch {
                            backupBusy = true
                            try {
                                ExpenseBackupManager.replaceAll(context, candidate.backup)
                                pendingBackup = null
                                Toast.makeText(
                                    context,
                                    "Ripristino completato: ${candidate.backup.expenses.size} spese",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Ripristino non riuscito: ${e.message ?: "errore sconosciuto"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                backupBusy = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SettingsPurple, contentColor = Color(0xFFFFFFFF))
                ) {
                    Text("Sostituisci spese")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !backupBusy,
                    onClick = { pendingBackup = null }
                ) {
                    Text("Annulla", color = SettingsPurple)
                }
            }
        )
    }
}

@Composable
private fun NotificationToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFFF0EAFF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = SettingsPurple, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = SettingsInk
            )
            Spacer(Modifier.height(3.dp))
            Text(subtitle, fontSize = 12.sp, color = SettingsMuted, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(6.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SettingsPurple,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFD1D0E0),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
