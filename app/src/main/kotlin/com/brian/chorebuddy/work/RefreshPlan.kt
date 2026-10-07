package com.brian.chorebuddy.work

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

enum class WakeAction {
    /** Ask LG for a fresh reading. */
    FETCH,

    /** Redraw the widget from the estimate. Does not call LG. */
    REPAINT,
}

data class Wake(
    val at: ZonedDateTime,
    val action: WakeAction,
)

/**
 * LG is checked every 10 minutes from 8:00 a.m. to midnight, while the app is
 * open or the widget is on the home screen. Between checks the rings count
 * down locally. A running timer that reaches zero is checked immediately, even
 * outside that window. While a timer is running, the widget is redrawn on each
 * minute boundary.
 */
object RefreshPlan {
    val interval: Duration = Duration.ofMinutes(10)
    val windowStart: LocalTime = LocalTime.of(8, 0)

    fun inWindow(now: ZonedDateTime): Boolean = !now.toLocalTime().isBefore(windowStart)

    fun fetchIsDue(
        now: ZonedDateTime,
        lastFetch: ZonedDateTime?,
        runningRemainMinutes: List<Int?>,
        unsettled: Boolean = false,
    ): Boolean {
        if (lastFetch == null) return inWindow(now)
        if (unsettled && !lastFetch.plusMinutes(1).isAfter(now)) return true
        if (inWindow(now) && !lastFetch.plus(interval).isAfter(now)) return true
        val zero = earliestZero(lastFetch, runningRemainMinutes)
        return zero != null && !zero.isAfter(now)
    }

    /**
     * Next alarm strictly after [now]. A repaint wins when the displayed minute
     * changes before the next LG check. A zero crossing is an LG check.
     */
    fun nextWake(
        now: ZonedDateTime,
        lastFetch: ZonedDateTime?,
        runningRemainMinutes: List<Int?>,
        listening: Boolean,
        unsettled: Boolean = false,
    ): Wake? {
        if (!listening) return null
        var fetchAt = regularFetchAt(now, lastFetch)
        if (unsettled) {
            val soon = (lastFetch ?: now).plusMinutes(1)
            val soonAt = if (soon.isAfter(now)) soon else now.plusMinutes(1)
            if (soonAt.isBefore(fetchAt)) fetchAt = soonAt
        }
        val zeroAt = earliestZero(lastFetch, runningRemainMinutes)?.takeIf { it.isAfter(now) }
        if (zeroAt != null && zeroAt.isBefore(fetchAt)) fetchAt = zeroAt
        val paintAt = nextPaint(now, lastFetch, runningRemainMinutes)
        if (paintAt != null && paintAt.isBefore(fetchAt)) return Wake(paintAt, WakeAction.REPAINT)
        return Wake(fetchAt, WakeAction.FETCH)
    }

    private fun regularFetchAt(now: ZonedDateTime, lastFetch: ZonedDateTime?): ZonedDateTime {
        val nextMorning = nextWindowStart(now)
        if (!inWindow(now)) return nextMorning
        val raw = (lastFetch ?: now).plus(interval)
        val scheduled = if (raw.isAfter(now)) raw else now.plus(interval)
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        if (!scheduled.isBefore(midnight)) return nextMorning
        return scheduled
    }

    private fun nextWindowStart(now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(windowStart).atZone(now.zone)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    private fun earliestZero(lastFetch: ZonedDateTime?, runningRemainMinutes: List<Int?>): ZonedDateTime? {
        if (lastFetch == null) return null
        return runningRemainMinutes.mapNotNull { remain ->
            if (remain == null || remain <= 0) null else lastFetch.plusMinutes(remain.toLong())
        }.minOrNull()
    }

    private fun nextPaint(
        now: ZonedDateTime,
        lastFetch: ZonedDateTime?,
        runningRemainMinutes: List<Int?>,
    ): ZonedDateTime? {
        if (lastFetch == null) return null
        return runningRemainMinutes.mapNotNull { remain ->
            if (remain == null || remain <= 0) return@mapNotNull null
            val end = lastFetch.plusMinutes(remain.toLong())
            if (!end.isAfter(now)) return@mapNotNull null
            val elapsedMin = Duration.between(lastFetch, now).toMinutes()
            val boundary = lastFetch.plusMinutes(elapsedMin + 1)
            if (boundary.isAfter(now) && boundary.isBefore(end)) boundary else null
        }.minOrNull()
    }
}
