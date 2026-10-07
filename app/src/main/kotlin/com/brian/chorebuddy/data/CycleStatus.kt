package com.brian.chorebuddy.data

/**
 * States that mean the machine is not in a cycle. Anything else (including
 * pause, rinse, cool-down, and wrinkle care) counts as running so the donut
 * stays lit.
 */
private val IDLE_STATES = setOf(
    "POWER_OFF",
    "POWEROFF",
    "OFF",
    "INITIAL",
    "NONE",
    "STANDBY",
    "SLEEP",
    "WAITING",
    "END",
    "COMPLETE",
    "COMPLETED",
    "FINISHED",
    "RESERVED",
    "RESERVE",
    "DELAY",
    "DELAYED",
    "ERROR",
    "ERROR_AUTO_OFF",
    "NOT_SELECTED",
    "CANCELLED",
    "CANCELED",
)

fun normalizeState(raw: String): String =
    raw.trim().uppercase().replace(' ', '_').replace('-', '_')

fun isRunningState(raw: String): Boolean {
    val state = normalizeState(raw)
    return state.isNotEmpty() && state !in IDLE_STATES
}

/**
 * Sensing states publish a cycle length before they publish a real countdown.
 * A remaining time of zero here means "not known yet", not "finished".
 */
fun isUnsettledTimer(raw: String): Boolean {
    val state = normalizeState(raw)
    return state.contains("DETECT") || state.contains("SENSING")
}

fun stateLabel(raw: String): String {
    return when (normalizeState(raw)) {
        "", "POWER_OFF", "POWEROFF", "OFF", "INITIAL", "NONE", "STANDBY", "SLEEP", "WAITING" -> "Off"
        "END", "COMPLETE", "COMPLETED", "FINISHED" -> "Done"
        "RESERVED", "RESERVE", "DELAY", "DELAYED" -> "Wait"
        "ERROR", "ERROR_AUTO_OFF" -> "Error"
        "PAUSE", "PAUSED" -> "Paused"
        "CANCELLED", "CANCELED" -> "Off"
        else -> prettyToken(raw)
    }
}

fun prettyToken(raw: String): String {
    val cleaned = raw.trim().replace('-', '_')
    if (cleaned.isEmpty()) return ""
    return cleaned.split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
}

/**
 * Elapsed fraction of the cycle, 0..1. A running machine with no total time
 * still gets a short arc so the ring reads as on.
 */
fun cycleProgress(running: Boolean, remainMinutes: Int?, totalMinutes: Int?): Float {
    if (!running) return 0f
    val total = totalMinutes ?: return 0.08f
    if (total <= 0) return 0.08f
    val remain = (remainMinutes ?: total).coerceAtLeast(0)
    val elapsed = (total - remain).coerceAtLeast(0)
    return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/** Sweep used by both the widget bitmap and the in-app donut. */
fun donutSweepDegrees(progress: Float, running: Boolean): Float {
    if (!running) return 0f
    val shown = progress.coerceIn(0f, 1f).let { if (it <= 0f) 0.04f else it }
    return shown * 360f
}

fun formatRemain(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val hours = safe / 60
    val mins = safe % 60
    return if (hours > 0) "%d:%02d".format(hours, mins) else "${mins}m"
}

fun centerText(cycle: ApplianceCycle): String {
    if (!cycle.linked) return "—"
    if (cycle.running && cycle.remainMinutes != null) return formatRemain(cycle.remainMinutes)
    return stateLabel(cycle.state)
}

fun detailText(cycle: ApplianceCycle): String {
    if (!cycle.linked) return "Not linked"
    if (!cycle.running) return ""
    if (cycle.course.isNotBlank()) return cycle.course
    val state = stateLabel(cycle.state)
    return if (state == centerText(cycle)) "" else state
}

fun describeCycle(cycle: ApplianceCycle): String {
    val name = cycle.role.title
    val base = when {
        !cycle.linked -> "$name not linked"
        !cycle.running -> "$name ${centerText(cycle)}"
        else -> {
            val time = cycle.remainMinutes?.let { "$it minutes left" } ?: "running"
            val course = cycle.course.ifBlank { stateLabel(cycle.state) }
            "$name $course, $time"
        }
    }
    if (cycle.lastMonthWh == null && cycle.thisMonthWh == null) return base
    return "$base. Last month ${spokenKwh(cycle.lastMonthWh)}. This month ${spokenKwh(cycle.thisMonthWh)}"
}

private fun spokenKwh(wattHours: Int?): String {
    val number = formatKwh(wattHours)
    return if (wattHours == null) number else "$number kWh"
}

fun unlinkedCycle(role: ApplianceRole): ApplianceCycle = ApplianceCycle(
    role = role,
    alias = role.title,
    state = "",
    course = "",
    running = false,
    progress = 0f,
    remainMinutes = null,
    totalMinutes = null,
    linked = false,
)

/**
 * Counts a running cycle down from the last LG reading. The label stays on the
 * current minute until that minute has elapsed, and the ring moves with the
 * seconds in between. The stored snapshot is not changed.
 */
/**
 * Minutes to count down from. A sensing cycle that still reports 0 remaining
 * uses its total length until LG publishes the real countdown.
 */
fun countdownBaseline(cycle: ApplianceCycle): Int? {
    val remain = cycle.remainMinutes
    if (remain != null && remain > 0) return remain
    if (cycle.running && isUnsettledTimer(cycle.state)) {
        val total = cycle.totalMinutes
        if (total != null && total > 0) return total
    }
    return remain
}

fun projectCycle(cycle: ApplianceCycle, fetchedAtEpochMs: Long, nowEpochMs: Long): ApplianceCycle {
    val remain = countdownBaseline(cycle)
    if (!cycle.running || remain == null || fetchedAtEpochMs <= 0L) return cycle
    val elapsedMs = (nowEpochMs - fetchedAtEpochMs).coerceAtLeast(0L)
    val remainMs = remain * 60_000L - elapsedMs
    val remainShown = if (remainMs <= 0L) 0 else ((remainMs + 59_999L) / 60_000L).toInt()
    val total = cycle.totalMinutes?.takeIf { it > 0 }
    val progress = if (total == null) {
        cycle.progress
    } else {
        val remainExact = remainMs.coerceAtLeast(0L) / 60_000f
        ((total - remainExact) / total.toFloat()).coerceIn(0f, 1f)
    }
    return cycle.copy(remainMinutes = remainShown, progress = progress)
}

fun LaundrySnapshot.projected(nowEpochMs: Long): LaundrySnapshot {
    if (fetchedAtEpochMs <= 0L) return this
    return copy(
        washer = projectCycle(washer, fetchedAtEpochMs, nowEpochMs),
        dryer = projectCycle(dryer, fetchedAtEpochMs, nowEpochMs),
        dishwasher = projectCycle(dishwasher, fetchedAtEpochMs, nowEpochMs),
    )
}

fun sampleSnapshot(): LaundrySnapshot {
    val wash = ApplianceCycle(
        role = ApplianceRole.WASHER,
        alias = "Washer",
        state = "RINSING",
        course = "Normal",
        running = true,
        progress = cycleProgress(true, 42, 65),
        remainMinutes = 42,
        totalMinutes = 65,
        linked = true,
        lastMonthWh = 18_400,
        thisMonthWh = 3_200,
        thisYearWh = listOf(900, 1100, 1400, 1600, 2100, 2400, 2200, 1900, 18_400, 3_200, 0, 0),
        priorYearWh = listOf(800, 1000, 1200, 1500, 1800, 2000, 1900, 1700, 16_000, 14_200, 12_400, 9_800),
    )
    val dry = ApplianceCycle(
        role = ApplianceRole.DRYER,
        alias = "Dryer",
        state = "POWER_OFF",
        course = "",
        running = false,
        progress = 0f,
        remainMinutes = null,
        linked = true,
        lastMonthWh = 24_600,
        thisMonthWh = 8_100,
        thisYearWh = listOf(4_200, 5_100, 6_400, 7_800, 9_200, 11_000, 10_400, 8_600, 24_600, 8_100, 0, 0),
        priorYearWh = listOf(3_800, 4_400, 5_500, 6_900, 8_100, 9_400, 8_800, 7_200, 20_100, 18_400, 15_200, 11_000),
    )
    val dish = ApplianceCycle(
        role = ApplianceRole.DISHWASHER,
        alias = "Dishwasher",
        state = "RUNNING",
        course = "Auto",
        running = true,
        progress = cycleProgress(true, 18, 124),
        remainMinutes = 18,
        totalMinutes = 124,
        linked = true,
        lastMonthWh = 9_100,
        thisMonthWh = 1_400,
        thisYearWh = listOf(600, 700, 800, 900, 1_100, 1_300, 1_200, 1_000, 9_100, 1_400, 0, 0),
        priorYearWh = listOf(500, 650, 700, 800, 900, 1_000, 950, 880, 7_400, 6_200, 4_800, 3_100),
    )
    return LaundrySnapshot(
        washer = wash,
        dryer = dry,
        dishwasher = dish,
        fetchedAtEpochMs = 0L,
        error = "",
        usingSample = true,
    )
}

private val WASHER_TYPES = setOf(
    "DEVICE_WASHER",
    "DEVICE_WASHTOWER_WASHER",
    "DEVICE_WASHCOMBO_MAIN",
    "DEVICE_WASHCOMBO_MINI",
)

private val DRYER_TYPES = setOf(
    "DEVICE_DRYER",
    "DEVICE_WASHTOWER_DRYER",
)

private val DISH_TYPES = setOf("DEVICE_DISH_WASHER")

private val COMBINED_TYPES = setOf("DEVICE_WASHTOWER", "DEVICE_WASHCOMBO")

fun zoneFor(deviceType: String, role: ApplianceRole): String {
    if (deviceType !in COMBINED_TYPES) return ""
    return when (role) {
        ApplianceRole.WASHER -> "washer"
        ApplianceRole.DRYER -> "dryer"
        ApplianceRole.DISHWASHER -> ""
    }
}

/**
 * Prefer a dedicated washer, dryer, or dishwasher record. A single WashTower
 * or WashCombo device fills both laundry slots and the state parser reads the
 * washer and dryer sections.
 */
fun assignSlots(devices: List<ThinqDevice>): SlotMap {
    fun first(types: Set<String>) = devices.firstOrNull { it.type in types }
    val washer = first(WASHER_TYPES)
    val dryer = first(DRYER_TYPES)
    val dish = first(DISH_TYPES)
    val combined = devices.firstOrNull { it.type in COMBINED_TYPES }
    return SlotMap(
        washer = washer?.let { ApplianceSlot(it.id, "", it.alias) }
            ?: combined?.let { ApplianceSlot(it.id, "washer", it.alias) },
        dryer = dryer?.let { ApplianceSlot(it.id, "", it.alias) }
            ?: combined?.let { ApplianceSlot(it.id, "dryer", it.alias) },
        dishwasher = dish?.let { ApplianceSlot(it.id, "", it.alias) },
    )
}
