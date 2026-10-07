package com.brian.chorebuddy.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DonutBitmapRendererTest {
    @Test
    fun aShortWidgetDropsTheMonthTable() {
        assertFalse(DonutBitmapRenderer.energyTableFits(height = 320f, colW = 180f, titleSize = 36f, energySize = 32f))
    }

    @Test
    fun aTallWidgetKeepsTheMonthTable() {
        assertTrue(DonutBitmapRenderer.energyTableFits(height = 900f, colW = 180f, titleSize = 36f, energySize = 32f))
    }
}
