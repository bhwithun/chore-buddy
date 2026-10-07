package com.brian.chorebuddy.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

class LaundryRepository private constructor(context: Context) {
    private val storage = AppStorage(context.applicationContext)
    private var energyGiveUpDay: Long = 0
    private var seriesAttemptDay: Long = 0

    val snapshot: Flow<LaundrySnapshot> = storage.snapshot
    val credentials: Flow<StoredCredentials> = storage.credentials

    suspend fun load(): LaundrySnapshot = storage.snapshotOnce()

    suspend fun connect(pat: String, country: String): ConnectResult = withContext(Dispatchers.IO) {
        val trimmedPat = pat.trim()
        val code = country.trim().uppercase()
        if (trimmedPat.isEmpty()) throw ThinqException("Paste a personal access token first.")
        if (!ThinqRegions.isSupported(code)) {
            throw ThinqException("Country $code is not in the LG region list. US accounts use US.")
        }
        val existing = storage.credentialsOnce()
        val clientId = existing.clientId.ifBlank { UUID.randomUUID().toString() }
        storage.saveCredentials(trimmedPat, code, clientId)
        val devices = ThinqApi(trimmedPat, code, clientId).listDevices()
        storage.saveDevices(devices)
        val slots = assignSlots(devices)
        storage.saveSlots(slots)
        ConnectResult(devices, slots, refresh())
    }

    suspend fun saveSelection(
        washerId: String,
        dryerId: String,
        dishId: String,
    ): LaundrySnapshot = withContext(Dispatchers.IO) {
        val creds = storage.credentialsOnce()
        val byId = creds.devices.associateBy { it.id }
        val slots = SlotMap(
            washer = slotFor(byId[washerId], ApplianceRole.WASHER),
            dryer = slotFor(byId[dryerId], ApplianceRole.DRYER),
            dishwasher = slotFor(byId[dishId], ApplianceRole.DISHWASHER),
        )
        storage.saveSlots(slots)
        refresh()
    }

    /**
     * Fetches cycle state. Energy is included on a manual refresh, and on the
     * first automatic refresh of the local day. Later checks reuse those numbers.
     */
    suspend fun refresh(includeEnergy: Boolean = false): LaundrySnapshot = withContext(Dispatchers.IO) {
        val previous = storage.snapshotOnce()
        val creds = storage.credentialsOnce()
        if (creds.pat.isBlank()) {
            val sample = sampleSnapshot()
            storage.saveSnapshot(sample)
            return@withContext sample
        }
        try {
            val clientId = creds.clientId.ifBlank { UUID.randomUUID().toString() }
            if (clientId != creds.clientId) {
                storage.saveCredentials(creds.pat, creds.country, clientId)
            }
            val fetched = fetch(creds.pat, creds.country, clientId, creds.slots)
            val snapshot = if (includeEnergy || energyStale(previous)) {
                applyEnergy(fetched, creds).also(::rememberEnergyAttempt)
            } else {
                carryEnergy(fetched, previous)
            }
            storage.saveSnapshot(snapshot)
            snapshot
        } catch (error: Exception) {
            val message = error.message ?: "Could not reach LG."
            val kept = previous.copy(error = message)
            storage.saveSnapshot(kept)
            kept
        }
    }

    /** Loads last month and this month when today's energy reading is missing. */
    suspend fun ensureEnergy() = withContext(Dispatchers.IO) {
        val creds = storage.credentialsOnce()
        if (creds.pat.isBlank()) return@withContext
        val previous = storage.snapshotOnce()
        if (previous.usingSample) return@withContext
        if (!energyStale(previous)) return@withContext
        storage.saveSnapshot(applyEnergy(previous, creds).also(::rememberEnergyAttempt))
    }

    private fun energyStale(snapshot: LaundrySnapshot): Boolean {
        val today = LocalDate.now().toEpochDay()
        if (snapshot.energyEpochDay != today) return true
        if (energyGiveUpDay == today) return false
        val linked = snapshot.cycles().filter { it.linked }
        if (linked.isNotEmpty() && linked.all { it.lastMonthWh == null && it.thisMonthWh == null }) return true
        if (seriesAttemptDay == today) return false
        return linked.any { it.thisYearWh == null }
    }

    private fun rememberEnergyAttempt(snapshot: LaundrySnapshot) {
        seriesAttemptDay = snapshot.energyEpochDay
        val linked = snapshot.cycles().filter { it.linked }
        if (linked.isNotEmpty() && linked.all { it.lastMonthWh == null && it.thisMonthWh == null }) {
            energyGiveUpDay = snapshot.energyEpochDay
        }
    }

    private fun fetch(
        pat: String,
        country: String,
        clientId: String,
        slots: SlotMap,
    ): LaundrySnapshot {
        val api = ThinqApi(pat, country, clientId)
        val states = mutableMapOf<String, JsonValue>()
        fun stateFor(id: String): JsonValue? {
            if (id.isBlank()) return null
            return states.getOrPut(id) { api.deviceState(id) }
        }
        val failures = mutableListOf<String>()
        fun cycle(role: ApplianceRole, slot: ApplianceSlot?): ApplianceCycle {
            if (slot == null) return unlinkedCycle(role)
            return try {
                val state = stateFor(slot.deviceId) ?: return unlinkedCycle(role)
                toCycle(role, slot.alias, parseCycle(state, role, slot.zone), linked = true)
            } catch (error: Exception) {
                failures += "${role.title}: ${error.message ?: "unavailable"}"
                unlinkedCycle(role)
            }
        }
        return LaundrySnapshot(
            washer = cycle(ApplianceRole.WASHER, slots.washer),
            dryer = cycle(ApplianceRole.DRYER, slots.dryer),
            dishwasher = cycle(ApplianceRole.DISHWASHER, slots.dishwasher),
            fetchedAtEpochMs = System.currentTimeMillis(),
            error = failures.joinToString(" "),
            usingSample = false,
        )
    }

    private fun carryEnergy(fetched: LaundrySnapshot, previous: LaundrySnapshot): LaundrySnapshot {
        fun keep(cycle: ApplianceCycle, prior: ApplianceCycle) = cycle.copy(
            lastMonthWh = prior.lastMonthWh,
            thisMonthWh = prior.thisMonthWh,
            thisYearWh = prior.thisYearWh,
            priorYearWh = prior.priorYearWh,
        )
        return fetched.copy(
            washer = keep(fetched.washer, previous.washer),
            dryer = keep(fetched.dryer, previous.dryer),
            dishwasher = keep(fetched.dishwasher, previous.dishwasher),
            energyEpochDay = previous.energyEpochDay,
        )
    }

    private fun applyEnergy(snapshot: LaundrySnapshot, creds: StoredCredentials): LaundrySnapshot {
        val api = ThinqApi(creds.pat, creds.country, creds.clientId)
        val today = LocalDate.now()
        var note = ""
        fun unsupported(cycle: ApplianceCycle) = cycle.copy(
            lastMonthWh = null,
            thisMonthWh = null,
            thisYearWh = emptyList(),
            priorYearWh = emptyList(),
        )
        fun yearWatts(deviceId: String, year: Int, throughMonth: Int): List<Int>? {
            val start = "%04d01".format(java.util.Locale.US, year)
            val end = "%04d%02d".format(java.util.Locale.US, year, throughMonth)
            try {
                val byMonth = api.monthlyWattHoursByMonth(deviceId, start, end) ?: return null
                val months = monthsOfYear(year, byMonth, throughMonth)
                Log.w("ChoreBuddy", "energy $year nonzero ${months.count { it > 0 }}")
                return months
            } catch (error: ThinqException) {
                if (error.code == "1221" || error.code == "1305" || error.code == "1308" || error.code == "1309") {
                    throw error
                }
                if (error.code != "1102") throw error
            }
            // A multi-month range is rejected. Ask one month at a time.
            // 1102 on a single month means LG has no row for it.
            var empty = 0
            val months = (1..12).map { month ->
                if (month > throughMonth) {
                    0
                } else {
                    val key = "%04d%02d".format(java.util.Locale.US, year, month)
                    try {
                        api.monthlyWattHours(deviceId, key) ?: return null
                    } catch (error: ThinqException) {
                        if (error.code == "1102") {
                            empty += 1
                            0
                        } else {
                            throw error
                        }
                    }
                }
            }
            Log.w("ChoreBuddy", "energy $year nonzero ${months.count { it > 0 }} empty $empty")
            return months
        }
        fun fill(cycle: ApplianceCycle, slot: ApplianceSlot?): ApplianceCycle {
            if (slot == null || slot.deviceId.isBlank() || !cycle.linked) return unsupported(cycle)
            return try {
                val thisYear = yearWatts(slot.deviceId, today.year, today.monthValue) ?: return unsupported(cycle)
                val priorYear = yearWatts(slot.deviceId, today.year - 1, 12) ?: return unsupported(cycle)
                val thisMonth = thisYear[today.monthValue - 1]
                val lastMonth = if (today.monthValue == 1) priorYear[11] else thisYear[today.monthValue - 2]
                cycle.copy(
                    lastMonthWh = lastMonth,
                    thisMonthWh = thisMonth,
                    thisYearWh = thisYear,
                    priorYearWh = priorYear,
                )
            } catch (error: ThinqException) {
                Log.w("ChoreBuddy", "energy ${error.code}")
                if (error.code == "1305" || error.code == "1308" || error.code == "1309") {
                    note = "Energy needs a token that can view energy use."
                    cycle
                } else if (error.code == "1221") {
                    unsupported(cycle)
                } else {
                    cycle
                }
            } catch (_: Exception) {
                cycle
            }
        }
        val error = listOf(snapshot.error, note).filter { it.isNotBlank() }.distinct().joinToString(" ")
        return snapshot.copy(
            washer = fill(snapshot.washer, creds.slots.washer),
            dryer = fill(snapshot.dryer, creds.slots.dryer),
            dishwasher = fill(snapshot.dishwasher, creds.slots.dishwasher),
            error = error,
            energyEpochDay = today.toEpochDay(),
        )
    }

    private fun slotFor(device: ThinqDevice?, role: ApplianceRole): ApplianceSlot? {
        device ?: return null
        return ApplianceSlot(device.id, zoneFor(device.type, role), device.alias)
    }

    companion object {
        @Volatile
        private var instance: LaundryRepository? = null

        fun get(context: Context): LaundryRepository {
            return instance ?: synchronized(this) {
                instance ?: LaundryRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

data class ConnectResult(
    val devices: List<ThinqDevice>,
    val slots: SlotMap,
    val snapshot: LaundrySnapshot,
)
