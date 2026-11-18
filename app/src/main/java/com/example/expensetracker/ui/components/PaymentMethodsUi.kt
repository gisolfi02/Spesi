package com.example.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Stile condiviso tra elenco e grafico dei metodi di pagamento. */
data class PaymentMethodStyle(
    val name: String,
    val icon: ImageVector,
    val color: Color
)

object PaymentMethodsUi {
    private val options = mapOf(
        "Carta di credito" to PaymentMethodStyle("Carta di credito", Icons.Filled.CreditCard, Color(0xFF3495FF)),
        "Carta di debito" to PaymentMethodStyle("Carta di debito", Icons.Filled.Payment, Color(0xFF8655EB)),
        "Contanti" to PaymentMethodStyle("Contanti", Icons.Filled.Payments, Color(0xFF32BC74)),
        "Wallet digitale" to PaymentMethodStyle("Wallet digitale", Icons.Filled.PhoneAndroid, Color(0xFF20BDC9)),
        "Bonifico" to PaymentMethodStyle("Bonifico", Icons.Filled.AccountBalance, Color(0xFF5D78C9)),
        "Altro" to PaymentMethodStyle("Altro", Icons.Filled.MoreHoriz, Color(0xFFFFB51B))
    )
    fun forName(name: String): PaymentMethodStyle = options[name]
        ?: PaymentMethodStyle(name, Icons.Filled.AccountBalanceWallet, Color(0xFF787F95))
}

/** Sotto il nome di ciascuna spesa, accanto alla data. */
@Composable
fun PaymentMethodInline(method: String, modifier: Modifier = Modifier) {
    val style = PaymentMethodsUi.forName(method)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(style.icon, contentDescription = null, tint = style.color,
            modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(style.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = 10.sp, color = Color(0xFF70758F))
    }
}
