package com.brian.chorebuddy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinqParseTest {
    @Test
    fun parsesDeviceList() {
        val devices = parseDevices(
            """
            {
              "messageId": "abc",
              "response": [
                {
                  "deviceId": "wash-1",
                  "deviceInfo": {
                    "deviceType": "DEVICE_WASHTOWER_WASHER",
                    "alias": "Washer",
                    "modelName": "WKE100HWA"
                  }
                },
                {
                  "deviceId": "dish-1",
                  "deviceInfo": {
                    "deviceType": "DEVICE_DISH_WASHER",
                    "alias": "Dishwasher",
                    "modelName": "LDFN4542"
                  }
                }
              ]
            }
            """.trimIndent()
        )
        assertEquals(2, devices.size)
        assertEquals("DEVICE_WASHTOWER_WASHER", devices[0].type)
        assertEquals("Washer", devices[0].alias)
    }

    @Test
    fun readsSeparateWasherAndDryerSections() {
        val state = unwrapThinq(
            """
            {
              "response": {
                "washer": {
                  "runState": { "currentState": "RINSING" },
                  "timer": { "remainHour": 0, "remainMinute": 42, "totalHour": 1, "totalMinute": 5 },
                  "course": { "currentCourse": "NORMAL" }
                },
                "dryer": {
                  "runState": { "currentState": "POWER_OFF" },
                  "timer": { "remainHour": 0, "remainMinute": 0, "totalHour": 0, "totalMinute": 0 }
                }
              }
            }
            """.trimIndent()
        )
        val wash = toCycle(ApplianceRole.WASHER, "Washer", parseCycle(state, ApplianceRole.WASHER, "washer"), true)
        val dry = toCycle(ApplianceRole.DRYER, "Dryer", parseCycle(state, ApplianceRole.DRYER, "dryer"), true)
        assertTrue(wash.running)
        assertEquals(42, wash.remainMinutes)
        assertEquals(65, 60 + 5)
        assertEquals("Normal", wash.course)
        assertEquals(23f / 65f, wash.progress, 0.001f)
        assertFalse(dry.running)
        assertEquals("Off", centerText(dry))
        assertEquals("42m", centerText(wash))
    }

    @Test
    fun flatDryerStateUsesTheRoot() {
        val state = unwrapThinq(
            """
            {
              "response": {
                "runState": { "currentState": "COOLING" },
                "timer": { "remainHour": 0, "remainMinute": 13, "totalHour": 0, "totalMinute": 50 }
              }
            }
            """.trimIndent()
        )
        val dry = toCycle(ApplianceRole.DRYER, "Dryer", parseCycle(state, ApplianceRole.DRYER, ""), true)
        assertTrue(dry.running)
        assertEquals(13, dry.remainMinutes)
        assertEquals((50 - 13) / 50f, dry.progress, 0.001f)
    }

    @Test
    fun invalidTokenMessageIsReadable() {
        val message = errorMessage("""{"error":{"code":"1103","message":"Invalid Token"}}""", 401)
        assertTrue(message.contains("token"))
    }
}
