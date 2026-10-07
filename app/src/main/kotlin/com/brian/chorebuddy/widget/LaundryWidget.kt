package com.brian.chorebuddy.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.unit.ColorProvider
import com.brian.chorebuddy.MainActivity
import com.brian.chorebuddy.data.LaundryRepository
import com.brian.chorebuddy.data.LaundrySnapshot
import com.brian.chorebuddy.data.projected
import com.brian.chorebuddy.data.describeCycle
import com.brian.chorebuddy.ui.theme.AppliancePalette
import com.brian.chorebuddy.work.RefreshScheduler

class LaundryWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = LaundryRepository.get(context).load().projected(System.currentTimeMillis())
        val openApp = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        provideContent {
            GlanceTheme {
                WidgetContent(context = context, snapshot = snapshot, openApp = openApp)
            }
        }
    }
}

@Composable
private fun WidgetContent(
    context: Context,
    snapshot: LaundrySnapshot,
    openApp: androidx.glance.action.Action,
) {
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val widthPx = (size.width.value * density).toInt().coerceAtLeast(1)
    val heightPx = (size.height.value * density).toInt().coerceAtLeast(1)
    val bitmap = DonutBitmapRenderer.render(snapshot, widthPx, heightPx)
    val description = snapshot.cycles().joinToString(". ") { describeCycle(it) }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(ColorProvider(Color(AppliancePalette.BACKGROUND)))
            .clickable(openApp)
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = description,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize(),
        )
    }
}

class LaundryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LaundryWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.catchUp(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshScheduler.reschedule(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RefreshScheduler.reschedule(context)
    }
}
