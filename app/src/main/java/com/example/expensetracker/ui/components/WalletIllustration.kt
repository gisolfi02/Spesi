package com.example.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate

/** Illustrazione vettoriale locale: nessuna immagine o connessione necessaria. */
@Composable
fun WalletIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val sx = size.width / 180f
        val sy = size.height / 140f
        fun pt(x: Float, y: Float) = Offset(x * sx, y * sy)
        fun sz(w: Float, h: Float) = Size(w * sx, h * sy)
        fun corner(r: Float) = CornerRadius(r * sx, r * sy)

        // Foglie verdi dietro il portafoglio.
        rotate(-30f, pivot = pt(137f, 46f)) {
            drawOval(
                brush = Brush.linearGradient(listOf(Color(0xFF98F09B), Color(0xFF28B66A))),
                topLeft = pt(127f, 4f), size = sz(26f, 68f)
            )
        }
        rotate(35f, pivot = pt(151f, 64f)) {
            drawOval(
                brush = Brush.linearGradient(listOf(Color(0xFF8DE99A), Color(0xFF2CC67B))),
                topLeft = pt(140f, 30f), size = sz(24f, 55f)
            )
        }

        // Piccoli riflessi dorati.
        drawLine(Color(0xFFFFAE1F), pt(39f, 23f), pt(30f, 10f), strokeWidth = 5f * sx)
        drawLine(Color(0xFFFFC43A), pt(23f, 42f), pt(9f, 36f), strokeWidth = 5f * sx)

        // Corpo del portafoglio con profondita' e gradiente.
        drawRoundRect(
            color = Color(0xFF3C22AA),
            topLeft = pt(67f, 31f), size = sz(91f, 91f), cornerRadius = corner(22f)
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFFBFA4FF), Color(0xFF7846F5), Color(0xFF4527C5)),
                start = pt(73f, 35f), end = pt(157f, 122f)
            ),
            topLeft = pt(62f, 28f), size = sz(91f, 92f), cornerRadius = corner(22f)
        )
        drawRoundRect(
            color = Color(0xFFBBA0FF),
            topLeft = pt(70f, 36f), size = sz(76f, 76f), cornerRadius = corner(17f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * sx)
        )

        // Chiusura esterna del portafoglio.
        drawRoundRect(
            color = Color(0xFF4D2BBE),
            topLeft = pt(126f, 70f), size = sz(48f, 27f), cornerRadius = corner(11f)
        )
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFFBCA7FF), Color(0xFF8659ED))),
            topLeft = pt(124f, 66f), size = sz(45f, 27f), cornerRadius = corner(11f)
        )
        drawCircle(Color(0xFFDDD1FF), radius = 7f * sx, center = pt(142f, 79f))
        drawCircle(Color(0xFF9870F3), radius = 4f * sx, center = pt(141f, 78f))

        // Monete sovrapposte sul davanti.
        fun coin(x: Float, y: Float, w: Float) {
            drawRoundRect(
                color = Color(0xFFE59B09),
                topLeft = pt(x, y + 8f), size = sz(w, 15f), cornerRadius = corner(5f)
            )
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFFFDB5C), Color(0xFFFFA71B))),
                topLeft = pt(x, y + 4f), size = sz(w, 13f), cornerRadius = corner(5f)
            )
            drawOval(
                brush = Brush.verticalGradient(listOf(Color(0xFFFFE78A), Color(0xFFFFB52E))),
                topLeft = pt(x, y), size = sz(w, 9f)
            )
        }
        coin(19f, 103f, 44f)
        coin(45f, 113f, 44f)
    }
}
