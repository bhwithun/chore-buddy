package com.brian.chorebuddy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleStatusTest {
    @Test
    fun idleStatesStayDim() {
        assertFalse(isRunningState("POWER_OFF"))
        assertFalse(isRunningState("END"))
        assertFalse(isRunningState("RESERVED"))
        assertFalse(isRunningState("ERROR"))
        assertEquals("Off", stateLabel("POWER_OFF"))
        assertEquals("Done", stateLabel("END"))
        assertEquals("Wait", stateLabel("RESERVED"))
    }

    @Test
    fun aRunningTimerCountsDownBetweenChecks() {
        val fetchedAt = 1_700_000_000_000L
        val cycle = ApplianceCycle(
            role = ApplianceRole.WASHER,
            alias = "Washer",
            state = "RINSING",
            course = "Normal",
            running = true,
            progress = cycleProgress(true, 10, 20),
            remainMinutes = 10,
            totalMinutes = 20,
            linked = true,
        )
        val started = projectCycle(cycle, fetchedAt, fetchedAt)
        assertEquals(10, started.remainMinutes)
        val later = projectCycle(cycle, fetchedAt, fetchedAt + 90_000L)
        assertEquals(9, later.remainMinutes)
        assertEquals((20f - 8.5f) / 20f, later.progress, 0.001f)
        val done = projectCycle(cycle, fetchedAt, fetchedAt + 10 * 60_000L)
        assertEquals(0, done.remainMinutes)
        assertEquals(1f, done.progress, 0.001f)
    }

    @Test
    fun detectingWithNoRemainUsesTheCycleLength() {
        val fetchedAt = 1_700_000_000_000L
        val cycle = ApplianceCycle(
            role = ApplianceRole.DRYER,
            alias = "Dryer",
            state = "DETECTING",
            course = "",
            running = true,
            progress = 1f,
            remainMinutes = 0,
            totalMinutes = 100,
            linked = true,
        )
        val shown = projectCycle(cycle, fetchedAt, fetchedAt)
        assertEquals(100, shown.remainMinutes)
        assertEquals(0f, shown.progress, 0.001f)
        assertEquals("1:40", formatRemain(shown.remainMinutes!!))
        val later = projectCycle(cycle, fetchedAt, fetchedAt + 90_000L)
        assertEquals(99, later.remainMinutes)
    }

    @Test
    fun aFinishedCountdownStaysAtZero() {
        val cycle = ApplianceCycle(
            role = ApplianceRole.DRYER,
            alias = "Dryer",
            state = "COOLING",
            course = "",
            running = true,
            progress = 1f,
            remainMinutes = 0,
            totalMinutes = 50,
            linked = true,
        )
        val shown = projectCycle(cycle, 1_000L, 1_000L)
        assertEquals(0, shown.remainMinutes)
    }

    @Test
    fun activeStatesFillTheRing() {
        assertTrue(isRunningState("RINSING"))
        assertTrue(isRunningState("PAUSE"))
        assertTrue(isRunningState("COOLING"))
        assertEquals(23f / 65f, cycleProgress(true, 42, 65), 0.001f)
        assertEquals(0f, cycleProgress(false, 42, 65), 0.001f)
        assertEquals("42m", formatRemain(42))
        assertEquals("1:05", formatRemain(65))
    }

    @Test
    fun assignsWashtowerHalvesAndDishwasher() {
        val devices = listOf(
            ThinqDevice("w", "DEVICE_WASHTOWER_WASHER", "Washer", "WK"),
            ThinqDevice("d", "DEVICE_WASHTOWER_DRYER", "Dryer", "WK"),
            ThinqDevice("k", "DEVICE_DISH_WASHER", "Dishwasher", "LD"),
        )
        val slots = assignSlots(devices)
        assertEquals("w", slots.washer?.deviceId)
        assertEquals("", slots.washer?.zone)
        assertEquals("d", slots.dryer?.deviceId)
        assertEquals("k", slots.dishwasher?.deviceId)
    }

    @Test
    fun singleWashtowerFillsBothLaundrySlots() {
        val slots = assignSlots(
            listOf(ThinqDevice("tower", "DEVICE_WASHTOWER", "WashTower", "WK"))
        )
        assertEquals("tower", slots.washer?.deviceId)
        assertEquals("washer", slots.washer?.zone)
        assertEquals("tower", slots.dryer?.deviceId)
        assertEquals("dryer", slots.dryer?.zone)
        assertEquals(null, slots.dishwasher)
    }
}
