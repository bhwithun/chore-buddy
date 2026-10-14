package com.brian.chorebuddy.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class DonutBitmapRendererTest {
    @Test
    fun aShortWidgetDropsTheMonthTable() {
        assertEquals(0, DonutBitmapRenderer.energyRowCount(height = 200f, colW = 180f, titleSize = 36f, energySize = 32f))
    }

    @Test
    fun aMediumWidgetKeepsTheRowsThatFit() {
        assertEquals(1, DonutBitmapRenderer.energyRowCount(height = 320f, colW = 180f, titleSize = 36f, energySize = 32f))
        assertEquals(4, DonutBitmapRenderer.energyRowCount(height = 500f, colW = 180f, titleSize = 36f, energySize = 32f))
    }

    @Test
    fun aTallWidgetShowsAllTwelveMonths() {
        assertEquals(12, DonutBitmapRenderer.energyRowCount(height = 900f, colW = 180f, titleSize = 36f, energySize = 32f))
        assertEquals(12, DonutBitmapRenderer.energyRowCount(height = 1400f, colW = 180f, titleSize = 36f, energySize = 32f))
    }
}
