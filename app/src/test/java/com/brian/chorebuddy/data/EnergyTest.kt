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
    fun twelveMonthsRunThroughTheCurrentMonth() {
        val today = LocalDate.of(2026, 10, 13)
        val months = recentEnergyMonths(today)
        assertEquals(12, months.size)
        assertEquals(LocalDate.of(2025, 11, 1), months.first())
        assertEquals(LocalDate.of(2026, 10, 1), months.last())
        assertEquals(
            listOf(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)),
            recentEnergyMonths(today, 3),
        )
        assertEquals(emptyList<LocalDate>(), recentEnergyMonths(today, 0))

        val january = recentEnergyMonths(LocalDate.of(2026, 1, 2))
        assertEquals(LocalDate.of(2025, 2, 1), january.first())
        assertEquals(LocalDate.of(2026, 1, 1), january.last())
    }

    @Test
    fun monthWattHoursComeFromTheYearLists() {
        val today = LocalDate.of(2026, 10, 13)
        val cycle = ApplianceCycle(
            role = ApplianceRole.WASHER,
            alias = "Washer",
            state = "POWER_OFF",
            course = "",
            running = false,
            progress = 0f,
            remainMinutes = null,
            linked = true,
            lastMonthWh = 1,
            thisMonthWh = 2,
            thisYearWh = (1..12).map { it * 10 },
            priorYearWh = (1..12).map { it * 100 },
        )
        assertEquals(1100, wattHoursForMonth(cycle, LocalDate.of(2025, 11, 1), today))
        assertEquals(100, wattHoursForMonth(cycle, LocalDate.of(2026, 10, 8), today))
    }

    @Test
    fun aMissingYearListUsesTheTwoStoredMonths() {
        val today = LocalDate.of(2026, 10, 13)
        val cycle = ApplianceCycle(
            role = ApplianceRole.WASHER,
            alias = "Washer",
            state = "POWER_OFF",
            course = "",
            running = false,
            progress = 0f,
            remainMinutes = null,
            linked = true,
            lastMonthWh = 5,
            thisMonthWh = 9,
        )
        assertEquals(9, wattHoursForMonth(cycle, LocalDate.of(2026, 10, 1), today))
        assertEquals(5, wattHoursForMonth(cycle, LocalDate.of(2026, 9, 1), today))
        assertEquals(null, wattHoursForMonth(cycle, LocalDate.of(2026, 8, 1), today))
    }

    @Test
    fun anEmptyMonthIsZero() {
        val body = """{"response":{"result":{"property":["energyUsage"],"dataList":[]}}}"""
        assertEquals(0, parseEnergyWattHours(body, "energyUsage"))
        assertEquals("0.0", formatKwh(0))
    }
}
