package com.brian.chorebuddy.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RefreshPlanTest {
    private val zone: ZoneId = ZoneId.of("America/Detroit")

    @Test
    fun beforeEightWaitsUntilEight() {
        val wake = RefreshPlan.nextWake(
            now = at("2026-10-06T07:30"),
            lastFetch = at("2026-10-06T07:20"),
            runningRemainMinutes = emptyList(),
            listening = true,
        )
        assertEquals(Wake(at("2026-10-06T08:00"), WakeAction.FETCH), wake)
        assertFalse(RefreshPlan.fetchIsDue(at("2026-10-06T07:30"), at("2026-10-06T07:20"), emptyList()))
    }

    @Test
    fun idleMachinesAreCheckedEveryTenMinutes() {
        val wake = RefreshPlan.nextWake(
            now = at("2026-10-06T09:00"),
            lastFetch = at("2026-10-06T08:55"),
            runningRemainMinutes = emptyList(),
            listening = true,
        )
        assertEquals(Wake(at("2026-10-06T09:05"), WakeAction.FETCH), wake)
    }

    @Test
    fun aRunningTimerRepaintsEachMinuteAndChecksAtTen() {
        val wake = RefreshPlan.nextWake(
            now = at("2026-10-06T09:00"),
            lastFetch = at("2026-10-06T08:55"),
            runningRemainMinutes = listOf(40),
            listening = true,
        )
        assertEquals(Wake(at("2026-10-06T09:01"), WakeAction.REPAINT), wake)
    }

    @Test
    fun aTimerThatReachesZeroIsCheckedThen() {
        val wake = RefreshPlan.nextWake(
            now = at("2026-10-06T09:00"),
            lastFetch = at("2026-10-06T08:55"),
            runningRemainMinutes = listOf(8),
            listening = true,
        )
        assertEquals(Wake(at("2026-10-06T09:01"), WakeAction.REPAINT), wake)
        val atZero = RefreshPlan.nextWake(
            now = at("2026-10-06T09:02"),
            lastFetch = at("2026-10-06T08:55"),
            runningRemainMinutes = listOf(8),
            listening = true,
        )
        assertEquals(Wake(at("2026-10-06T09:03"), WakeAction.FETCH), atZero)
        assertTrue(RefreshPlan.fetchIsDue(at("2026-10-06T09:03"), at("2026-10-06T08:55"), listOf(8)))
    }

    @Test
    fun aZeroAfterMidnightIsStillChecked() {
        val wake = RefreshPlan.nextWake(
            now = at("2026-10-06T23:58"),
            lastFetch = at("2026-10-06T23:55"),
            runningRemainMinutes = listOf(8),
            listening = true,
        )
        assertEquals(Wake(at("2026-10-06T23:59"), WakeAction.REPAINT), wake)
        val overnight = RefreshPlan.nextWake(
            now = at("2026-10-07T00:02"),
            lastFetch = at("2026-10-06T23:55"),
            runningRemainMinutes = listOf(8),
            listening = true,
        )
        assertEquals(Wake(at("2026-10-07T00:03"), WakeAction.FETCH), overnight)
    }

    @Test
    fun tenMinuteCheckDoesNotCrossMidnight() {
        val wake = RefreshPlan.nextWake(
            now = at("2026-10-06T23:55"),
            lastFetch = at("2026-10-06T23:50"),
            runningRemainMinutes = emptyList(),
            listening = true,
        )
        assertEquals(Wake(at("2026-10-07T08:00"), WakeAction.FETCH), wake)
    }

    @Test
    fun aSensingCycleWithNoCountdownIsCheckedAgainSoon() {
        val wake = RefreshPlan.nextWake(
            now = at("2026-10-06T21:55:20"),
            lastFetch = at("2026-10-06T21:55:00"),
            runningRemainMinutes = listOf(100),
            listening = true,
            unsettled = true,
        )
        assertEquals(Wake(at("2026-10-06T21:56:00"), WakeAction.FETCH), wake)
        assertFalse(
            RefreshPlan.fetchIsDue(
                at("2026-10-06T21:55:20"),
                at("2026-10-06T21:55:00"),
                listOf(100),
                unsettled = true,
            )
        )
        assertTrue(
            RefreshPlan.fetchIsDue(
                at("2026-10-06T21:56:00"),
                at("2026-10-06T21:55:00"),
                listOf(100),
                unsettled = true,
            )
        )
    }

    @Test
    fun stoppedListeningCancelsTheSchedule() {
        assertNull(
            RefreshPlan.nextWake(
                now = at("2026-10-06T12:00"),
                lastFetch = at("2026-10-06T11:50"),
                runningRemainMinutes = listOf(4),
                listening = false,
            )
        )
    }

    private fun at(text: String): ZonedDateTime = LocalDateTime.parse(text).atZone(zone)
}
