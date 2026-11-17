package com.example.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class DonutSegment(
    val name: String,
    val amount: Double,
    val color: Color
)

@Composable
fun CategoryDonutChart(
    categories: List<DonutSegment>,
    totalLabel: String,
    modifier: Modifier = Modifier
) {
    val total = categories.sumOf { it.amount.coerceAtLeast(0.0) }

    BoxWithConstraints(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        val textRadius = maxWidth * 0.37f
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.245f
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val arcTopLeft = Offset(inset, inset)

            drawArc(
                color = Color(0xFFF0EDF9),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )

            if (total > 0.0) {
                var startAngle = -90f
                categories.forEach { item ->
                    val sweep = (item.amount.coerceAtLeast(0.0) / total * 360.0).toFloat()
                    if (sweep > 0f) {
                        drawArc(
                            color = item.color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth)
                        )
                    }
                    startAngle += sweep
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = totalLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF10132C),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(text = "Totale", fontSize = 11.sp, color = Color(0xFF777C94))
        }

        if (total > 0.0) {
            var angle = -90.0
            categories.forEach { item ->
                val sweep = item.amount.coerceAtLeast(0.0) / total * 360.0
                val midAngle = Math.toRadians(angle + sweep / 2.0)
                val x = (cos(midAngle) * textRadius.value).dp
                val y = (sin(midAngle) * textRadius.value).dp
                val pct = (item.amount / total * 100.0).roundToInt()
                if (pct >= 5) {
                    Text(
                        text = "$pct%",
                        modifier = Modifier.align(Alignment.Center).offset(x = x, y = y),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                angle += sweep
            }
        }
    }
}
