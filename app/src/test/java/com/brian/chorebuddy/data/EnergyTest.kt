package com.brian.chorebuddy.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EnergyTest {
    @Test
    fun monthKeysUseThePreviousCalendarMonth() {
        assertEquals("202610", energyMonthKey(LocalDate.of(2026, 10, 6)))
        assertEquals("202609", lastEnergyMonth(LocalDate.of(2026, 10, 6)))
        assertEquals("202512", lastEnergyMonth(LocalDate.of(2026, 1, 1)))
        assertEquals("Oct", energyMonthLabel(LocalDate.of(2026, 10, 6)))
        assertEquals("Sep", energyMonthLabel(LocalDate.of(2026, 9, 1)))
        assertEquals("Dec", energyMonthLabel(LocalDate.of(2025, 12, 1)))
    }

    @Test
    fun usageResponseSumsWattHours() {
        val body = """
            {"response":{"resultCode":"0000","result":{"property":["energyUsage"],"dataList":[
              {"energyUsage":700.4,"usedDate":"202609"},
              {"energyUsage":300,"usedDate":"202610"}
            ]}}}
        """.trimIndent()
        assertEquals(listOf("energyUsage"), parseEnergyProperties(body))
        assertEquals(1000, parseEnergyWattHours(body, "energyUsage"))
        assertEquals(mapOf("202609" to 700, "202610" to 300), parseEnergyByMonth(body, "energyUsage"))
        val year = monthsOfYear(2026, parseEnergyByMonth(body, "energyUsage"), 10)
        assertEquals(12, year.size)
        assertEquals(0, year[0])
        assertEquals(700, year[8])
        assertEquals(300, year[9])
        assertEquals(0, year[11])
        assertEquals("1.0", formatKwh(1000))
        assertEquals("20.6", formatKwh(20_646))
        assertEquals("0.1", formatKwh(58))
        assertEquals("—", formatKwh(null))
    }

    @Test
    fun anEmptyMonthIsZero() {
        val body = """{"response":{"result":{"property":["energyUsage"],"dataList":[]}}}"""
        assertEquals(0, parseEnergyWattHours(body, "energyUsage"))
        assertEquals("0.0", formatKwh(0))
    }
}
