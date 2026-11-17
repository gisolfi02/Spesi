package com.example.expensetracker.ui.screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.ui.components.ExpenseCategoryCatalog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.ExpenseEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DetailInk = Color(0xFF11132B)
private val DetailMuted = Color(0xFF70758F)
private val DetailPurple = Color(0xFF7138EB)
private val DetailBackground = Color(0xFFF8F8FF)

@Composable
fun ExpenseDetailScreen(
    expense: ExpenseEntity,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    isDeleting: Boolean = false,
    recurrenceDescription: String? = null
) {
    var showDeleteDialog by remember(expense.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val dao = remember(context) { AppDatabase.getDatabase(context).expenseDao() }
    val categoryFlow = remember(dao) { dao.observeCustomCategories() }
    val customCategories by categoryFlow.collectAsState(initial = emptyList())
    val style = ExpenseCategoryCatalog.style(expense.category, customCategories)
    val date = remember(expense.dateEpochDay) {
        LocalDate.ofEpochDay(expense.dateEpochDay)
            .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN))
    }

    Column(
        modifier = modifier.fillMaxSize().background(DetailBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 17.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFFF0EEFF))
                    .clickable { onBackClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro", tint = DetailInk)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "Dettaglio spesa",
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DetailInk
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(162.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFFF2ECFF), Color(0xFFDCD7FF))
                    )
                )
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
                    .padding(horizontal = 22.dp)
            ) {
                Text("Importo", fontSize = 15.sp, color = DetailMuted)
                Spacer(Modifier.height(9.dp))
                Text(
                    historyEuro(expense.amountCents),
                    fontSize = 37.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DetailInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    expense.merchant.ifBlank { "Spesa ${expense.category}" },
                    fontSize = 14.sp,
                    color = DetailMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ExpenseInfoLine(
                    style.icon,
                    "Categoria",
                    expense.category,
                    tint = style.color,
                    iconBackground = style.color.copy(alpha = 0.14f)
                )
                HorizontalDivider(color = Color(0xFFEBEAF4))
                ExpenseInfoLine(Icons.Filled.DateRange, "Data", date)
                HorizontalDivider(color = Color(0xFFEBEAF4))
                ExpenseInfoLine(
                    paymentIcon(expense.paymentMethod),
                    "Metodo di pagamento",
                    expense.paymentMethod
                )
                HorizontalDivider(color = Color(0xFFEBEAF4))
                ExpenseInfoLine(
                    Icons.Filled.Store,
                    "Esercente",
                    expense.merchant.ifBlank { "Non specificato" }
                )
                HorizontalDivider(color = Color(0xFFEBEAF4))
                ExpenseInfoLine(
                    Icons.Filled.Notes,
                    "Nota",
                    expense.note.ifBlank { "Nessuna nota" }
                )
                if (expense.isRecurring) {
                    HorizontalDivider(color = Color(0xFFEBEAF4))
                    ExpenseInfoLine(
                        Icons.Filled.Autorenew, "Spesa ricorrente",
                        recurrenceDescription ?: "Sì"
                    )
                }
            }
        }

        Button(
            onClick = onEditClick,
            enabled = !isDeleting,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(19.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DetailPurple, contentColor = Color(0xFFFFFFFF))
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(Modifier.width(9.dp))
            Text("Modifica spesa", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = { showDeleteDialog = true },
            enabled = !isDeleting,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(19.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFC63347), contentColor = Color(0xFFFFFFFF))
        ) {
            if (isDeleting) {
                CircularProgressIndicator(modifier = Modifier.size(21.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                Spacer(Modifier.width(9.dp))
                Text("Elimina spesa", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text("Eliminare questa spesa?", color = DetailInk) },
            text = {
                Text(
                    "La spesa di ${historyEuro(expense.amountCents)} verrà rimossa dal telefono. " +
                            "Questa operazione non può essere annullata."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; onDeleteConfirmed() },
                    enabled = !isDeleting
                ) {
                    Text("Elimina", color = Color(0xFFC63347), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isDeleting
                ) { Text("Annulla", color = DetailPurple) }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun ExpenseInfoLine(
    icon: ImageVector,
    title: String,
    value: String,
    tint: Color = DetailPurple,
    iconBackground: Color = Color(0xFFF0E9FF)
) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = DetailInk, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 13.sp, color = DetailMuted)
        }
    }
}

private fun paymentIcon(method: String): ImageVector = when (method) {
    "Carta di credito" -> Icons.Filled.CreditCard
    "Carta di debito", "Bancomat" -> Icons.Filled.Payment
    "Contanti" -> Icons.Filled.Payments
    "Wallet digitale" -> Icons.Filled.PhoneAndroid
    "Bonifico" -> Icons.Filled.AccountBalance
    else -> Icons.Filled.MoreHoriz
}
