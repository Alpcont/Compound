package com.alp.compound

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Daily weights as faint dots, the 7-day average as a solid line. */
class WeightChart(context: Context) : View(context) {
    var points: List<Pair<LocalDate, Double>> = emptyList()
    var averages: List<Pair<LocalDate, Double>> = emptyList()

    private val d = resources.displayMetrics.density
    private val secondary = context.getColor(R.color.text_secondary)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = secondary; alpha = 140 }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.brand)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * d
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val gridPaint = Paint().apply { color = secondary; alpha = 45; strokeWidth = d }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondary
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (points.isEmpty()) {
            textPaint.textAlign = Paint.Align.CENTER
            c.drawText("Log your weight to see the chart", width / 2f, height / 2f, textPaint)
            return
        }
        val left = 40 * d
        val right = width - 8 * d
        val top = 8 * d
        val bottom = height - 22 * d

        val all = points.map { it.second } + averages.map { it.second }
        var minKg = all.min()
        var maxKg = all.max()
        if (maxKg - minKg < 1.0) {
            val mid = (maxKg + minKg) / 2
            minKg = mid - 0.5
            maxKg = mid + 0.5
        }
        val pad = (maxKg - minKg) * 0.1
        minKg -= pad
        maxKg += pad

        val d0 = points.first().first.toEpochDay()
        val d1 = points.last().first.toEpochDay()
        val span = (d1 - d0).coerceAtLeast(1L).toFloat()
        fun x(date: LocalDate): Float =
            if (d1 == d0) (left + right) / 2f
            else left + (date.toEpochDay() - d0).toFloat() / span * (right - left)
        fun y(kg: Double): Float = (bottom - (kg - minKg) / (maxKg - minKg) * (bottom - top)).toFloat()

        textPaint.textAlign = Paint.Align.LEFT
        for (i in 0..3) {
            val kg = minKg + (maxKg - minKg) * i / 3.0
            val yy = y(kg)
            c.drawLine(left, yy, right, yy, gridPaint)
            c.drawText(fmtKg(kg), 0f, yy + 4 * d, textPaint)
        }

        for ((date, kg) in points) c.drawCircle(x(date), y(kg), 3f * d, dotPaint)

        if (averages.size > 1) {
            val path = Path()
            averages.forEachIndexed { i, (date, kg) ->
                if (i == 0) path.moveTo(x(date), y(kg)) else path.lineTo(x(date), y(kg))
            }
            c.drawPath(path, linePaint)
        }

        val labelY = height - 6 * d
        c.drawText(points.first().first.format(dateFmt), left, labelY, textPaint)
        if (d1 != d0) {
            textPaint.textAlign = Paint.Align.RIGHT
            c.drawText(points.last().first.format(dateFmt), right, labelY, textPaint)
        }
    }
}
