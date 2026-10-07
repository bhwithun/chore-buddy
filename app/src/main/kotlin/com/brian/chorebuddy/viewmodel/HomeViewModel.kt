package com.brian.chorebuddy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brian.chorebuddy.data.LaundryRepository
import com.brian.chorebuddy.data.LaundrySnapshot
import com.brian.chorebuddy.data.projected
import com.brian.chorebuddy.widget.WidgetUpdater
import com.brian.chorebuddy.work.RefreshScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class HomeUi(
    val snapshot: LaundrySnapshot? = null,
    val refreshing: Boolean = false,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = LaundryRepository.get(app)
    private val refreshing = kotlinx.coroutines.flow.MutableStateFlow(false)

    private val clock = flow {
        while (coroutineContext.isActive) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val ui: StateFlow<HomeUi> = combine(repository.snapshot, refreshing, clock) { snapshot, busy, now ->
        HomeUi(snapshot.projected(now), busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            try {
                repository.refresh(includeEnergy = true)
                WidgetUpdater.updateAll(getApplication())
            } finally {
                RefreshScheduler.reschedule(getApplication())
                refreshing.value = false
            }
        }
    }
}
