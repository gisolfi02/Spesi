package com.example.expensetracker.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.data.ExpenseEntity
import com.example.expensetracker.ui.components.CalendarIllustration
import com.example.expensetracker.ui.components.ExpenseCategoryCatalog
import com.example.expensetracker.ui.components.ExpenseCategoryStyle
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Utilita' condivise con MonthDetailScreen.kt.
internal val HistoryInk = Color(0xFF11132B)
internal val HistoryMuted = Color(0xFF70758F)
internal val HistoryPurple = Color(0xFF7138EB)
internal val HistoryBackground = Color(0xFFF8F8FF)

internal typealias HistoryCategoryStyle = ExpenseCategoryStyle

internal val HistoryCategoryStyles = ExpenseCategoryCatalog.defaults

internal fun historyStyle(
    name: String,
    styles: List<ExpenseCategoryStyle> = HistoryCategoryStyles
): ExpenseCategoryStyle = styles.firstOrNull { it.name == name }
    ?: ExpenseCategoryStyle(name, HistoryPurple, Icons.Filled.Receipt)

internal fun historyEuro(cents: Long): String =
    "€" + DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.ITALIAN))
        .format(BigDecimal.valueOf(cents, 2))

internal fun historyMonthTitle(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString() }

internal fun historyMonthShort(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("MMMM", Locale.ITALIAN))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString() }

internal fun historyDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN))

internal fun historyMonthlyTotals(expenses: List<ExpenseEntity>): Map<String, Long> =
    expenses.groupBy { it.category }
        .mapValues { (_, rows) -> rows.sumOf { it.amountCents } }
        .filterValues { it > 0L }

@Composable
fun HistoryScreen(
    year: Int,
    onYearChange: (Int) -> Unit,
    onMonthClick: (YearMonth) -> Unit,
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dao = remember(context) { AppDatabase.getDatabase(context).expenseDao() }
    val categoryFlow = remember(dao) { dao.observeCustomCategories() }
    val customCategories by categoryFlow.collectAsState(initial = emptyList())
    val categoryStyles = ExpenseCategoryCatalog.all(customCategories)
    var showYearPicker by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var onlyWithExpenses by rememberSaveable { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    // Una sola query copre l'anno selezionato, il precedente e dicembre
    // dell'anno precedente (necessario per il confronto con gennaio).
    val startDay = remember(year) { YearMonth.of(year - 1, 1).atDay(1).toEpochDay() }
    val endDay = remember(year) { YearMonth.of(year + 1, 1).atDay(1).toEpochDay() }
    val yearsFlow = remember(dao, startDay, endDay) {
        dao.observeMonthlyExpenses(startDay, endDay)
    }
    val fetchedExpenses by yearsFlow.collectAsState(initial = emptyList())
    val groupedMonths = remember(fetchedExpenses) {
        fetchedExpenses.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) }
    }

    val yearExpenses = remember(groupedMonths, year) {
        (1..12).flatMap { groupedMonths[YearMonth.of(year, it)].orEmpty() }
    }
    val priorYearExpenses = remember(groupedMonths, year) {
        (1..12).flatMap { groupedMonths[YearMonth.of(year - 1, it)].orEmpty() }
    }
    val yearTotal = yearExpenses.sumOf { it.amountCents }
    val now = YearMonth.now()
    val averageMonths = if (year == now.year) now.monthValue else 12
    // Per l'anno in corso il confronto e' gennaio-oggi contro gli stessi
    // mesi dell'anno precedente, anziche' contro un anno intero.
    val previousYearComparableTotal = priorYearExpenses
        .filter { LocalDate.ofEpochDay(it.dateEpochDay).monthValue <= averageMonths }
        .sumOf { it.amountCents }
    val averageCents = if (averageMonths > 0) yearTotal / averageMonths else 0L

    val monthList = (12 downTo 1)
        .map { YearMonth.of(year, it) }
        .filter { month ->
            // Nel corrente anno non mostriamo i mesi futuri ancora vuoti.
            month <= now || groupedMonths[month].orEmpty().isNotEmpty() || year != now.year
        }
        .filter { month ->
            val matchesName = historyMonthTitle(month)
                .contains(search.trim(), ignoreCase = true)
            val matchesExpenseFilter = !onlyWithExpenses || !groupedMonths[month].isNullOrEmpty()
            matchesName && matchesExpenseFilter
        }

    Column(
        modifier = modifier.fillMaxSize().background(HistoryBackground)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF4EC5FA), HistoryPurple))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AccountBalanceWallet, null, tint = Color.White,
                    modifier = Modifier.size(31.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Storico spese", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold,
                    color = HistoryInk, lineHeight = 31.sp)
                Row(
                    modifier = Modifier.clickable { showYearPicker = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(year.toString(), color = HistoryMuted, fontSize = 16.sp)
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = HistoryMuted,
                        modifier = Modifier.size(21.dp))
                }
            }
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF0EEFF)).clickable(onClick = onNotificationClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.NotificationsNone, contentDescription = "Notifiche",
                    tint = HistoryMuted, modifier = Modifier.size(27.dp))
            }
        }

        // Riepilogo annuale, con illustrazione vettoriale locale.
        Box(
            modifier = Modifier.fillMaxWidth().height(159.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFF2ECFF), Color(0xFFDCD7FF))))
        ) {
            CalendarIllustration(
                modifier = Modifier.align(Alignment.CenterEnd)
                    .size(width = 155.dp, height = 140.dp)
            )
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
                    .padding(start = 17.dp, end = 110.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("Totale anno $year", color = HistoryMuted, fontSize = 14.sp)
                Text(historyEuro(yearTotal), fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold, color = HistoryInk,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (previousYearComparableTotal > 0) {
                    HistoryChangeLabel(yearTotal, previousYearComparableTotal, "vs ${year - 1}")
                } else {
                    Text("Nessun confronto precedente", fontSize = 11.sp,
                        color = HistoryMuted, maxLines = 1)
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(43.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF0E9FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.BarChart, null, tint = HistoryPurple,
                        modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Spesa media mensile", color = HistoryInk,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(historyEuro(averageCents), color = HistoryMuted, fontSize = 15.sp)
                }
                if (previousYearComparableTotal > 0) {
                    val priorAverage = previousYearComparableTotal / averageMonths.coerceAtLeast(1)
                    HistoryChangeLabel(averageCents, priorAverage, null)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null,
                    tint = HistoryMuted) },
                placeholder = { Text("Cerca mese...", color = HistoryMuted, fontSize = 14.sp) },
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = HistoryPurple,
                    unfocusedBorderColor = Color(0xFFEAE8F5)
                )
            )
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (onlyWithExpenses) HistoryPurple else Color(0xFFF0E9FF))
                        .clickable { showFilters = true }
                        .padding(horizontal = 13.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val buttonTint = if (onlyWithExpenses) Color.White else HistoryPurple
                    Icon(
                        Icons.Filled.FilterList,
                        contentDescription = null,
                        tint = buttonTint,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Filtra",
                        color = buttonTint,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Menu personalizzato: stesse tonalita' e forme delle card dell'app.
                DropdownMenu(
                    expanded = showFilters,
                    onDismissRequest = { showFilters = false },
                    modifier = Modifier.width(282.dp),
                    shape = RoundedCornerShape(22.dp),
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    shadowElevation = 5.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "VISUALIZZA",
                            color = HistoryMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 9.dp, bottom = 1.dp)
                        )
                        HistoryFilterOption(
                            title = "Tutti i mesi",
                            subtitle = "Anche quelli senza spese",
                            icon = Icons.Filled.DateRange,
                            selected = !onlyWithExpenses,
                            onClick = {
                                onlyWithExpenses = false
                                showFilters = false
                            }
                        )
                        HistoryFilterOption(
                            title = "Solo mesi con spese",
                            subtitle = "Nascondi i mesi vuoti",
                            icon = Icons.Filled.ReceiptLong,
                            selected = onlyWithExpenses,
                            onClick = {
                                onlyWithExpenses = true
                                showFilters = false
                            }
                        )
                    }
                }
            }
        }

        Text("Mesi del $year", fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold, color = HistoryInk)

        if (monthList.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                color = Color.White, shadowElevation = 2.dp
            ) {
                Text("Nessun mese corrisponde alla ricerca.",
                    modifier = Modifier.padding(23.dp), color = HistoryMuted)
            }
        }

        monthList.forEach { month ->
            val monthExpenses = groupedMonths[month].orEmpty()
            val previousTotal = groupedMonths[month.minusMonths(1)].orEmpty()
                .sumOf { it.amountCents }
            HistoryMonthCard(
                month = month,
                expenses = monthExpenses,
                previousTotal = previousTotal,
                categoryStyles = categoryStyles,
                onClick = { onMonthClick(month) }
            )
        }
    }

    if (showYearPicker) {
        HistoryYearPicker(
            year = year,
            onDismiss = { showYearPicker = false },
            onConfirm = { newYear ->
                onYearChange(newYear)
                showYearPicker = false
            }
        )
    }
}

@Composable
internal fun HistoryChangeLabel(
    total: Long,
    previous: Long,
    annotation: String?,
    compact: Boolean = false
) {
    if (previous <= 0L) return
    val change = (total - previous).toDouble() / previous.toDouble() * 100.0
    val goingUp = change > 0
    val tint = when {
        goingUp -> Color(0xFFBE2338)
        change < 0 -> Color(0xFF138B39)
        else -> HistoryMuted
    }
    val background = when {
        goingUp -> Color(0xFFFFDDE3)
        change < 0 -> Color(0xFFD4F4DF)
        else -> Color(0xFFEFEFF5)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                .background(background)
                .padding(horizontal = if (compact) 6.dp else 8.dp,
                    vertical = if (compact) 3.dp else 4.dp)
        ) {
            val num = DecimalFormat("0.0", DecimalFormatSymbols(Locale.ITALIAN))
                .format(abs(change))
            Text("${if (goingUp) "↑" else if (change < 0) "↓" else "="} $num%",
                color = tint, fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Bold, maxLines = 1)
        }
        if (annotation != null) {
            Spacer(Modifier.width(5.dp))
            Text(annotation, color = HistoryMuted,
                fontSize = if (compact) 9.sp else 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun HistoryMonthCard(
    month: YearMonth,
    expenses: List<ExpenseEntity>,
    previousTotal: Long,
    categoryStyles: List<ExpenseCategoryStyle>,
    onClick: () -> Unit
) {
    val total = expenses.sumOf { it.amountCents }
    val totals = historyMonthlyTotals(expenses)
    val mainCategory = totals.maxByOrNull { it.value }
    val dominantPercentage = if (mainCategory != null && total > 0) {
        (mainCategory.value.toDouble() / total * 100).roundToInt()
    } else 0
    val monthColors = listOf(
        Color(0xFFF0E9FF), Color(0xFFE4F2FF), Color(0xFFE3F8EA), Color(0xFFFFF4D8)
    )

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(23.dp), color = Color.White, shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(35.dp).clip(RoundedCornerShape(12.dp))
                        .background(monthColors[(month.monthValue - 1) % monthColors.size]),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.DateRange, null, tint = HistoryPurple,
                        modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(historyMonthTitle(month), fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp, color = HistoryInk,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${expenses.size} ${if (expenses.size == 1) "transazione" else "transazioni"}",
                        color = HistoryMuted, fontSize = 12.sp)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = "Apri dettaglio mese",
                    tint = HistoryMuted, modifier = Modifier.size(22.dp))
            }

            Spacer(Modifier.height(9.dp))
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(historyEuro(total), fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold, color = HistoryInk,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    when {
                        previousTotal > 0L -> HistoryChangeLabel(
                            total, previousTotal, "vs ${historyMonthShort(month.minusMonths(1))}",
                            compact = true
                        )
                        total > 0L -> Text("Primo mese con spese", color = HistoryMuted,
                            fontSize = 10.sp)
                        else -> Text("Nessuna spesa", color = HistoryMuted, fontSize = 10.sp)
                    }
                }
                SmallMonthDonut(
                    totals = totals, total = total,
                    mainPercentage = dominantPercentage,
                    mainCategory = mainCategory?.key ?: "",
                    categoryStyles = categoryStyles,
                    modifier = Modifier.size(89.dp)
                )
                Column(
                    modifier = Modifier.width(89.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    val top = totals.entries.sortedByDescending { it.value }.take(3)
                    if (top.isEmpty()) {
                        Text("Nessuna categoria", fontSize = 10.sp, color = HistoryMuted)
                    } else top.forEach { (name, value) ->
                        val style = historyStyle(name, categoryStyles)
                        val ratio = if (total > 0) value.toFloat() / total else 0f
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(15.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(style.color),
                                contentAlignment = Alignment.Center) {
                                Icon(style.icon, null, tint = Color.White,
                                    modifier = Modifier.size(10.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Box(modifier = Modifier.weight(1f).height(7.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFFE8E7F3))) {
                                Box(modifier = Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f))
                                    .fillMaxHeight().clip(RoundedCornerShape(5.dp))
                                    .background(style.color))
                            }
                            Spacer(Modifier.width(3.dp))
                            Text("${(ratio * 100).roundToInt()}%", fontSize = 10.sp,
                                color = HistoryMuted, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallMonthDonut(
    totals: Map<String, Long>,
    total: Long,
    mainPercentage: Int,
    mainCategory: String,
    categoryStyles: List<ExpenseCategoryStyle>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = 13.dp.toPx()
            val arcSize = Size(size.width - width, size.height - width)
            val topLeft = Offset(width / 2f, width / 2f)
            drawArc(Color(0xFFF0EDF9), -90f, 360f, false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = width))
            if (total > 0L) {
                var start = -90f
                totals.keys.forEach { name ->
                    val style = historyStyle(name, categoryStyles)
                    val count = totals[style.name] ?: 0L
                    if (count > 0L) {
                        val sweep = count.toFloat() / total.toFloat() * 360f
                        drawArc(style.color, start, sweep, false,
                            topLeft = topLeft, size = arcSize, style = Stroke(width = width))
                        start += sweep
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$mainPercentage%", color = HistoryInk,
                fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Text(if (total > 0L) mainCategory else "Vuoto", color = HistoryMuted,
                fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HistoryYearPicker(
    year: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var displayYear by remember(year) { mutableIntStateOf(year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleziona anno", color = HistoryInk,
            fontWeight = FontWeight.Bold) },
        text = {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { displayYear-- }) {
                    Icon(Icons.Filled.ChevronLeft,
                        contentDescription = "Anno precedente", tint = HistoryPurple)
                }
                Text(displayYear.toString(), fontSize = 25.sp,
                    color = HistoryInk, fontWeight = FontWeight.ExtraBold)
                IconButton(onClick = { displayYear++ }) {
                    Icon(Icons.Filled.ChevronRight,
                        contentDescription = "Anno successivo", tint = HistoryPurple)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(displayYear) }) {
                Text("Seleziona", color = HistoryPurple)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
        containerColor = Color.White
    )
}

// Riga del menu Filtra, coerente con le card e i colori di ExpenseTracker.
@Composable
private fun HistoryFilterOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) Color(0xFFF0E9FF) else Color(0xFFF8F8FF)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(39.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) HistoryPurple else Color(0xFFEDE8FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else HistoryPurple,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = HistoryInk,
                maxLines = 1
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = HistoryMuted,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle
            else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (selected) "Selezionato" else null,
            tint = if (selected) HistoryPurple else Color(0xFFCAC5DE),
            modifier = Modifier.size(20.dp)
        )
    }
}
