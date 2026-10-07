package com.brian.chorebuddy.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * The old 15-minute periodic job. Kept so an update can cancel a job that was
 * already queued. New refreshes go through [RefreshAlarmReceiver].
 */
class RefreshLaundryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        RefreshScheduler.cancelLegacy(applicationContext)
        return Result.success()
    }
}
