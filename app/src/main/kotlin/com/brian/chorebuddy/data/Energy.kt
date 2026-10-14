package com.brian.chorebuddy.data

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Month key LG expects on the energy usage call, `YYYYMM`. */
fun energyMonthKey(date: LocalDate): String =
    "%04d%02d".format(Locale.US, date.year, date.monthValue)

fun lastEnergyMonth(today: LocalDate): String =
    energyMonthKey(today.withDayOfMonth(1).minusMonths(1))

fun preferredEnergyProperty(names: List<String>): String? =
    names.firstOrNull { it.equals("energyUsage", ignoreCase = true) } ?: names.firstOrNull()

fun parseEnergyProperties(text: String): List<String> {
    val obj = energyBody(text) ?: return emptyList()
    val items = (obj.fields["property"] as? JsonValue.Arr)?.items ?: return emptyList()
    return items.mapNotNull { (it as? JsonValue.Str)?.value }.filter { it.isNotBlank() }
}

/** Sums watt-hours for one monthly response. An empty list is zero use. */
fun parseEnergyWattHours(text: String, property: String): Int =
    parseEnergyByMonth(text, property).values.sum()

/**
 * Watt-hours keyed by `YYYYMM`. Rows for the same month are added together.
 * An empty list is an empty map, which the caller treats as zero for each month.
 */
fun parseEnergyByMonth(text: String, property: String): Map<String, Int> {
    val obj = energyBody(text) ?: return emptyMap()
    val rows = (obj.fields["dataList"] as? JsonValue.Arr)?.items ?: return emptyMap()
    val totals = linkedMapOf<String, Double>()
    for (item in rows) {
        val row = item.asObj() ?: continue
        val amount = jsonDouble(row.fields[property])
            ?: row.fields.entries.firstNotNullOfOrNull { (key, value) ->
                if (key.equals("usedDate", ignoreCase = true)) null else jsonDouble(value)
            }
            ?: continue
        val month = monthKey(row.fields["usedDate"]) ?: ""
        totals[month] = (totals[month] ?: 0.0) + amount
    }
    return totals.mapValues { (_, value) -> value.roundToInt() }
}

/** Twelve months, January through December. Months after [throughMonth] are zero. */
fun monthsOfYear(year: Int, byMonth: Map<String, Int>, throughMonth: Int): List<Int> =
    (1..12).map { month ->
        if (month > throughMonth) 0
        else byMonth["%04d%02d".format(Locale.US, year, month)] ?: 0
    }

/** Short month name for the energy grid, such as "Sep". */
fun energyMonthLabel(date: LocalDate): String =
    date.month.getDisplayName(TextStyle.SHORT, Locale.US)

/**
 * The [count] calendar months ending with [today], oldest first.
 * Twelve months is one of each name, from last year through this month.
 */
fun recentEnergyMonths(today: LocalDate, count: Int = 12): List<LocalDate> {
    val months = count.coerceIn(0, 12)
    if (months == 0) return emptyList()
    val end = today.withDayOfMonth(1)
    val start = end.minusMonths((months - 1).toLong())
    return (0 until months).map { start.plusMonths(it.toLong()) }
}

/**
 * Watt-hours for one calendar month. Year lists win when they are present.
 * The two stored months cover a reading that has not filled a year list yet.
 */
fun wattHoursForMonth(cycle: ApplianceCycle, month: LocalDate, today: LocalDate): Int? {
    val asked = month.withDayOfMonth(1)
    val series = when (asked.year) {
        today.year -> cycle.thisYearWh
        today.year - 1 -> cycle.priorYearWh
        else -> null
    }
    if (series != null && asked.monthValue in 1..series.size) {
        return series[asked.monthValue - 1]
    }
    val current = today.withDayOfMonth(1)
    if (asked == current) return cycle.thisMonthWh
    if (asked == current.minusMonths(1)) return cycle.lastMonthWh
    return null
}

/** One decimal, no unit. Null stays a dash. Zero is "0.0". */
fun formatKwh(wattHours: Int?): String {
    if (wattHours == null) return "—"
    return "%.1f".format(Locale.US, wattHours / 1000.0)
}

/** Energy calls nest the payload under `response.result`. Status calls do not. */
private fun energyBody(text: String): JsonValue.Obj? {
    val outer = unwrapThinq(text).asObj() ?: return null
    return outer.obj("result") ?: outer
}

private fun monthKey(value: JsonValue?): String? {
    val digits = when (value) {
        is JsonValue.Str -> value.value
        is JsonValue.Num -> value.value.toLong().toString()
        else -> return null
    }.filter { it.isDigit() }
    if (digits.length < 6) return null
    return digits.take(6)
}

private fun jsonDouble(value: JsonValue?): Double? = when (value) {
    is JsonValue.Num -> value.value
    is JsonValue.Str -> value.value.toDoubleOrNull()
    else -> null
}
