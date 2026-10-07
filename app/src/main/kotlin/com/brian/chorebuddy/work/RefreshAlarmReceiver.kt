package com.brian.chorebuddy.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.brian.chorebuddy.data.LaundryRepository
import com.brian.chorebuddy.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RefreshAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (RefreshScheduler.isListening(app)) {
                    val repaint = intent?.getStringExtra(RefreshScheduler.EXTRA_ACTION) == WakeAction.REPAINT.name
                    if (repaint) {
                        WidgetUpdater.updateAll(app)
                    } else {
                        try {
                            LaundryRepository.get(app).refresh()
                            WidgetUpdater.updateAll(app)
                        } catch (_: Exception) {
                            // The scheduler still parks the next check one interval out.
                        }
                    }
                }
                RefreshScheduler.schedule(app, refreshIfDue = false)
            } finally {
                pending.finish()
            }
        }
    }
}
