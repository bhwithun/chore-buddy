package com.brian.chorebuddy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brian.chorebuddy.data.LaundryRepository
import com.brian.chorebuddy.data.ThinqDevice
import com.brian.chorebuddy.widget.WidgetUpdater
import com.brian.chorebuddy.work.RefreshScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUi(
    val pat: String = "",
    val country: String = "US",
    val devices: List<ThinqDevice> = emptyList(),
    val washerId: String = "",
    val dryerId: String = "",
    val dishId: String = "",
    val busy: Boolean = false,
    val message: String = "",
    val error: String = "",
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = LaundryRepository.get(app)
    private val _ui = MutableStateFlow(SettingsUi())
    val ui: StateFlow<SettingsUi> = _ui

    init {
        viewModelScope.launch {
            val creds = repository.credentials.first()
            _ui.update {
                it.copy(
                    pat = creds.pat,
                    country = creds.country,
                    devices = creds.devices,
                    washerId = creds.slots.washer?.deviceId.orEmpty(),
                    dryerId = creds.slots.dryer?.deviceId.orEmpty(),
                    dishId = creds.slots.dishwasher?.deviceId.orEmpty(),
                )
            }
        }
    }

    fun onPat(value: String) = _ui.update { it.copy(pat = value, error = "", message = "") }

    fun onCountry(value: String) = _ui.update { it.copy(country = value.take(2), error = "", message = "") }

    fun onWasher(id: String) = _ui.update { it.copy(washerId = id) }

    fun onDryer(id: String) = _ui.update { it.copy(dryerId = id) }

    fun onDish(id: String) = _ui.update { it.copy(dishId = id) }

    fun connect() {
        val current = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = "", message = "") }
            try {
                val result = repository.connect(current.pat, current.country)
                WidgetUpdater.updateAll(getApplication())
                RefreshScheduler.reschedule(getApplication())
                val linked = listOfNotNull(
                    result.slots.washer?.let { "washer" },
                    result.slots.dryer?.let { "dryer" },
                    result.slots.dishwasher?.let { "dishwasher" },
                )
                val summary = if (linked.isEmpty()) {
                    "Signed in, but no washer, dryer, or dishwasher was on this account."
                } else {
                    "Linked ${linked.joinToString(", ")}."
                }
                val extra = result.snapshot.error
                _ui.update {
                    it.copy(
                        busy = false,
                        devices = result.devices,
                        washerId = result.slots.washer?.deviceId.orEmpty(),
                        dryerId = result.slots.dryer?.deviceId.orEmpty(),
                        dishId = result.slots.dishwasher?.deviceId.orEmpty(),
                        message = summary,
                        error = extra,
                    )
                }
            } catch (error: Exception) {
                _ui.update {
                    it.copy(busy = false, error = error.message ?: "Could not reach LG.")
                }
            }
        }
    }

    fun saveSelection() {
        val current = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = "", message = "") }
            try {
                val snapshot = repository.saveSelection(current.washerId, current.dryerId, current.dishId)
                WidgetUpdater.updateAll(getApplication())
                RefreshScheduler.reschedule(getApplication())
                _ui.update {
                    it.copy(
                        busy = false,
                        message = "Saved device selection.",
                        error = snapshot.error,
                    )
                }
            } catch (error: Exception) {
                _ui.update {
                    it.copy(busy = false, error = error.message ?: "Could not save.")
                }
            }
        }
    }
}
