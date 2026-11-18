package com.example.expensetracker.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.ExpenseEntity
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.data.CustomCategory
import androidx.room3.withWriteTransaction
import com.example.expensetracker.ui.components.ExpenseCategoryCatalog
import com.example.expensetracker.ui.components.ExpenseCategoryStyle
import kotlinx.coroutines.launch
import com.example.expensetracker.data.RecurringRule
import com.example.expensetracker.data.RecurrenceInterval
import com.example.expensetracker.ui.components.WalletIllustration
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ink = Color(0xFF11132B)
private val Muted = Color(0xFF70758F)
private val Purple = Color(0xFF7138EB)
private val Background = Color(0xFFF8F8FF)

private data class CategoryDeletionRequest(
    val category: CustomCategory,
    val linkedExpenses: Int
)

// Metodi di pagamento: colori e icone coerenti con lo stile dell'app.
private data class PaymentMethodOption(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

private val paymentMethods = listOf(
    PaymentMethodOption(
        "Carta di credito", "Visa, Mastercard e altre carte di credito",
        Icons.Filled.CreditCard, Color(0xFF3495FF)
    ),
    PaymentMethodOption(
        "Carta di debito", "Bancomat e carte di debito",
        Icons.Filled.Payment, Color(0xFF8655EB)
    ),
    PaymentMethodOption(
        "Contanti", "Banconote e monete",
        Icons.Filled.Payments, Color(0xFF32BC74)
    ),
    PaymentMethodOption(
        "Wallet digitale", "Google Wallet, Samsung Wallet e simili",
        Icons.Filled.PhoneAndroid, Color(0xFF20BDC9)
    ),
    PaymentMethodOption(
        "Bonifico", "Trasferimento bancario",
        Icons.Filled.AccountBalance, Color(0xFF5D78C9)
    ),
    PaymentMethodOption(
        "Altro", "Altri metodi di pagamento",
        Icons.Filled.MoreHoriz, Color(0xFFFFB51B)
    )
)

private fun parseAmountCents(text: String): Long? {
    return try {
        BigDecimal(text.trim().replace(',', '.'))
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
            .takeIf { it > 0 }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun AddExpenseScreen(
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    existingExpense: ExpenseEntity? = null,
    existingRecurrence: RecurringRule? = null,
    onBackClick: () -> Unit,
    onSave: (ExpenseEntity, RecurrenceInterval?, Int?, Long?) -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { AppDatabase.getDatabase(context) }
    val categoryDao = remember(database) { database.expenseDao() }
    val categoryFlow = remember(categoryDao) { categoryDao.observeCustomCategories() }
    val customCategories by categoryFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showCreateCategorySheet by remember { mutableStateOf(false) }
    var showManageCategoriesSheet by remember { mutableStateOf(false) }
    var manageCategoryError by remember { mutableStateOf<String?>(null) }
    var pendingDeletion by remember { mutableStateOf<CategoryDeletionRequest?>(null) }
    var isDeletingCategory by remember { mutableStateOf(false) }
    var deletionError by remember { mutableStateOf<String?>(null) }
    var savingCategory by remember { mutableStateOf(false) }
    var createCategoryError by remember { mutableStateOf<String?>(null) }
    // Le chiavi ripristinano i campi giusti quando si passa da una spesa all'altra.
    var amount by rememberSaveable(existingExpense?.id) {
        mutableStateOf(
            existingExpense?.let {
                BigDecimal.valueOf(it.amountCents, 2).toPlainString().replace('.', ',')
            } ?: ""
        )
    }
    var selectedCategory by rememberSaveable(existingExpense?.id) {
        mutableStateOf(existingExpense?.category ?: "Cibo")
    }
    var selectedDay by rememberSaveable(existingExpense?.id) {
        mutableLongStateOf(existingExpense?.dateEpochDay ?: LocalDate.now().toEpochDay())
    }
    var paymentMethod by rememberSaveable(existingExpense?.id) {
        mutableStateOf(existingExpense?.paymentMethod ?: "Carta di credito")
    }
    var merchant by rememberSaveable(existingExpense?.id) {
        mutableStateOf(existingExpense?.merchant ?: "")
    }
    var note by rememberSaveable(existingExpense?.id) {
        mutableStateOf(existingExpense?.note ?: "")
    }
    var recurringEnabled by rememberSaveable(existingExpense?.id) {
        mutableStateOf(existingRecurrence != null)
    }
    var recurrenceCode by rememberSaveable(existingExpense?.id) {
        mutableStateOf(existingRecurrence?.intervalCode ?: RecurrenceInterval.MONTHLY.name)
    }
    // NONE = nessuna scadenza; MONTHS = durata rispetto alla data iniziale;
    // DATE = giorno finale fisso (incluso).
    var expiryMode by rememberSaveable(existingExpense?.id) {
        mutableStateOf(when {
            existingRecurrence?.expiryAfterMonths != null -> "MONTHS"
            existingRecurrence?.endDateEpochDay != null -> "DATE"
            else -> "NONE"
        })
    }
    var expiryMonths by rememberSaveable(existingExpense?.id) {
        mutableIntStateOf(existingRecurrence?.expiryAfterMonths ?: 2)
    }
    var expiryAbsoluteDay by rememberSaveable(existingExpense?.id) {
        mutableLongStateOf(existingRecurrence?.endDateEpochDay
            ?: LocalDate.ofEpochDay(existingExpense?.dateEpochDay
                ?: LocalDate.now().toEpochDay()).plusMonths(2).toEpochDay())
    }
    var showExpirySheet by rememberSaveable { mutableStateOf(false) }
    var showRecurrenceSheet by rememberSaveable { mutableStateOf(false) }
    var showPaymentSheet by rememberSaveable { mutableStateOf(false) }
    val selectedPayment = paymentMethods.firstOrNull { it.label == paymentMethod }
        ?: paymentMethods.first()
    val categories = ExpenseCategoryCatalog.selectable(customCategories).let { known ->
        // In modifica lasciamo visibile la categoria storica anche se archiviata.
        if (existingExpense != null && known.none { it.name == selectedCategory }) {
            known + ExpenseCategoryCatalog.style(selectedCategory, customCategories)
        } else known
    }

    val date = LocalDate.ofEpochDay(selectedDay)
    val dateText = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN))
    val amountCents = parseAmountCents(amount)
    val effectiveEndDate = when (expiryMode) {
        "MONTHS" -> date.plusMonths(expiryMonths.toLong())
        "DATE" -> LocalDate.ofEpochDay(expiryAbsoluteDay)
        else -> null
    }
    val expiryIsValid = !recurringEnabled || effectiveEndDate == null ||
            (!effectiveEndDate.isBefore(date) && !effectiveEndDate.isAfter(date.plusYears(100)))
    val expiryText = when (expiryMode) {
        "MONTHS" -> "Dopo $expiryMonths ${if (expiryMonths == 1) "mese" else "mesi"} · " +
                effectiveEndDate!!.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN))
        "DATE" -> "Fino al " +
                effectiveEndDate!!.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN))
        else -> "Senza scadenza"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFFF0EEFF))
                    .clickable(onClick = onBackClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro", tint = Ink)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (existingExpense == null) "Nuova spesa" else "Modifica spesa",
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Ink
            )
        }

        // Importo: sfondo lilla e illustrazione nello stesso stile della Home.
        Box(
            modifier = Modifier.fillMaxWidth().height(147.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFF2ECFF), Color(0xFFDCD7FF))))
        ) {
            WalletIllustration(
                modifier = Modifier.align(Alignment.CenterEnd).size(width = 145.dp, height = 130.dp)
            )
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
                    .padding(start = 17.dp, end = 128.dp)
            ) {
                Text("Importo", fontSize = 15.sp, color = Muted)
                Spacer(Modifier.height(15.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("€", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                    Spacer(Modifier.width(4.dp))
                    BasicTextField(
                        value = amount,
                        onValueChange = { input ->
                            if (input.length <= 12 && input.matches(Regex("\\d{0,9}([,.]\\d{0,2})?"))) {
                                amount = input
                            }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Ink, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold
                        ),
                        cursorBrush = SolidColor(Purple),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        decorationBox = { inner ->
                            Box {
                                if (amount.isEmpty()) {
                                    Text("0,00", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                                        color = Ink.copy(alpha = 0.38f))
                                }
                                inner()
                            }
                        }
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp), color = Color.White,
            shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Categoria", fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold, color = Ink)
                    if (customCategories.any { !it.isArchived }) {
                        TextButton(
                            onClick = {
                                manageCategoryError = null
                                showManageCategoriesSheet = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null,
                                tint = Purple, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Gestisci", color = Purple, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(13.dp))
                categories.chunked(3).forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        row.forEach { item ->
                            CategoryChoice(
                                category = item,
                                selected = selectedCategory == item.name,
                                onClick = { selectedCategory = item.name },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    if (rowIndex != (categories.size - 1) / 3) Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(13.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF2EBFF))
                        .clickable {
                            createCategoryError = null
                            showCreateCategorySheet = true
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AddCircleOutline, contentDescription = null,
                        tint = Purple, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Nuova categoria", color = Purple,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, null, tint = Purple)
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp), color = Color.White,
            shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 5.dp)) {
                DetailRow(
                    icon = Icons.Filled.DateRange,
                    title = "Data",
                    value = dateText,
                    showArrow = true,
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                selectedDay = LocalDate.of(year, month + 1, day).toEpochDay()
                            },
                            date.year, date.monthValue - 1, date.dayOfMonth
                        ).show()
                    }
                )
                HorizontalDivider(color = Color(0xFFEBEAF4))
                DetailRow(
                    icon = selectedPayment.icon,
                    title = "Metodo di pagamento",
                    value = paymentMethod,
                    showArrow = true,
                    iconTint = selectedPayment.color,
                    iconBackground = selectedPayment.color.copy(alpha = 0.12f),
                    onClick = { showPaymentSheet = true }
                )
                HorizontalDivider(color = Color(0xFFEBEAF4))
                DetailTextField(
                    icon = Icons.Filled.Store,
                    title = "Esercente",
                    value = merchant,
                    onValueChange = { merchant = it.take(90) },
                    placeholder = "Es. Ristorante La Piazza"
                )
                HorizontalDivider(color = Color(0xFFEBEAF4))
                DetailTextField(
                    icon = Icons.Filled.Notes,
                    title = "Nota (opzionale)",
                    value = note,
                    onValueChange = { note = it.take(250) },
                    placeholder = "Aggiungi una nota"
                )
            }
        }

        // La ricorrenza è ora attiva: il dettaglio della frequenza è selezionabile.
        Surface(
            shape = RoundedCornerShape(24.dp), color = Color.White,
            shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DetailIcon(Icons.Filled.Autorenew)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Spesa ricorrente", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("Ripeti automaticamente questa spesa", fontSize = 11.sp, color = Muted)
                    }
                    Switch(
                        checked = recurringEnabled,
                        onCheckedChange = { recurringEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Purple,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFD7D4E4)
                        )
                    )
                }
                if (recurringEnabled) {
                    HorizontalDivider(color = Color(0xFFEBEAF4))
                    DetailRow(
                        icon = Icons.Filled.CalendarMonth,
                        title = "Frequenza",
                        value = RecurrenceInterval.valueOf(recurrenceCode).label,
                        showArrow = true,
                        onClick = { showRecurrenceSheet = true }
                    )
                    HorizontalDivider(color = Color(0xFFEBEAF4))
                    DetailRow(
                        icon = Icons.Filled.EventAvailable,
                        title = "Scadenza",
                        value = expiryText,
                        showArrow = true,
                        onClick = { showExpirySheet = true }
                    )
                    if (!expiryIsValid) {
                        Text(
                            "La scadenza non può precedere l'inizio della spesa.",
                            color = Color(0xFFD33B4E), fontSize = 12.sp,
                            modifier = Modifier.padding(start = 54.dp, end = 5.dp, bottom = 4.dp)
                        )
                    }
                }
            }
        }

        val canSave = amountCents != null && !isSaving && expiryIsValid
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        if (canSave) listOf(Color(0xFF8A50FF), Color(0xFF6121DC))
                        else listOf(Color(0xFFC7B9E8), Color(0xFFACA0D0))
                    )
                )
                .clickable(enabled = canSave) {
                    if (amountCents != null) {
                        // Manteniamo lo stesso ID della spesa quando la modifichiamo.
                        val updatedExpense = existingExpense?.copy(
                            amountCents = amountCents,
                            category = selectedCategory,
                            dateEpochDay = selectedDay,
                            merchant = merchant.trim(),
                            paymentMethod = paymentMethod,
                            note = note.trim(),
                            isRecurring = recurringEnabled
                        ) ?: ExpenseEntity(
                            amountCents = amountCents,
                            category = selectedCategory,
                            dateEpochDay = selectedDay,
                            merchant = merchant.trim(),
                            paymentMethod = paymentMethod,
                            note = note.trim(),
                            isRecurring = recurringEnabled
                        )
                        onSave(
                            updatedExpense,
                            if (recurringEnabled) RecurrenceInterval.valueOf(recurrenceCode) else null,
                            if (recurringEnabled && expiryMode == "MONTHS") expiryMonths else null,
                            if (recurringEnabled && expiryMode == "DATE") expiryAbsoluteDay else null
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(23.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    if (existingExpense == null) "Salva spesa" else "Salva modifiche",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
            }
        }
        Spacer(Modifier.height(5.dp))
    }

    if (showCreateCategorySheet) {
        NewCategoryBottomSheet(
            // Anche i nomi archiviati restano riservati, per proteggere lo storico.
            existingNames = ExpenseCategoryCatalog.all(customCategories).map { it.name },
            isSaving = savingCategory,
            error = createCategoryError,
            onDismiss = {
                if (!savingCategory) showCreateCategorySheet = false
            },
            onCreate = { proposed ->
                if (!savingCategory) {
                    val cleanName = proposed.name.trim().replace(Regex("\\s+"), " ")
                    if (ExpenseCategoryCatalog.all(customCategories).any {
                            it.name.equals(cleanName, ignoreCase = true)
                        }) {
                        createCategoryError = "Esiste già una categoria con questo nome"
                    } else {
                        savingCategory = true
                        createCategoryError = null
                        scope.launch {
                            try {
                                categoryDao.insertCustomCategory(proposed.copy(name = cleanName))
                                selectedCategory = cleanName
                                showCreateCategorySheet = false
                            } catch (e: Exception) {
                                createCategoryError = "Impossibile salvare la categoria: ${e.message ?: "riprova"}"
                            } finally {
                                savingCategory = false
                            }
                        }
                    }
                }
            }
        )
    }

    if (showManageCategoriesSheet) {
        ManageCategoriesBottomSheet(
            categories = customCategories.filterNot { it.isArchived },
            error = manageCategoryError,
            onDismiss = { showManageCategoriesSheet = false },
            onDelete = { category ->
                // Prima di chiedere conferma contiamo le spese collegate.
                scope.launch {
                    try {
                        val count = categoryDao.countExpensesInCategory(category.name)
                        deletionError = null
                        pendingDeletion = CategoryDeletionRequest(category, count)
                        showManageCategoriesSheet = false
                    } catch (e: Exception) {
                        manageCategoryError = "Impossibile controllare la categoria: " +
                                (e.message ?: "riprova")
                    }
                }
            }
        )
    }

    pendingDeletion?.let { request ->
        DeleteCategoryDialog(
            category = request.category,
            linkedExpenses = request.linkedExpenses,
            isDeleting = isDeletingCategory,
            error = deletionError,
            onDismiss = {
                if (!isDeletingCategory) pendingDeletion = null
            },
            onConfirm = {
                if (!isDeletingCategory) {
                    isDeletingCategory = true
                    deletionError = null
                    scope.launch {
                        try {
                            // Transazione atomica: le spese passate mantengono
                            // categoria, nome, icona e colore originali.
                            database.withWriteTransaction {
                                check(categoryDao.archiveCustomCategory(request.category.name) == 1) {
                                    "Categoria già archiviata o inesistente"
                                }
                                // Fermiamo soltanto gli addebiti futuri associati.
                                categoryDao.stopRecurringExpenseSourcesInCategory(request.category.name)
                                categoryDao.deleteRecurringRulesForCategory(request.category.name)
                            }
                            if (selectedCategory == request.category.name &&
                                existingExpense == null) {
                                selectedCategory = "Cibo"
                            }
                            pendingDeletion = null
                        } catch (e: Exception) {
                            deletionError = "Eliminazione non riuscita: " +
                                    (e.message ?: "riprova")
                        } finally {
                            isDeletingCategory = false
                        }
                    }
                }
            }
        )
    }

    if (showRecurrenceSheet) {
        RecurrenceBottomSheet(
            selected = RecurrenceInterval.valueOf(recurrenceCode),
            onDismiss = { showRecurrenceSheet = false },
            onSelect = {
                recurrenceCode = it.name
                showRecurrenceSheet = false
            }
        )
    }

    if (showExpirySheet) {
        ExpiryBottomSheet(
            selectedMode = expiryMode,
            selectedMonths = expiryMonths,
            selectedEndDay = expiryAbsoluteDay,
            startDay = selectedDay,
            onDismiss = { showExpirySheet = false },
            onSelectNone = {
                expiryMode = "NONE"
                showExpirySheet = false
            },
            onSelectMonths = { months ->
                expiryMode = "MONTHS"
                expiryMonths = months
                showExpirySheet = false
            },
            onSelectDate = { day ->
                expiryMode = "DATE"
                expiryAbsoluteDay = day
                showExpirySheet = false
            }
        )
    }

    if (showPaymentSheet) {
        PaymentMethodBottomSheet(
            selectedMethod = paymentMethod,
            onDismiss = { showPaymentSheet = false },
            onSelect = { method ->
                paymentMethod = method.label
                showPaymentSheet = false
            }
        )
    }

}

@Composable
private fun CategoryChoice(
    category: ExpenseCategoryStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier.height(52.dp)
            .clip(shape)
            .background(category.color.copy(alpha = 0.10f))
            .then(if (selected) Modifier.border(BorderStroke(2.dp, Purple), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier.size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(category.color),
            contentAlignment = Alignment.Center
        ) {
            Icon(category.icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Text(
            category.name, fontSize = if (category.name == "Intrattenimento") 9.sp else 10.sp,
            fontWeight = FontWeight.SemiBold, color = Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailIcon(
    icon: ImageVector,
    tint: Color = Purple,
    backgroundColor: Color = Color(0xFFF0EAFF)
) {
    Box(
        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    title: String,
    value: String,
    showArrow: Boolean,
    onClick: () -> Unit,
    iconTint: Color = Purple,
    iconBackground: Color = Color(0xFFF0EAFF)
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(68.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DetailIcon(icon, tint = iconTint, backgroundColor = iconBackground)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(value, fontSize = 12.sp, color = Muted)
        }
        if (showArrow) Icon(Icons.Filled.ChevronRight, null, tint = Purple)
    }
}

@Composable
private fun DetailTextField(
    icon: ImageVector,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(68.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DetailIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(color = Muted, fontSize = 12.sp),
                cursorBrush = SolidColor(Purple),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, fontSize = 12.sp, color = Muted.copy(alpha = 0.68f))
                        inner()
                    }
                }
            )
        }
    }
}


/**
 * Pannello di scelta del metodo di pagamento.
 * La selezione viene mantenuta nel campo `paymentMethod` di AddExpenseScreen
 * e salvata nel database come stringa, compatibilmente con ExpenseEntity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentMethodBottomSheet(
    selectedMethod: String,
    onDismiss: () -> Unit,
    onSelect: (PaymentMethodOption) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Metodo di pagamento",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Ink
            )
            Text(
                text = "Scegli come hai pagato questa spesa",
                fontSize = 13.sp,
                color = Muted
            )
            Spacer(Modifier.height(6.dp))

            paymentMethods.forEach { method ->
                val selected = method.label == selectedMethod
                val shape = RoundedCornerShape(18.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(
                            if (selected) Color(0xFFF2EBFF)
                            else Color(0xFFF8F8FF)
                        )
                        .border(
                            BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) Purple else Color(0xFFECEBF5)
                            ),
                            shape
                        )
                        .clickable { onSelect(method) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(method.color.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = method.icon,
                            contentDescription = null,
                            tint = method.color,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = method.label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        Text(
                            text = method.description,
                            fontSize = 11.sp,
                            color = Muted,
                            maxLines = 2
                        )
                    }
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(25.dp)
                                .background(Purple, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selezionato",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Chiudi", color = Purple, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


/** Pannello nello stesso stile di quello dei metodi di pagamento. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurrenceBottomSheet(
    selected: RecurrenceInterval,
    onDismiss: () -> Unit,
    onSelect: (RecurrenceInterval) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Frequenza della spesa", fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold, color = Ink)
            Text("Scegli ogni quanto registrare la spesa",
                color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(5.dp))

            RecurrenceInterval.entries.forEach { frequency ->
                val active = frequency == selected
                val shape = RoundedCornerShape(18.dp)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(shape)
                        .background(if (active) Color(0xFFF2EBFF) else Background)
                        .border(
                            BorderStroke(
                                if (active) 2.dp else 1.dp,
                                if (active) Purple else Color(0xFFECEBF5)
                            ), shape
                        )
                        .clickable { onSelect(frequency) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DetailIcon(
                        icon = if (frequency == RecurrenceInterval.WEEKLY ||
                            frequency == RecurrenceInterval.BIWEEKLY) Icons.Filled.DateRange
                        else Icons.Filled.CalendarMonth
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(frequency.label, color = Ink, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold)
                        Text(frequency.explanation, color = Muted, fontSize = 11.sp)
                    }
                    if (active) Icon(Icons.Filled.CheckCircle,
                        contentDescription = "Selezionata", tint = Purple)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Chiudi", color = Purple, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Nuova categoria creata dalla stessa schermata di inserimento della spesa. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewCategoryBottomSheet(
    existingNames: List<String>,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (CustomCategory) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var chosenColor by rememberSaveable {
        mutableLongStateOf(ExpenseCategoryCatalog.colorOptions.first().argb)
    }
    var chosenIcon by rememberSaveable {
        mutableStateOf(ExpenseCategoryCatalog.iconOptions.first().key)
    }
    val cleanName = name.trim().replace(Regex("\\s+"), " ")
    val duplicate = existingNames.any { it.equals(cleanName, ignoreCase = true) }
    val valid = cleanName.isNotBlank() && cleanName.length <= 24 && !duplicate
    val color = Color(chosenColor.toInt())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Nuova categoria", color = Ink,
                fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text("Personalizza il nome, il colore e l'icona.",
                color = Muted, fontSize = 13.sp)

            OutlinedTextField(
                value = name,
                onValueChange = { input -> if (input.length <= 28) name = input },
                label = { Text("Nome categoria") },
                placeholder = { Text("Es. Animali domestici") },
                singleLine = true,
                isError = duplicate || cleanName.length > 24,
                shape = RoundedCornerShape(17.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Purple,
                    unfocusedBorderColor = Color(0xFFE3E0ED),
                    focusedLabelColor = Purple
                )
            )
            when {
                duplicate -> Text("Questo nome è già utilizzato", color = Color(0xFFBA2648), fontSize = 12.sp)
                cleanName.length > 24 -> Text("Massimo 24 caratteri", color = Color(0xFFBA2648), fontSize = 12.sp)
            }

            Text("Colore", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
            ExpenseCategoryCatalog.colorOptions.chunked(5).forEach { rowColors ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowColors.forEach { option ->
                        Box(
                            modifier = Modifier.weight(1f).height(45.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(option.color)
                                .clickable { chosenColor = option.argb },
                            contentAlignment = Alignment.Center
                        ) {
                            if (chosenColor == option.argb) {
                                Icon(Icons.Filled.Check, contentDescription = "Colore ${option.label} selezionato",
                                    tint = Color.White, modifier = Modifier.size(25.dp))
                            }
                        }
                    }
                }
            }

            Text("Icona", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
            ExpenseCategoryCatalog.iconOptions.chunked(4).forEach { rowIcons ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowIcons.forEach { item ->
                        val active = item.key == chosenIcon
                        Column(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(15.dp))
                                .background(if (active) color.copy(alpha = 0.15f) else Background)
                                .then(
                                    if (active) Modifier.border(2.dp, color, RoundedCornerShape(15.dp))
                                    else Modifier
                                )
                                .clickable { chosenIcon = item.key }
                                .padding(vertical = 10.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(item.icon, contentDescription = item.label,
                                tint = if (active) color else Muted,
                                modifier = Modifier.size(26.dp))
                            Text(item.label, color = if (active) Ink else Muted,
                                fontSize = 10.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Background)
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(43.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(ExpenseCategoryCatalog.iconFor(chosenIcon), null,
                        tint = Color.White, modifier = Modifier.size(25.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Anteprima", fontSize = 11.sp, color = Muted)
                    Text(cleanName.ifBlank { "Nome categoria" },
                        fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold)
                }
            }
            if (error != null) {
                Text(error, color = Color(0xFFBA2648), fontSize = 12.sp)
            }
            Button(
                onClick = {
                    onCreate(CustomCategory(
                        name = cleanName,
                        colorArgb = chosenColor,
                        iconKey = chosenIcon
                    ))
                },
                enabled = valid && !isSaving,
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Color(0xFFFFFFFF))
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Crea categoria", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


/** Elenco di sole categorie personalizzate: le sei standard non si cancellano. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageCategoriesBottomSheet(
    categories: List<CustomCategory>,
    error: String?,
    onDismiss: () -> Unit,
    onDelete: (CustomCategory) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Text("Gestisci categorie", fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold, color = Ink)
            Text("Le categorie archiviate non saranno più selezionabili, " +
                    "ma le spese passate manterranno nome, colore e icona. " +
                    "Le sei categorie predefinite non possono essere eliminate.",
                fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(3.dp))

            categories.forEach { category ->
                val color = Color(category.colorArgb.toInt())
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(17.dp))
                        .background(Background)
                        .padding(start = 12.dp, top = 7.dp, end = 8.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(ExpenseCategoryCatalog.iconFor(category.iconKey),
                            contentDescription = null, tint = Color.White,
                            modifier = Modifier.size(25.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(category.name, modifier = Modifier.weight(1f),
                        color = Ink, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onDelete(category) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Elimina ${category.name}",
                            tint = Color(0xFFCB3C56), modifier = Modifier.size(22.dp))
                    }
                }
            }

            if (error != null) {
                Text(error, color = Color(0xFFCB3C56), fontSize = 12.sp)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Chiudi", color = Purple, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Eliminazione logica: le spese storiche restano intatte. */
@Composable
private fun DeleteCategoryDialog(
    category: CustomCategory,
    linkedExpenses: Int,
    isDeleting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = Color.White,
        icon = {
            Icon(Icons.Filled.Inventory2, contentDescription = null,
                tint = Color(0xFFCB3C56))
        },
        title = {
            Text("Archiviare ${category.name}?", fontWeight = FontWeight.Bold,
                fontSize = 20.sp, color = Ink)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text(
                    "La categoria non sarà più disponibile per le nuove spese.",
                    color = Muted, fontSize = 14.sp
                )
                if (linkedExpenses > 0) {
                    Text(
                        "Le $linkedExpenses spese già registrate manterranno " +
                                "questa categoria, con il suo colore e la sua icona.",
                        color = Muted, fontSize = 14.sp
                    )
                }
                Text(
                    "Eventuali ricorrenze attive di questa categoria saranno interrotte; " +
                            "le spese già generate resteranno nello Storico.",
                    color = Muted, fontSize = 12.sp
                )
                if (error != null) {
                    Text(error, color = Color(0xFFCB3C56), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCB3C56), contentColor = Color(0xFFFFFFFF)),
                shape = RoundedCornerShape(13.dp)
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp,
                        modifier = Modifier.size(17.dp))
                } else Text("Archivia categoria")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text("Annulla", color = Purple)
            }
        }
    )
}


/** Selezione scadenza: durata dinamica dalla data iniziale oppure data fissa. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryBottomSheet(
    selectedMode: String,
    selectedMonths: Int,
    selectedEndDay: Long,
    startDay: Long,
    onDismiss: () -> Unit,
    onSelectNone: () -> Unit,
    onSelectMonths: (Int) -> Unit,
    onSelectDate: (Long) -> Unit
) {
    val context = LocalContext.current
    var customMonths by rememberSaveable { mutableStateOf(selectedMonths.toString()) }
    val start = LocalDate.ofEpochDay(startDay)
    val selectedEnd = LocalDate.ofEpochDay(selectedEndDay)
    val common = listOf(1, 2, 3, 6, 12)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Quando termina?", fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold, color = Ink)
            Text("Le rate oltre la scadenza non saranno registrate.",
                color = Muted, fontSize = 13.sp)

            ExpiryOptionRow("Senza scadenza", "Continua finché non la disattivi",
                selectedMode == "NONE", onSelectNone)

            Text("DURATA DALLA DATA INIZIALE", color = Muted,
                fontWeight = FontWeight.Bold, fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp))
            common.forEach { months ->
                val finish = start.plusMonths(months.toLong()).format(
                    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN))
                ExpiryOptionRow(
                    "Dopo $months ${if (months == 1) "mese" else "mesi"}",
                    "Ultimo giorno incluso: $finish",
                    selectedMode == "MONTHS" && selectedMonths == months
                ) { onSelectMonths(months) }
            }

            Text("Altra durata (mesi)", fontSize = 13.sp,
                color = Ink, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = customMonths,
                    onValueChange = { value ->
                        if (value.length <= 4 && value.all(Char::isDigit)) customMonths = value
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("Es. 8") },
                    shape = RoundedCornerShape(14.dp)
                )
                val value = customMonths.toIntOrNull()
                Button(
                    onClick = { if (value != null) onSelectMonths(value) },
                    enabled = value != null && value in 1..1200,
                    colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Color(0xFFFFFFFF))
                ) { Text("Imposta") }
            }

            HorizontalDivider(color = Color(0xFFEBEAF4))
            ExpiryOptionRow(
                "Scegli una data precisa",
                if (selectedMode == "DATE") {
                    selectedEnd.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN))
                } else "La scadenza rimane fissa anche cambiando la data iniziale",
                selectedMode == "DATE"
            ) {
                val initial = if (selectedMode == "DATE" &&
                    !selectedEnd.isBefore(start)) selectedEnd else start.plusMonths(2)
                DatePickerDialog(
                    context,
                    { _, year, monthZero, day ->
                        onSelectDate(LocalDate.of(year, monthZero + 1, day).toEpochDay())
                    },
                    initial.year, initial.monthValue - 1, initial.dayOfMonth
                ).show()
            }
        }
    }
}

@Composable
private fun ExpiryOptionRow(title: String, caption: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(if (active) Color(0xFFF2EBFF) else Background)
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) Purple else Color(0xFFEAE6F2),
                shape = RoundedCornerShape(17.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(caption, color = Muted, fontSize = 11.sp)
        }
        if (active) Icon(Icons.Filled.CheckCircle,
            contentDescription = "Selezionata", tint = Purple,
            modifier = Modifier.size(22.dp))
    }
}

