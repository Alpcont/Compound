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

/**
 * Small reusable date chart: values as faint dots, an average as a solid line, and optional bars
 * (bars start the scale at zero). Used for weight, waist, estimated 1RM, volume and cardio minutes.
 */
class WeightChart(context: Context) : View(context) {
    var points: List<Pair<LocalDate, Double>> = emptyList()
    var averages: List<Pair<LocalDate, Double>> = emptyList()
    var bars: List<Pair<LocalDate, Double>> = emptyList()
    var emptyText = "Log your weight to see the chart"
    var yFmt: (Double) -> String = { fmtKg(it) }

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
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.brand); alpha = 150 }
    private val gridPaint = Paint().apply { color = secondary; alpha = 45; strokeWidth = d }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondary
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val dated = points + bars + averages
        if (points.isEmpty() && bars.isEmpty()) {
            textPaint.textAlign = Paint.Align.CENTER
            c.drawText(emptyText, width / 2f, height / 2f, textPaint)
            return
        }
        val left = 40 * d
        val right = width - 8 * d
        val top = 8 * d
        val bottom = height - 22 * d

        val all = dated.map { it.second }
        var minKg = all.min()
        var maxKg = all.max()
        if (bars.isNotEmpty()) {
            minKg = 0.0
            maxKg = maxOf(maxKg * 1.1, 1.0)
        } else {
            if (maxKg - minKg < 1.0) {
                val mid = (maxKg + minKg) / 2
                minKg = mid - 0.5
                maxKg = mid + 0.5
            }
            val pad = (maxKg - minKg) * 0.1
            minKg -= pad
            maxKg += pad
        }

        val first = dated.minOf { it.first }
        val last = dated.maxOf { it.first }
        val d0 = first.toEpochDay()
        val d1 = last.toEpochDay()
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
            c.drawText(yFmt(kg), 0f, yy + 4 * d, textPaint)
        }

        if (bars.isNotEmpty()) {
            val slot = (right - left) / (d1 - d0 + 1).toFloat()
            val half = (slot * 0.35f).coerceIn(1.5f * d, 8f * d)
            for ((date, v) in bars) {
                val xx = x(date)
                c.drawRect(xx - half, y(v), xx + half, y(0.0), barPaint)
            }
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
        c.drawText(first.format(dateFmt), left, labelY, textPaint)
        if (d1 != d0) {
            textPaint.textAlign = Paint.Align.RIGHT
            c.drawText(last.format(dateFmt), right, labelY, textPaint)
        }
    }
}
