package com.example.expensetracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.data.ExpenseEntity
import com.example.expensetracker.data.RecurringRule
import com.example.expensetracker.data.RecurringExpenseManager
import com.example.expensetracker.ui.components.CategoryDonutChart
import com.example.expensetracker.ui.components.ExpenseCategoryCatalog
import com.example.expensetracker.ui.components.ExpenseCategoryStyle
import com.example.expensetracker.ui.components.DonutSegment
import com.example.expensetracker.ui.components.WalletIllustration
import com.example.expensetracker.ui.components.ExpenseBreakdownPager
import com.example.expensetracker.ui.components.PaymentMethodInline
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val Ink = Color(0xFF11132B)
private val Muted = Color(0xFF70758F)
private val Purple = Color(0xFF7138EB)
private val Background = Color(0xFFF8F8FF)

private val italian = Locale.ITALIAN

private fun euro(cents: Long): String {
    val formatter = DecimalFormat("#,##0.00", DecimalFormatSymbols(italian))
    return "€${formatter.format(cents / 100.0)}"
}

private fun percent(value: Double): String {
    val formatter = DecimalFormat("0.0", DecimalFormatSymbols(italian))
    return formatter.format(value)
}

private fun capitalizeItalian(text: String): String =
    text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(italian) else it.toString() }

private fun monthName(month: YearMonth): String =
    capitalizeItalian(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", italian)))

private fun shortMonth(month: YearMonth): String =
    capitalizeItalian(month.format(DateTimeFormatter.ofPattern("MMMM", italian)))

private fun expenseDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy", italian))

@Composable
fun HomeScreen() {
    // Manteniamo la navigazione della versione precedente e aggiungiamo il mese selezionabile.
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedMonthKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val selectedMonth = remember(selectedMonthKey) { YearMonth.parse(selectedMonthKey) }
    var selectedHistoryYear by rememberSaveable { mutableIntStateOf(YearMonth.now().year) }
    var historyDetailMonthKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAllExpenses by rememberSaveable { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var selectedExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var isEditingExpense by remember { mutableStateOf(false) }
    var selectedRule by remember { mutableStateOf<RecurringRule?>(null) }

    val context = LocalContext.current
    val dao = remember(context) { AppDatabase.getDatabase(context).expenseDao() }
    val categoryFlow = remember(dao) { dao.observeCustomCategories() }
    val customCategories by categoryFlow.collectAsState(initial = emptyList())
    val categoryStyles = ExpenseCategoryCatalog.all(customCategories)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Sincronizza rate scadute all'apertura e programma il controllo offline giornaliero.
    LaunchedEffect(context) {
        try {
            RecurringExpenseManager.ensurePeriodicCheck(context)
            RecurringExpenseManager.materializeDue(context)
        } catch (error: Exception) {
            snackbarHostState.showSnackbar(
                "Ricorrenze non aggiornate: ${error.message ?: "riprova"}"
            )
        }
    }

    // Carica la frequenza della spesa originale per dettaglio e modifica.
    LaunchedEffect(selectedExpense?.id) {
        selectedRule = selectedExpense?.let { dao.getRecurringRule(it.id) }
    }

    // Query osservabili: Room invia automaticamente una nuova lista quando cambiano i dati.
    val startDay = remember(selectedMonth) { selectedMonth.atDay(1).toEpochDay() }
    val endDay = remember(selectedMonth) { selectedMonth.plusMonths(1).atDay(1).toEpochDay() }
    val previousMonth = remember(selectedMonth) { selectedMonth.minusMonths(1) }
    val previousStartDay = remember(previousMonth) { previousMonth.atDay(1).toEpochDay() }

    val monthlyFlow = remember(dao, startDay, endDay) {
        dao.observeMonthlyExpenses(startDay, endDay)
    }
    val expenses by monthlyFlow.collectAsState(initial = emptyList())

    val previousTotalFlow = remember(dao, previousStartDay, startDay) {
        dao.observeMonthlyTotal(previousStartDay, startDay)
    }
    val previousTotal by previousTotalFlow.collectAsState(initial = 0L)

    BackHandler(enabled = showSettings) { showSettings = false }

    Scaffold(
        containerColor = Background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (!showSettings) BottomTabs(selected = selectedTab, onSelect = { nextTab ->
                selectedExpense = null
                isEditingExpense = false
                // Toccare di nuovo Storico torna all'elenco annuale.
                if (nextTab != 2 || selectedTab == 2) historyDetailMonthKey = null
                selectedTab = nextTab
            })
        }
    ) { contentPadding ->
        val activeExpense = selectedExpense
        if (showSettings) {
            SettingsScreen(
                modifier = Modifier.padding(contentPadding),
                onBackClick = { showSettings = false }
            )
        } else if (activeExpense != null) {
            if (isEditingExpense) {
                AddExpenseScreen(
                    modifier = Modifier.padding(contentPadding),
                    existingExpense = activeExpense,
                    existingRecurrence = selectedRule,
                    isSaving = isSaving,
                    onBackClick = { isEditingExpense = false },
                    onSave = { updated, interval, expiryMonths, expiryDay ->
                        if (!isSaving) {
                            isSaving = true
                            scope.launch {
                                try {
                                    val stored = RecurringExpenseManager.saveExpense(
                                        context, updated, interval, expiryMonths, expiryDay
                                    )
                                    selectedExpense = stored
                                    selectedRule = dao.getRecurringRule(stored.id)
                                    val destinationMonth = YearMonth.from(
                                        LocalDate.ofEpochDay(updated.dateEpochDay)
                                    )
                                    if (selectedTab == 0) {
                                        selectedMonthKey = destinationMonth.toString()
                                    } else if (selectedTab == 2) {
                                        historyDetailMonthKey = destinationMonth.toString()
                                        selectedHistoryYear = destinationMonth.year
                                    }
                                    isEditingExpense = false
                                    snackbarHostState.showSnackbar("Spesa modificata con successo")
                                } catch (error: Exception) {
                                    snackbarHostState.showSnackbar(
                                        "Modifica non riuscita: ${error.message ?: "riprova"}"
                                    )
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    }
                )
            } else {
                ExpenseDetailScreen(
                    expense = activeExpense,
                    modifier = Modifier.padding(contentPadding),
                    isDeleting = isSaving,
                    recurrenceDescription = selectedRule?.let { rule ->
                        val end = rule.expiryDate()
                        if (end == null) rule.interval().label
                        else rule.interval().label + " · scade il " +
                                end.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN))
                    },
                    onBackClick = { selectedExpense = null },
                    onEditClick = {
                        scope.launch {
                            selectedRule = dao.getRecurringRule(activeExpense.id)
                            isEditingExpense = true
                        }
                    },
                    onDeleteConfirmed = {
                        if (!isSaving) {
                            isSaving = true
                            scope.launch {
                                try {
                                    RecurringExpenseManager.deleteExpense(context, activeExpense)
                                    selectedExpense = null
                                    snackbarHostState.showSnackbar("Spesa eliminata")
                                } catch (error: Exception) {
                                    snackbarHostState.showSnackbar(
                                        "Eliminazione non riuscita: ${error.message ?: "riprova"}"
                                    )
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    }
                )
            }
        } else when (selectedTab) {
            0 -> HomeContent(
                modifier = Modifier.padding(contentPadding),
                month = selectedMonth,
                expenses = expenses,
                previousTotalCents = previousTotal,
                categoryStyles = categoryStyles,
                showAllExpenses = showAllExpenses,
                onToggleAllExpenses = { showAllExpenses = !showAllExpenses },
                onNotificationClick = { showSettings = true },
                onMonthClick = { showMonthPicker = true },
                onExpenseClick = { selectedExpense = it; isEditingExpense = false }
            )
            1 -> AddExpenseScreen(
                modifier = Modifier.padding(contentPadding),
                isSaving = isSaving,
                onBackClick = { selectedTab = 0 },
                onSave = { expense, interval, expiryMonths, expiryDay ->
                    if (!isSaving) {
                        isSaving = true
                        scope.launch {
                            try {
                                RecurringExpenseManager.saveExpense(
                                    context, expense, interval, expiryMonths, expiryDay
                                )
                                // Se la spesa e' di un altro mese, apriamo proprio quel mese.
                                selectedMonthKey = YearMonth.from(
                                    LocalDate.ofEpochDay(expense.dateEpochDay)
                                ).toString()
                                showAllExpenses = false
                                selectedTab = 0
                                snackbarHostState.showSnackbar("Spesa salvata con successo")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(
                                    "Errore nel salvataggio: ${e.message ?: "riprova"}"
                                )
                            } finally {
                                isSaving = false
                            }
                        }
                    }
                }
            )
            2 -> {
                val detailMonth = historyDetailMonthKey?.let { YearMonth.parse(it) }
                if (detailMonth == null) {
                    HistoryScreen(
                        year = selectedHistoryYear,
                        onYearChange = { selectedHistoryYear = it },
                        onMonthClick = { historyDetailMonthKey = it.toString() },
                        onNotificationClick = { showSettings = true },
                        modifier = Modifier.padding(contentPadding)
                    )
                } else {
                    MonthDetailScreen(
                        month = detailMonth,
                        onMonthChange = { historyDetailMonthKey = it.toString() },
                        onBackClick = { historyDetailMonthKey = null },
                        onExpenseClick = { selectedExpense = it; isEditingExpense = false },
                        modifier = Modifier.padding(contentPadding)
                    )
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            selectedMonth = selectedMonth,
            onDismiss = { showMonthPicker = false },
            onMonthSelected = { newMonth ->
                selectedMonthKey = newMonth.toString()
                showAllExpenses = false
                showMonthPicker = false
            }
        )
    }
}

@Composable
private fun HomeContent(
    modifier: Modifier,
    month: YearMonth,
    expenses: List<ExpenseEntity>,
    previousTotalCents: Long,
    categoryStyles: List<ExpenseCategoryStyle>,
    showAllExpenses: Boolean,
    onToggleAllExpenses: () -> Unit,
    onNotificationClick: () -> Unit,
    onMonthClick: () -> Unit,
    onExpenseClick: (ExpenseEntity) -> Unit
) {
    // Totale e categorie si ricavano esclusivamente dalle spese salvate nel database.
    val totalCents = expenses.sumOf { it.amountCents }
    val visibleExpenses = if (showAllExpenses) expenses else expenses.take(4)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF4EC5FA), Purple))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AccountBalanceWallet, null,
                    tint = Color.White, modifier = Modifier.size(31.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Spese", fontSize = 29.sp, fontWeight = FontWeight.ExtraBold,
                    color = Ink, lineHeight = 33.sp
                )
                Row(
                    modifier = Modifier.clickable(onClick = onMonthClick),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(monthName(month), fontSize = 16.sp, color = Muted)
                    Icon(
                        Icons.Filled.KeyboardArrowDown, null,
                        tint = Muted, modifier = Modifier.size(21.dp)
                    )
                }
            }
            Box(
                modifier = Modifier.size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF0EEFF))
                    .clickable(onClick = onNotificationClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.NotificationsNone,
                    contentDescription = "Impostazioni notifiche",
                    tint = Muted, modifier = Modifier.size(27.dp)
                )
                // Il puntino rosso verra' attivato solo per notifiche realmente presenti.
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(157.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFFF2ECFF), Color(0xFFDCD7FF))
                    )
                )
        ) {
            WalletIllustration(
                modifier = Modifier.align(Alignment.CenterEnd)
                    .size(width = 158.dp, height = 135.dp)
            )
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
                    .padding(start = 17.dp, end = 115.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Totale speso questo mese",
                    fontSize = 14.sp, color = Muted, lineHeight = 17.sp
                )
                Text(
                    euro(totalCents),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (previousTotalCents > 0L) {
                    val change = (totalCents - previousTotalCents).toDouble() /
                            previousTotalCents.toDouble() * 100.0
                    val increase = change > 0.0
                    val color = when {
                        increase -> Color(0xFFBE2338)
                        change < 0.0 -> Color(0xFF138B39)
                        else -> Muted
                    }
                    val pillBackground = when {
                        increase -> Color(0xFFFFDDE3)
                        change < 0.0 -> Color(0xFFD4F4DF)
                        else -> Color(0xFFECECF2)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(24.dp))
                                .background(pillBackground)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${if (increase) "↑" else if (change < 0.0) "↓" else "="} ${percent(abs(change))}%",
                                color = color,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "vs ${shortMonth(month.minusMonths(1))}",
                            fontSize = 11.sp, color = Muted, maxLines = 1
                        )
                    }
                } else {
                    Text(
                        "Nessun confronto precedente",
                        fontSize = 11.sp, color = Muted, maxLines = 1
                    )
                }
            }
        }

        ExpenseBreakdownPager(
            expenses = expenses,
            categoryStyles = categoryStyles
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White, shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showAllExpenses) "Tutte le spese" else "Ultime spese",
                        modifier = Modifier.weight(1f),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Ink
                    )
                    if (expenses.size > 4) {
                        TextButton(
                            onClick = onToggleAllExpenses,
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text(
                                if (showAllExpenses) "Mostra meno" else "Vedi tutto",
                                color = Purple,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                if (showAllExpenses) Icons.Filled.ExpandLess else Icons.Filled.ChevronRight,
                                null, tint = Purple, modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
                if (expenses.isEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Non hai ancora registrato spese per questo mese.",
                        color = Muted,
                        fontSize = 13.sp
                    )
                } else {
                    visibleExpenses.forEachIndexed { index, expense ->
                        ExpenseLine(expense, categoryStyles, onClick = { onExpenseClick(expense) })
                        if (index != visibleExpenses.lastIndex) {
                            HorizontalDivider(
                                color = Color(0xFFEAEAF5), thickness = 1.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryLine(style: ExpenseCategoryStyle, amountCents: Long, totalCents: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().height(29.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(23.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(style.color),
            contentAlignment = Alignment.Center
        ) {
            Icon(style.icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(5.dp))
        Text(
            style.name, modifier = Modifier.weight(1f),
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(3.dp))
        Text(
            euro(amountCents),
            fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = Ink, maxLines = 1
        )
        Spacer(Modifier.width(3.dp))
        val percentage = if (totalCents > 0L) {
            (amountCents.toDouble() / totalCents * 100.0).roundToInt()
        } else 0
        Text("$percentage%", fontSize = 9.sp, color = Muted, maxLines = 1)
    }
}

@Composable
private fun ExpenseLine(
    expense: ExpenseEntity,
    categoryStyles: List<ExpenseCategoryStyle>,
    onClick: () -> Unit
) {
    val style = categoryStyles.firstOrNull { it.name == expense.category }
        ?: ExpenseCategoryStyle(expense.category, Purple, Icons.Filled.Receipt)
    val title = expense.merchant.ifBlank { "Spesa ${expense.category}" }

    Row(
        modifier = Modifier.fillMaxWidth().height(69.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(43.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(style.color.copy(alpha = 0.17f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(style.icon, null, tint = style.color, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title, color = Ink, fontWeight = FontWeight.Bold,
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(expenseDate(expense.dateEpochDay), color = Muted,
                    fontSize = 10.sp, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                PaymentMethodInline(expense.paymentMethod, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                .background(style.color.copy(alpha = 0.15f))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                expense.category, color = style.color,
                fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            "−${euro(expense.amountCents)}",
            color = Ink, fontWeight = FontWeight.Bold,
            fontSize = 13.sp, maxLines = 1
        )
    }
}

@Composable
private fun MonthPickerDialog(
    selectedMonth: YearMonth,
    onDismiss: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit
) {
    var displayedYear by remember(selectedMonth) { mutableIntStateOf(selectedMonth.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleziona mese", fontWeight = FontWeight.Bold, color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { displayedYear-- }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Anno precedente", tint = Purple)
                    }
                    Text("$displayedYear", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                    IconButton(onClick = { displayedYear++ }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Anno successivo", tint = Purple)
                    }
                }
                (1..12).chunked(3).forEach { months ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        months.forEach { monthNumber ->
                            val ym = YearMonth.of(displayedYear, monthNumber)
                            val isSelected = ym == selectedMonth
                            Box(
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) Purple else Color(0xFFF3F0FE)
                                    )
                                    .clickable { onMonthSelected(ym) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    capitalizeItalian(
                                        ym.format(DateTimeFormatter.ofPattern("MMM", italian))
                                    ),
                                    color = if (isSelected) Color.White else Ink,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onMonthSelected(YearMonth.now())
            }) {
                Text("Mese attuale", color = Purple)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Chiudi", color = Muted) }
        },
        containerColor = Color.White
    )
}

@Composable
private fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    Surface(color = Color.White, shadowElevation = 10.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BottomTab("Home", Icons.Filled.Home, 0, selected, onSelect, Modifier.weight(1f))
            BottomTab("Aggiungi", Icons.Filled.AddCircle, 1, selected, onSelect, Modifier.weight(1f))
            BottomTab("Storico", Icons.Filled.BarChart, 2, selected, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BottomTab(
    label: String,
    icon: ImageVector,
    index: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = selected == index
    Column(
        modifier = modifier.fillMaxHeight().clip(RoundedCornerShape(20.dp))
            .background(if (active) Color(0xFFEEE7FF) else Color.Transparent)
            .selectable(selected = active, onClick = { onSelect(index) }, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (active) Purple else Muted,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label, fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Purple else Muted
        )
    }
}

@Composable
private fun ComingSoon(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.AccountBalanceWallet,
            null, tint = Purple, modifier = Modifier.size(58.dp)
        )
        Spacer(Modifier.height(15.dp))
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Muted)
    }
}
