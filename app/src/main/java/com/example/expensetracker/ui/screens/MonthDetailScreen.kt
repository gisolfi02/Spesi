package com.example.expensetracker.ui.screens

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.data.ExpenseEntity
import com.example.expensetracker.ui.components.CategoryDonutChart
import com.example.expensetracker.ui.components.ExpenseCategoryCatalog
import com.example.expensetracker.ui.components.ExpenseCategoryStyle
import com.example.expensetracker.ui.components.DonutSegment
import com.example.expensetracker.ui.components.WalletIllustration
import com.example.expensetracker.ui.components.ExpenseBreakdownPager
import com.example.expensetracker.ui.components.PaymentMethodInline
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MonthDetailScreen(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onExpenseClick: (ExpenseEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val dao = remember(context) { AppDatabase.getDatabase(context).expenseDao() }
    val categoryFlow = remember(dao) { dao.observeCustomCategories() }
    val customCategories by categoryFlow.collectAsState(initial = emptyList())
    val categoryStyles = ExpenseCategoryCatalog.all(customCategories)
    val scope = rememberCoroutineScope()
    var showMonthPicker by remember { mutableStateOf(false) }
    val startDay = remember(month) { month.atDay(1).toEpochDay() }
    val endDay = remember(month) { month.plusMonths(1).atDay(1).toEpochDay() }
    val previousStartDay = remember(month) { month.minusMonths(1).atDay(1).toEpochDay() }

    val expensesFlow = remember(dao, startDay, endDay) {
        dao.observeMonthlyExpenses(startDay, endDay)
    }
    val expenses by expensesFlow.collectAsState(initial = emptyList())
    val previousTotalFlow = remember(dao, previousStartDay, startDay) {
        dao.observeMonthlyTotal(previousStartDay, startDay)
    }
    val previousTotal by previousTotalFlow.collectAsState(initial = 0L)

    val total = expenses.sumOf { it.amountCents }
    // Il selettore di file Android salva il CSV anche senza connessione Internet.
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: error("Impossibile aprire il file")
                        output.bufferedWriter(Charsets.UTF_8).use { writer ->
                            writer.write("\uFEFF") // UTF-8 BOM: utile per Excel.
                            writer.write("Data;Esercente;Categoria;Importo (EUR);Metodo di pagamento;Nota\n")
                            expenses.forEach { expense ->
                                val amount = BigDecimal.valueOf(expense.amountCents, 2)
                                    .toPlainString().replace('.', ',')
                                val fields = listOf(
                                    LocalDate.ofEpochDay(expense.dateEpochDay).toString(),
                                    expense.merchant,
                                    expense.category,
                                    amount,
                                    expense.paymentMethod,
                                    expense.note
                                )
                                writer.write(fields.joinToString(";") { csvField(it) })
                                writer.write("\n")
                            }
                        }
                    }.isSuccess
                }
                Toast.makeText(context,
                    if (ok) "CSV esportato correttamente" else "Errore durante l'esportazione",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(HistoryBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 17.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        // Intestazione della pagina di dettaglio.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFFF0EEFF)).clickable(onClick = onBackClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Torna allo storico",
                    tint = HistoryInk)
            }
            Spacer(Modifier.width(13.dp))
            Text("Dettaglio mese", modifier = Modifier.weight(1f),
                color = HistoryInk, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                modifier = Modifier.size(45.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF0EEFF)).clickable { showMonthPicker = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = "Seleziona mese",
                    tint = HistoryInk)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                modifier = Modifier.size(39.dp).clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFFF0E9FF))
                    .clickable { onMonthChange(month.minusMonths(1)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Mese precedente",
                    tint = HistoryMuted)
            }
            Box(
                modifier = Modifier.weight(1f).height(40.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFECE7FF)).clickable { showMonthPicker = true },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(historyMonthTitle(month), fontSize = 18.sp,
                        fontWeight = FontWeight.Medium, color = HistoryInk,
                        maxLines = 1)
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = HistoryInk)
                }
            }
            Box(
                modifier = Modifier.size(39.dp).clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFFF0E9FF))
                    .clickable { onMonthChange(month.plusMonths(1)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Mese successivo",
                    tint = HistoryMuted)
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(154.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFF2ECFF), Color(0xFFDCD7FF))))
        ) {
            WalletIllustration(modifier = Modifier.align(Alignment.CenterEnd)
                .size(width = 155.dp, height = 136.dp))
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
                    .padding(start = 17.dp, end = 113.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Totale speso a ${historyMonthTitle(month)}",
                    fontSize = 13.sp, color = HistoryMuted,
                    maxLines = 2, lineHeight = 16.sp)
                Text(historyEuro(total), fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold, color = HistoryInk,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (previousTotal > 0L) {
                    HistoryChangeLabel(total, previousTotal,
                        "vs ${historyMonthShort(month.minusMonths(1))}")
                } else {
                    Text("Nessun confronto precedente", fontSize = 11.sp,
                        color = HistoryMuted, maxLines = 1)
                }
            }
        }

        ExpenseBreakdownPager(
            expenses = expenses,
            categoryStyles = categoryStyles
        )

        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            color = Color.White, shadowElevation = 4.dp) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
                Text("Spese di ${historyMonthTitle(month)}", fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold, color = HistoryInk)
                Spacer(Modifier.height(7.dp))
                if (expenses.isEmpty()) {
                    Text("Non hai ancora registrato spese per questo mese.",
                        modifier = Modifier.padding(vertical = 16.dp),
                        fontSize = 13.sp, color = HistoryMuted)
                } else {
                    expenses.forEachIndexed { index, expense ->
                        DetailExpenseLine(expense, categoryStyles, onClick = { onExpenseClick(expense) })
                        if (index < expenses.lastIndex) {
                            HorizontalDivider(color = Color(0xFFEAEAF5), thickness = 1.dp)
                        }
                    }
                }
            }
        }

        // Pulsante del mockup reso realmente funzionante (esportazione CSV).
        Box(
            modifier = Modifier.fillMaxWidth().height(52.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Color(0xFFF0E9FF))
                .clickable { exportLauncher.launch("spese_${month}.csv") },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FileUpload, contentDescription = null,
                    tint = HistoryPurple, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(9.dp))
                Text("Esporta CSV", color = HistoryPurple,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
    }

    if (showMonthPicker) {
        HistoryDetailMonthPicker(
            selectedMonth = month,
            onDismiss = { showMonthPicker = false },
            onSelect = { newMonth ->
                onMonthChange(newMonth)
                showMonthPicker = false
            }
        )
    }
}

private fun csvField(text: String): String {
    val clean = text.replace("\r", " ").replace("\n", " ")
    // Evita che un testo dell'esercente venga interpretato come formula da Excel.
    val safe = if (listOf("=", "+", "-", "@").any { clean.trimStart().startsWith(it) }) "'" + clean else clean
    return "\"" + safe.replace("\"", "\"\"") + "\""
}

@Composable
private fun DetailCategoryLine(
    style: HistoryCategoryStyle,
    amountCents: Long,
    totalCents: Long
) {
    val percent = if (totalCents > 0L) amountCents.toDouble() / totalCents * 100.0 else 0.0
    val percentage = String.format(Locale.ITALIAN, "%.1f%%", percent)
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(25.dp).clip(RoundedCornerShape(8.dp))
                    .background(style.color), contentAlignment = Alignment.Center
            ) {
                Icon(style.icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(style.name, modifier = Modifier.weight(1f),
                color = HistoryInk, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(historyEuro(amountCents), color = HistoryInk,
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(percentage, modifier = Modifier.align(Alignment.End),
            color = HistoryMuted, fontSize = 10.sp)
    }
}

@Composable
private fun DetailExpenseLine(
    expense: ExpenseEntity,
    categoryStyles: List<ExpenseCategoryStyle>,
    onClick: () -> Unit
) {
    val style = historyStyle(expense.category, categoryStyles)
    Row(
        modifier = Modifier.fillMaxWidth().height(66.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                .background(style.color.copy(alpha = 0.17f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(style.icon, contentDescription = null,
                tint = style.color, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(expense.merchant.ifBlank { "Spesa ${expense.category}" },
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = HistoryInk,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(historyDate(expense.dateEpochDay), fontSize = 10.sp,
                    color = HistoryMuted, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                PaymentMethodInline(expense.paymentMethod, Modifier.weight(1f))
            }
        }
        Box(
            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                .background(style.color.copy(alpha = 0.17f))
                .padding(horizontal = 7.dp, vertical = 4.dp)
        ) {
            Text(expense.category, fontSize = 10.sp, color = style.color,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(7.dp))
        Text("−${historyEuro(expense.amountCents)}", fontSize = 13.sp,
            color = HistoryInk, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun HistoryDetailMonthPicker(
    selectedMonth: YearMonth,
    onDismiss: () -> Unit,
    onSelect: (YearMonth) -> Unit
) {
    var displayedYear by remember(selectedMonth) { mutableIntStateOf(selectedMonth.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleziona mese", color = HistoryInk,
            fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { displayedYear-- }) {
                        Icon(Icons.Filled.ChevronLeft, "Anno precedente", tint = HistoryPurple)
                    }
                    Text("$displayedYear", fontSize = 21.sp,
                        color = HistoryInk, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { displayedYear++ }) {
                        Icon(Icons.Filled.ChevronRight, "Anno successivo", tint = HistoryPurple)
                    }
                }
                (1..12).chunked(3).forEach { months ->
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        months.forEach { number ->
                            val month = YearMonth.of(displayedYear, number)
                            val selected = month == selectedMonth
                            Box(
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) HistoryPurple else Color(0xFFF3F0FE))
                                    .clickable { onSelect(month) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(month.format(DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN))
                                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString() },
                                    fontSize = 13.sp,
                                    color = if (selected) Color.White else HistoryInk,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(YearMonth.now()) }) {
                Text("Mese attuale", color = HistoryPurple)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
        containerColor = Color.White
    )
}
