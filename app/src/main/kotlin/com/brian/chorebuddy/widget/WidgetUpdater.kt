package com.brian.chorebuddy.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll

object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        val appContext = context.applicationContext
        LaundryWidget().updateAll(appContext)
        val manager = GlanceAppWidgetManager(appContext)
        val ids = manager.getGlanceIds(LaundryWidget::class.java)
        if (ids.isEmpty()) requestProviderUpdate(appContext)
    }

    private fun requestProviderUpdate(context: Context) {
        val component = ComponentName(context, LaundryWidgetReceiver::class.java)
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(component)
        if (ids.isEmpty()) return
        val intent = Intent(context, LaundryWidgetReceiver::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(intent)
    }
}
