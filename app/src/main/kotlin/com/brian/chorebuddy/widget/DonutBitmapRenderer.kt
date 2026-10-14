package com.brian.chorebuddy.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.brian.chorebuddy.data.ApplianceCycle
import com.brian.chorebuddy.data.LaundrySnapshot
import com.brian.chorebuddy.data.centerText
import com.brian.chorebuddy.data.donutSweepDegrees
import com.brian.chorebuddy.data.energyMonthLabel
import com.brian.chorebuddy.data.formatKwh
import com.brian.chorebuddy.data.recentEnergyMonths
import com.brian.chorebuddy.data.wattHoursForMonth
import com.brian.chorebuddy.ui.theme.AppliancePalette
import java.time.LocalDate

object DonutBitmapRenderer {
    fun render(snapshot: LaundrySnapshot, width: Int, height: Int): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AppliancePalette.BACKGROUND)

        val cycles = snapshot.cycles()
        val count = cycles.size.coerceAtLeast(1)
        val today = LocalDate.now()
        val year = recentEnergyMonths(today)
        val measured = measureColumns(
            cycles,
            w.toFloat(),
            count,
            year.map { energyMonthLabel(it) },
            year.flatMap { month -> cycles.map { formatKwh(wattHoursForMonth(it, month, today)) } },
        )
        val rows = energyRowCount(h.toFloat(), measured.colW, measured.titleSize, measured.energySize)
        if (rows == 0) {
            drawCirclesAndCharts(canvas, cycles, w.toFloat(), h.toFloat(), count)
            return bitmap
        }
        drawMonthTable(canvas, cycles, w.toFloat(), h.toFloat(), count, measured, recentEnergyMonths(today, rows), today)
        return bitmap
    }

    /**
     * How many month rows fit while a donut and the year charts stay readable.
     * Zero means the widget is too short for even one row.
     */
    internal fun energyRowCount(height: Float, colW: Float, titleSize: Float, energySize: Float): Int {
        if (height <= 0f || colW <= 0f || energySize <= 0f) return 0
        val pad = (height * 0.03f).coerceAtMost(energySize * 0.35f)
        val minDonut = minOf(colW * 0.62f, height * 0.36f)
        val chartFloor = maxOf(height * 0.24f, minDonut * 0.55f)
        for (rows in 12 downTo 1) {
            val table = energySize * (TABLE_BASE + ROW_STEP * (rows - 1))
            val chartTop = pad + titleSize * TITLE_BLOCK + minDonut + table
            if (chartTop + chartFloor <= height - pad) return rows
        }
        return 0
    }

    private fun drawMonthTable(
        canvas: Canvas,
        cycles: List<ApplianceCycle>,
        width: Float,
        height: Float,
        count: Int,
        measured: ColumnMetrics,
        months: List<LocalDate>,
        today: LocalDate,
    ) {
        val colW = measured.colW
        val energySize = measured.energySize
        val titleSize = measured.titleSize
        val pad = (height * 0.03f).coerceAtMost(energySize * 0.35f)
        val minDonut = minOf(colW * 0.62f, height * 0.36f).coerceAtLeast(8f)
        val chartFloor = maxOf(height * 0.24f, minDonut * 0.55f)
        val table = energySize * (TABLE_BASE + ROW_STEP * (months.size - 1))
        val fixed = pad * 2f + titleSize * TITLE_BLOCK + table
        val leftover = (height - fixed - minDonut - chartFloor).coerceAtLeast(0f)
        val diameter = (minDonut + leftover).coerceAtMost(maxOf(minDonut, colW * 0.88f))

        val titleBaseline = pad + titleSize * 0.82f
        val cy = titleBaseline + titleSize * 0.45f + diameter / 2f
        val rowGap = energySize * ROW_STEP
        var baseline = cy + diameter / 2f + energySize * 0.95f
        val numberPaint = textPaint(AppliancePalette.MUTED, energySize, bold = false)
        val monthCx = colW * count + (width - colW * count) / 2f

        cycles.forEachIndexed { index, cycle ->
            val cx = colW * index + colW / 2f
            val titleColor = if (cycle.running) AppliancePalette.accent(cycle.role) else AppliancePalette.IDLE_TEXT
            canvas.drawText(cycle.role.title, cx, titleBaseline, textPaint(titleColor, titleSize, bold = true))
            drawDonut(canvas, cycle, cx, cy, diameter, titleSize)
        }
        months.forEach { month ->
            cycles.forEachIndexed { index, cycle ->
                val cx = colW * index + colW / 2f
                canvas.drawText(formatKwh(wattHoursForMonth(cycle, month, today)), cx, baseline, numberPaint)
            }
            canvas.drawText(energyMonthLabel(month), monthCx, baseline, numberPaint)
            baseline += rowGap
        }
        val chartTop = baseline - rowGap + energySize * 0.7f
        drawYearCharts(canvas, cycles, colW, count, chartTop, height - pad)
    }

    private data class ColumnMetrics(val colW: Float, val energySize: Float, val titleSize: Float)

    private fun measureColumns(
        cycles: List<ApplianceCycle>,
        width: Float,
        count: Int,
        monthLabels: List<String>,
        valueTexts: List<String>,
    ): ColumnMetrics {
        // Previous drawn sizes were column * 0.315 (title) and * 0.255 (usage).
        // 20% smaller, measured against the one-cell bitmap, is 0.204 and 0.186.
        val baseCol = width / count.toFloat()
        var colW = baseCol
        var energySize = baseCol * 0.186f
        var titleSize = baseCol * 0.204f
        fun fitToColumns() {
            val maxText = colW * 0.96f
            energySize = baseCol * 0.186f
            titleSize = baseCol * 0.204f
            valueTexts.forEach { text ->
                energySize = minOf(energySize, sizeThatFits(text, energySize, maxText, bold = false))
            }
            cycles.forEach { cycle ->
                titleSize = minOf(
                    titleSize,
                    sizeThatFits(cycle.role.title, titleSize, maxText, bold = true),
                )
            }
        }
        fitToColumns()
        if (monthLabels.isNotEmpty()) {
            val monthInk = textPaint(AppliancePalette.MUTED, energySize, bold = false).let { paint ->
                monthLabels.maxOf { paint.measureText(it) }
            }
            val monthCol = monthInk + energySize * 0.5f
            colW = ((width - monthCol) / count).coerceAtLeast(1f)
            fitToColumns()
        }
        return ColumnMetrics(colW, energySize, titleSize)
    }

    private fun drawCirclesAndCharts(
        canvas: Canvas,
        cycles: List<ApplianceCycle>,
        width: Float,
        height: Float,
        count: Int,
    ) {
        val metrics = measureColumns(cycles, width, count, monthLabels = emptyList(), valueTexts = emptyList())
        val colW = metrics.colW
        val titleSize = minOf(metrics.titleSize, height * 0.16f).coerceAtLeast(8f)
        val pad = (height * 0.04f).coerceAtMost(titleSize * 0.45f).coerceAtLeast(2f)
        val titleBaseline = pad + titleSize * 0.82f
        val donutTop = titleBaseline + titleSize * 0.35f
        val bandBottom = height - pad
        val band = (bandBottom - donutTop).coerceAtLeast(16f)
        val gap = (titleSize * 0.2f).coerceAtMost(band * 0.08f)
        val targetDiameter = colW * 0.92f
        val chartIfTarget = band - targetDiameter - gap
        val diameter: Float
        val chartH: Float
        if (chartIfTarget >= band * 0.34f) {
            diameter = targetDiameter
            chartH = chartIfTarget
        } else {
            chartH = band * 0.40f
            diameter = (band - chartH - gap).coerceAtLeast(8f)
        }
        val cy = donutTop + diameter / 2f
        val chartTop = (bandBottom - chartH).coerceAtLeast(cy + diameter / 2f)

        cycles.forEachIndexed { index, cycle ->
            val cx = colW * index + colW / 2f
            val titleColor = if (cycle.running) AppliancePalette.accent(cycle.role) else AppliancePalette.IDLE_TEXT
            canvas.drawText(cycle.role.title, cx, titleBaseline, textPaint(titleColor, titleSize, bold = true))
            drawDonut(canvas, cycle, cx, cy, diameter, titleSize)
        }
        drawYearCharts(canvas, cycles, colW, count, chartTop, bandBottom)
    }

    private fun drawYearCharts(
        canvas: Canvas,
        cycles: List<ApplianceCycle>,
        colW: Float,
        count: Int,
        top: Float,
        bottom: Float,
    ) {
        val plotH = bottom - top
        if (plotH < 8f) return
        val axis = axisPaint()
        val gold = fillPaint(AppliancePalette.GOLD)
        val blue = fillPaint(AppliancePalette.PRIOR_YEAR)
        cycles.forEachIndexed { index, cycle ->
            val peak = maxOf(
                cycle.thisYearWh.orEmpty().maxOrNull() ?: 0,
                cycle.priorYearWh.orEmpty().maxOrNull() ?: 0,
            ).coerceAtLeast(1)
            val left = colW * index + colW * 0.06f
            val right = colW * (index + 1) - colW * 0.06f
            val group = (right - left) / 12f
            val gap = group * 0.22f
            val barW = ((group - gap) / 2f).coerceAtLeast(1f)
            val thisYear = cycle.thisYearWh.orEmpty()
            val priorYear = cycle.priorYearWh.orEmpty()
            for (month in 0 until 12) {
                val x = left + month * group + gap / 2f
                val priorWh = priorYear.getOrElse(month) { 0 }
                val thisWh = thisYear.getOrElse(month) { 0 }
                val priorH = plotH * priorWh / peak
                val thisH = plotH * thisWh / peak
                if (priorH > 0.5f) {
                    canvas.drawRect(x, bottom - priorH, x + barW, bottom, blue)
                }
                if (thisH > 0.5f) {
                    canvas.drawRect(x + barW, bottom - thisH, x + barW * 2f, bottom, gold)
                }
            }
            canvas.drawLine(left, bottom, right, bottom, axis)
        }
    }

    private fun drawDonut(
        canvas: Canvas,
        cycle: ApplianceCycle,
        cx: Float,
        cy: Float,
        diameter: Float,
        titleSize: Float,
    ) {
        val stroke = diameter * 0.11f
        val radius = (diameter - stroke) / 2f
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val track = paint(AppliancePalette.TRACK, stroke)
        canvas.drawCircle(cx, cy, radius, track)

        val sweep = donutSweepDegrees(cycle.progress, cycle.running)
        if (sweep > 0f) {
            val accent = paint(AppliancePalette.accent(cycle.role), stroke)
            if (sweep >= 359f) {
                accent.strokeCap = Paint.Cap.BUTT
                canvas.drawCircle(cx, cy, radius, accent)
            } else {
                canvas.drawArc(oval, -90f, sweep, false, accent)
            }
        }

        val time = textPaint(
            if (cycle.running) AppliancePalette.ACTIVE_TEXT else AppliancePalette.IDLE_TEXT,
            minOf(diameter * 0.40f, titleSize / 0.8f),
            bold = true,
        )
        fitWidth(centerText(cycle), time, diameter * 0.72f)
        val label = centerText(cycle)
        val baseline = cy - (time.descent() + time.ascent()) / 2f
        canvas.drawText(label, cx, baseline, time)
    }

    private fun axisPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppliancePalette.AXIS
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private fun fillPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun paint(color: Int, stroke: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }

    private fun sizeThatFits(text: String, size: Float, maxWidth: Float, bold: Boolean): Float {
        if (maxWidth <= 0f) return size
        val paint = textPaint(AppliancePalette.ACTIVE_TEXT, size, bold)
        val measured = paint.measureText(text)
        if (measured <= maxWidth || measured <= 0f) return size
        return size * maxWidth / measured
    }

    private fun fitWidth(text: String, paint: Paint, maxWidth: Float) {
        if (maxWidth <= 0f) return
        val measured = paint.measureText(text)
        if (measured > maxWidth && measured > 0f) paint.textSize *= maxWidth / measured
    }

    private const val TITLE_BLOCK = 1.27f
    private const val TABLE_BASE = 1.65f
    private const val ROW_STEP = 1.2f

    private fun textPaint(color: Int, size: Float, bold: Boolean): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textAlign = Paint.Align.CENTER
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
}
