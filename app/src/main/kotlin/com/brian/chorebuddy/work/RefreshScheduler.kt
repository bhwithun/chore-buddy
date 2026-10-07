package com.brian.chorebuddy.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkManager
import com.brian.chorebuddy.data.LaundryRepository
import com.brian.chorebuddy.data.countdownBaseline
import com.brian.chorebuddy.data.isUnsettledTimer
import com.brian.chorebuddy.widget.LaundryWidgetReceiver
import com.brian.chorebuddy.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZonedDateTime

object RefreshScheduler {
    private const val LEGACY_WORK = "chore_buddy_laundry_refresh"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private var mirroredEnergy = false

    fun cancelLegacy(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK)
    }

    /** Set the next alarm from the last fetch. Refresh first if that tick is already due. */
    fun catchUp(context: Context) {
        val app = context.applicationContext
        scope.launch { schedule(app, refreshIfDue = true) }
    }

    /** Set the next alarm without fetching. Use this right after a fetch. */
    fun reschedule(context: Context) {
        val app = context.applicationContext
        scope.launch { schedule(app, refreshIfDue = false) }
    }

    suspend fun schedule(context: Context, refreshIfDue: Boolean) {
        val app = context.applicationContext
        gate.withLock {
            val repo = LaundryRepository.get(app)
            val pat = repo.credentials.first().pat
            if (pat.isBlank() || !isListening(app)) {
                cancelAlarm(app)
                return
            }
            var snapshot = repo.load()
            var now = ZonedDateTime.now()
            var remains = runningRemains(snapshot)
            var unsettled = hasUnsettledTimer(snapshot)
            var lastFetch = lastFetchOf(snapshot, now)
            if (refreshIfDue && RefreshPlan.fetchIsDue(now, lastFetch, remains, unsettled)) {
                try {
                    repo.refresh()
                    WidgetUpdater.updateAll(app)
                    snapshot = repo.load()
                } catch (_: Exception) {
                    // A failed call still has to move the next check forward.
                }
                now = ZonedDateTime.now()
                remains = runningRemains(snapshot)
                unsettled = hasUnsettledTimer(snapshot)
                lastFetch = lastFetchOf(snapshot, now)
            }
            val beforeEnergy = energyStamp(snapshot)
            repo.ensureEnergy()
            val refreshed = repo.load()
            if (energyStamp(refreshed) != beforeEnergy || !mirroredEnergy) {
                WidgetUpdater.updateAll(app)
                mirroredEnergy = true
            }
            val wake = RefreshPlan.nextWake(
                ZonedDateTime.now(),
                lastFetchOf(refreshed, ZonedDateTime.now()),
                runningRemains(refreshed),
                isListening(app),
                hasUnsettledTimer(refreshed),
            )
            if (wake == null) cancelAlarm(app) else setAlarm(app, wake)
        }
    }

    private fun energyStamp(snapshot: com.brian.chorebuddy.data.LaundrySnapshot): String =
        snapshot.energyEpochDay.toString() + snapshot.cycles().joinToString("|") {
            "${it.lastMonthWh},${it.thisMonthWh},${it.thisYearWh?.sum()},${it.priorYearWh?.sum()}"
        }

    private fun runningRemains(snapshot: com.brian.chorebuddy.data.LaundrySnapshot): List<Int?> =
        snapshot.cycles().filter { it.running }.map { countdownBaseline(it) }

    private fun hasUnsettledTimer(snapshot: com.brian.chorebuddy.data.LaundrySnapshot): Boolean =
        snapshot.cycles().any { cycle ->
            cycle.running &&
                isUnsettledTimer(cycle.state) &&
                (cycle.remainMinutes == null || cycle.remainMinutes == 0)
        }

    private fun lastFetchOf(
        snapshot: com.brian.chorebuddy.data.LaundrySnapshot,
        now: ZonedDateTime,
    ): ZonedDateTime? = snapshot.fetchedAtEpochMs.takeIf { it > 0L }?.let { millis ->
        Instant.ofEpochMilli(millis).atZone(now.zone)
    }

    fun isListening(context: Context): Boolean = isAppOpen() || isWidgetPlaced(context)

    fun isAppOpen(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    fun isWidgetPlaced(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, LaundryWidgetReceiver::class.java)
        return manager.getAppWidgetIds(component).isNotEmpty()
    }

    private fun setAlarm(context: Context, wake: Wake) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context, wake.action)
        val triggerAtMillis = wake.at.toInstant().toEpochMilli()
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntent(context, WakeAction.FETCH))
    }

    private fun pendingIntent(context: Context, action: WakeAction): PendingIntent {
        val intent = Intent(context, RefreshAlarmReceiver::class.java).apply {
            putExtra(EXTRA_ACTION, action.name)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    const val EXTRA_ACTION = "refresh_action"
}
