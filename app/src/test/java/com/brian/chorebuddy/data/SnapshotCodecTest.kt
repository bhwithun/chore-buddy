package com.brian.chorebuddy.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotCodecTest {
    @Test
    fun roundTripsASnapshotWithPipesInTheCourse() {
        val original = sampleSnapshot().copy(
            washer = sampleSnapshot().washer.copy(course = "Towels|Hot"),
            error = "rate|limit",
            usingSample = false,
            fetchedAtEpochMs = 1_700_000_000_000L,
        )
        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(original))
        assertEquals(original.washer.course, decoded.washer.course)
        assertEquals(original.error, decoded.error)
        assertEquals(original.usingSample, decoded.usingSample)
        assertEquals(original.fetchedAtEpochMs, decoded.fetchedAtEpochMs)
        assertEquals(original.washer.remainMinutes, decoded.washer.remainMinutes)
        assertEquals(original.washer.progress, decoded.washer.progress, 0.0001f)
        assertEquals(original.dryer.running, decoded.dryer.running)
        assertEquals(original.washer.lastMonthWh, decoded.washer.lastMonthWh)
        assertEquals(original.dryer.thisMonthWh, decoded.dryer.thisMonthWh)
        assertEquals(original.energyEpochDay, decoded.energyEpochDay)
        assertEquals(original.washer.thisYearWh, decoded.washer.thisYearWh)
        assertEquals(original.dryer.priorYearWh, decoded.dryer.priorYearWh)
    }

    @Test
    fun roundTripsDevices() {
        val devices = listOf(ThinqDevice("id|1", "DEVICE_DRYER", "Dry\\er", "M"))
        assertEquals(devices, SnapshotCodec.decodeDevices(SnapshotCodec.encodeDevices(devices)))
    }
}
