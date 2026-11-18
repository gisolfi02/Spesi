package com.example.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.ExpenseEntity
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val Ink = Color(0xFF11132B)
private val Muted = Color(0xFF70758F)
private val Purple = Color(0xFF7138EB)

private fun euro(cents: Long): String =
    "€${DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.ITALIAN)).format(cents / 100.0)}"

/** Due pagine affiancate, scorribili da destra a sinistra e viceversa. */
@Composable
fun ExpenseBreakdownPager(
    expenses: List<ExpenseEntity>,
    categoryStyles: List<ExpenseCategoryStyle>,
    modifier: Modifier = Modifier
) {
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val total = expenses.sumOf { it.amountCents }
    val categoryAmounts = expenses.groupBy { it.category }
        .mapValues { (_, xs) -> xs.sumOf { it.amountCents } }
    val paymentAmounts = expenses.groupBy { it.paymentMethod }
        .mapValues { (_, xs) -> xs.sumOf { it.amountCents } }

    val categories = categoryAmounts.entries.filter { it.value > 0L }.map { (name, cents) ->
        val style = categoryStyles.firstOrNull { it.name == name }
            ?: ExpenseCategoryStyle(name, Purple, Icons.Filled.Receipt)
        BreakdownItem(name, cents, style.color, style.icon)
    }.sortedByDescending { it.cents }
    val payments = paymentAmounts.entries.filter { it.value > 0L }.map { (name, cents) ->
        val style = PaymentMethodsUi.forName(name)
        BreakdownItem(name, cents, style.color, style.icon)
    }.sortedByDescending { it.cents }

    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        color = Color.White, shadowElevation = 4.dp) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (pager.currentPage == 0) "Spese per categoria" else "Spese per pagamento",
                    modifier = Modifier.weight(1f),
                    color = Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().height(236.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                val rows = if (page == 0) categories else payments
                if (rows.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CategoryDonutChart(emptyList(), euro(total), Modifier.size(205.dp))
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryDonutChart(
                            categories = rows.map { DonutSegment(it.name, it.cents.toDouble(), it.color) },
                            totalLabel = euro(total),
                            modifier = Modifier.weight(.94f)
                        )
                        Column(
                            modifier = Modifier.weight(1.06f)
                                .heightIn(max = 226.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            rows.forEach { row -> BreakdownLegendRow(row, total) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                repeat(2) { i ->
                    val active = pager.currentPage == i
                    Box(
                        Modifier.padding(horizontal = 4.dp)
                            .size(width = if (active) 20.dp else 7.dp, height = 7.dp)
                            .clip(CircleShape)
                            .background(if (active) Purple else Color(0xFFDAD5EA))
                            .clickable { scope.launch { pager.animateScrollToPage(i) } }
                    )
                }
            }
        }
    }
}

private data class BreakdownItem(
    val name: String, val cents: Long, val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun BreakdownLegendRow(item: BreakdownItem, total: Long) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 29.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(23.dp).clip(RoundedCornerShape(7.dp)).background(item.color),
            contentAlignment = Alignment.Center) {
            Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(5.dp))
        Text(item.name, modifier = Modifier.weight(1f), fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, color = Ink, lineHeight = 11.sp)
        Spacer(Modifier.width(2.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(euro(item.cents), color = Ink, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${if (total > 0) (item.cents.toDouble() / total * 100).roundToInt() else 0}%",
                color = Muted, fontSize = 9.sp)
        }
    }
}
