package com.brian.chorebuddy.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID

/**
 * LG ThinQ Connect client.
 *
 * Auth is a personal access token from https://connect-pat.lgthinq.com, sent as
 * a bearer token. [SERVICE_KEY] is the public service key shipped in LG's open
 * SDK. It identifies the API, not the account.
 *
 * https://thinq.developer.lge.com/en/cloud/docs/thinq-connect/PAT-en/
 */
class ThinqApi(
    private val accessToken: String,
    private val country: String,
    private val clientId: String,
) {
    fun listDevices(): List<ThinqDevice> = parseDevices(get("devices"))

    fun deviceState(deviceId: String): JsonValue = unwrapThinq(get("devices/$deviceId/state"))

    /**
     * Watt-hours for one calendar month (`YYYYMM`). Null when this machine
     * does not publish an energy property. LG asks that this not be polled often.
     */
    fun monthlyWattHours(deviceId: String, month: String): Int? =
        monthlyWattHoursByMonth(deviceId, month, month)?.values?.sum()

    /**
     * Watt-hours for each `YYYYMM` in the inclusive range. Null when this
     * machine does not publish an energy property.
     */
    fun monthlyWattHoursByMonth(deviceId: String, startMonth: String, endMonth: String): Map<String, Int>? {
        val property = energyProperty(deviceId) ?: return null
        val encoded = URLEncoder.encode(property, "UTF-8")
        val path = "devices/energy/$deviceId/usage?property=$encoded&period=MONTHLY&startDate=$startMonth&endDate=$endMonth"
        return parseEnergyByMonth(get(path), property)
    }

    private val energyProperties = mutableMapOf<String, String?>()

    private fun energyProperty(deviceId: String): String? {
        if (energyProperties.containsKey(deviceId)) return energyProperties[deviceId]
        val name = preferredEnergyProperty(parseEnergyProperties(get("devices/energy/$deviceId/profile")))
        energyProperties[deviceId] = name
        return name
    }

    private fun get(path: String): String {
        val host = ThinqRegions.hostForCountry(country)
        val url = URL("$host/$path")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("x-country", country.trim().uppercase())
            setRequestProperty("x-message-id", messageId())
            setRequestProperty("x-client-id", clientId)
            setRequestProperty("x-api-key", SERVICE_KEY)
            setRequestProperty("x-service-phase", "OP")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ThinqException(errorMessage(body, code), lgErrorCode(body))
            body
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val SERVICE_KEY = "v6GFvkweNo7DK7yD3ylIZ9w52aKBU0eJ7wLXkSR3"
        const val PAT_URL = "https://connect-pat.lgthinq.com"

        fun messageId(): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(uuidBytes())

        private fun uuidBytes(): ByteArray {
            val uuid = UUID.randomUUID()
            val bytes = ByteArray(16)
            val msb = uuid.mostSignificantBits
            val lsb = uuid.leastSignificantBits
            for (i in 0 until 8) {
                bytes[i] = (msb ushr (8 * (7 - i))).toByte()
                bytes[8 + i] = (lsb ushr (8 * (7 - i))).toByte()
            }
            return bytes
        }
    }
}
