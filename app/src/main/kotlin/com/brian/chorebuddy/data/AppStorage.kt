package com.brian.chorebuddy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chore_buddy")

class AppStorage(private val context: Context) {
    object Keys {
        val PAT = stringPreferencesKey("lg_pat")
        val COUNTRY = stringPreferencesKey("lg_country")
        val CLIENT_ID = stringPreferencesKey("lg_client_id")
        val WASHER_ID = stringPreferencesKey("washer_id")
        val WASHER_ZONE = stringPreferencesKey("washer_zone")
        val WASHER_ALIAS = stringPreferencesKey("washer_alias")
        val DRYER_ID = stringPreferencesKey("dryer_id")
        val DRYER_ZONE = stringPreferencesKey("dryer_zone")
        val DRYER_ALIAS = stringPreferencesKey("dryer_alias")
        val DISH_ID = stringPreferencesKey("dish_id")
        val DISH_ZONE = stringPreferencesKey("dish_zone")
        val DISH_ALIAS = stringPreferencesKey("dish_alias")
        val SNAPSHOT = stringPreferencesKey("snapshot")
        val DEVICES = stringPreferencesKey("devices")
    }

    val snapshot: Flow<LaundrySnapshot> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.SNAPSHOT]
        if (raw.isNullOrBlank()) sampleSnapshot() else SnapshotCodec.decode(raw)
    }

    val credentials: Flow<StoredCredentials> = context.dataStore.data.map { prefs ->
        StoredCredentials(
            pat = prefs[Keys.PAT].orEmpty(),
            country = prefs[Keys.COUNTRY]?.ifBlank { null } ?: "US",
            clientId = prefs[Keys.CLIENT_ID].orEmpty(),
            slots = SlotMap(
                washer = slot(prefs[Keys.WASHER_ID], prefs[Keys.WASHER_ZONE], prefs[Keys.WASHER_ALIAS]),
                dryer = slot(prefs[Keys.DRYER_ID], prefs[Keys.DRYER_ZONE], prefs[Keys.DRYER_ALIAS]),
                dishwasher = slot(prefs[Keys.DISH_ID], prefs[Keys.DISH_ZONE], prefs[Keys.DISH_ALIAS]),
            ),
            devices = SnapshotCodec.decodeDevices(prefs[Keys.DEVICES].orEmpty()),
        )
    }

    suspend fun snapshotOnce(): LaundrySnapshot = snapshot.first()

    suspend fun credentialsOnce(): StoredCredentials = credentials.first()

    suspend fun saveCredentials(pat: String, country: String, clientId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PAT] = pat.trim()
            prefs[Keys.COUNTRY] = country.trim().uppercase()
            prefs[Keys.CLIENT_ID] = clientId
        }
    }

    suspend fun saveSlots(slots: SlotMap) {
        context.dataStore.edit { prefs ->
            putSlot(prefs, Keys.WASHER_ID, Keys.WASHER_ZONE, Keys.WASHER_ALIAS, slots.washer)
            putSlot(prefs, Keys.DRYER_ID, Keys.DRYER_ZONE, Keys.DRYER_ALIAS, slots.dryer)
            putSlot(prefs, Keys.DISH_ID, Keys.DISH_ZONE, Keys.DISH_ALIAS, slots.dishwasher)
        }
    }

    suspend fun saveDevices(devices: List<ThinqDevice>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEVICES] = SnapshotCodec.encodeDevices(devices)
        }
    }

    suspend fun saveSnapshot(snapshot: LaundrySnapshot) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SNAPSHOT] = SnapshotCodec.encode(snapshot)
        }
    }

    private fun slot(id: String?, zone: String?, alias: String?): ApplianceSlot? {
        if (id.isNullOrBlank()) return null
        return ApplianceSlot(id, zone.orEmpty(), alias.orEmpty())
    }

    private fun putSlot(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        idKey: Preferences.Key<String>,
        zoneKey: Preferences.Key<String>,
        aliasKey: Preferences.Key<String>,
        slot: ApplianceSlot?,
    ) {
        if (slot == null || slot.deviceId.isBlank()) {
            prefs.remove(idKey)
            prefs.remove(zoneKey)
            prefs.remove(aliasKey)
        } else {
            prefs[idKey] = slot.deviceId
            prefs[zoneKey] = slot.zone
            prefs[aliasKey] = slot.alias
        }
    }
}

data class StoredCredentials(
    val pat: String,
    val country: String,
    val clientId: String,
    val slots: SlotMap,
    val devices: List<ThinqDevice>,
)
