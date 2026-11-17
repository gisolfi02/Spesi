package com.example.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/** Calendario e monete disegnati interamente in locale: nessuna immagine esterna. */
@Composable
fun CalendarIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val sx = size.width / 180f
        val sy = size.height / 145f
        fun pt(x: Float, y: Float) = Offset(x * sx, y * sy)
        fun sz(w: Float, h: Float) = Size(w * sx, h * sy)
        fun corner(r: Float) = CornerRadius(r * sx, r * sy)

        // Foglie sul retro e piccoli raggi dorati.
        rotate(-32f, pivot = pt(145f, 63f)) {
            drawOval(
                brush = Brush.linearGradient(listOf(Color(0xFF98F09B), Color(0xFF27B871))),
                topLeft = pt(140f, 19f), size = sz(23f, 73f)
            )
        }
        rotate(33f, pivot = pt(155f, 82f)) {
            drawOval(
                brush = Brush.linearGradient(listOf(Color(0xFF8DE99A), Color(0xFF2CC67B))),
                topLeft = pt(145f, 54f), size = sz(20f, 58f)
            )
        }
        drawLine(Color(0xFFFFBA2C), pt(41f, 28f), pt(30f, 12f), strokeWidth = 5f * sx)
        drawLine(Color(0xFFFFC640), pt(25f, 45f), pt(8f, 38f), strokeWidth = 5f * sx)

        // Ombra e corpo calendario.
        drawRoundRect(
            color = Color(0xFF3E2AA2),
            topLeft = pt(72f, 30f), size = sz(92f, 99f), cornerRadius = corner(19f)
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFFC9B7FF), Color(0xFF8458F6), Color(0xFF5336C5)),
                start = pt(70f, 24f), end = pt(161f, 126f)
            ),
            topLeft = pt(67f, 26f), size = sz(91f, 98f), cornerRadius = corner(18f)
        )
        drawRoundRect(
            color = Color(0xFFF5F2FF),
            topLeft = pt(80f, 46f), size = sz(67f, 63f), cornerRadius = corner(9f)
        )
        // Griglia settimanale.
        for (row in 0..2) {
            for (col in 0..3) {
                drawRoundRect(
                    color = if ((row + col) % 3 == 0) Color(0xFFA18AFA)
                        else Color(0xFFD8D0FF),
                    topLeft = pt(88f + 14f * col, 57f + 15f * row),
                    size = sz(10f, 10f), cornerRadius = corner(2.5f)
                )
            }
        }
        // Anelli della rilegatura.
        for (x in listOf(91f, 112f, 133f)) {
            drawArc(
                color = Color(0xFFE4D9FF), startAngle = 185f, sweepAngle = 270f,
                useCenter = false, topLeft = pt(x - 5f, 16f), size = sz(11f, 27f),
                style = Stroke(width = 5f * sx)
            )
        }
        // Monete in primo piano.
        fun coin(x: Float, y: Float, width: Float) {
            drawRoundRect(Color(0xFFDD8E06), pt(x, y + 8f),
                sz(width, 16f), cornerRadius = corner(5f))
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFFFD85D), Color(0xFFFFA51A))),
                topLeft = pt(x, y + 4f), size = sz(width, 14f), cornerRadius = corner(5f)
            )
            drawOval(
                brush = Brush.verticalGradient(listOf(Color(0xFFFFEA93), Color(0xFFFFB42E))),
                topLeft = pt(x, y), size = sz(width, 9f)
            )
        }
        coin(17f, 111f, 43f)
        coin(41f, 122f, 46f)
    }
}
