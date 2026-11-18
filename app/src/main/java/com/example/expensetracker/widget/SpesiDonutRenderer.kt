package com.example.expensetracker.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max

/** Disegna il grafico nativamente: i widget non possono usare direttamente Compose Canvas. */
internal object SpesiDonutRenderer {
    fun render(segments: List<Pair<Int, Long>>, categoryCount: Int): Bitmap {
        val side = 320
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 48f
            strokeCap = Paint.Cap.BUTT
        }
        val rect = RectF(45f, 45f, side - 45f, side - 45f)
        ring.color = AndroidColor.rgb(230, 226, 244)
        canvas.drawArc(rect, 0f, 360f, false, ring)

        val valid = segments.filter { it.second > 0L }
        val total = valid.sumOf { it.second }
        if (total > 0L) {
            var start = -90f
            valid.forEach { (color, cents) ->
                val sweep = 360f * cents.toDouble() / total.toDouble()
                val gap = if (valid.size > 1) 1.6f else 0f
                ring.color = color
                canvas.drawArc(
                    rect,
                    start + gap / 2f,
                    max(0f, sweep.toFloat() - gap),
                    false,
                    ring
                )
                start += sweep.toFloat()
            }
        }

        val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(17, 19, 43)
            textSize = 65f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
            )
        }
        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(112, 117, 143)
            textSize = 27f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(categoryCount.toString(), 160f, 167f, number)
        canvas.drawText(if (categoryCount == 1) "categoria" else "categorie", 160f, 207f, subtitle)
        return bitmap
    }
}
