package com.brian.chorebuddy

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.brian.chorebuddy.work.RefreshScheduler

class ChoreBuddyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RefreshScheduler.cancelLegacy(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                RefreshScheduler.catchUp(this@ChoreBuddyApp)
            }

            override fun onStop(owner: LifecycleOwner) {
                RefreshScheduler.reschedule(this@ChoreBuddyApp)
            }
        })
        RefreshScheduler.catchUp(this)
    }
}
