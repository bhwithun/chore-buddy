package com.brian.chorebuddy.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brian.chorebuddy.data.ApplianceCycle
import com.brian.chorebuddy.data.detailText
import com.brian.chorebuddy.data.energyMonthLabel
import com.brian.chorebuddy.data.formatKwh
import com.brian.chorebuddy.ui.components.ApplianceDonut
import com.brian.chorebuddy.ui.theme.AppliancePalette
import com.brian.chorebuddy.viewmodel.HomeViewModel
import com.brian.chorebuddy.widget.LaundryWidgetReceiver
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snapshot = ui.snapshot
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Chore Buddy", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Washer, dryer, and dishwasher. A full ring is a finished cycle. Gray means that machine is off.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (snapshot == null) {
            CircularProgressIndicator()
            return@Column
        }

        if (snapshot.usingSample) {
            Text(
                text = "These are sample cycles. Add your LG token to show your machines.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        val cycles = snapshot.cycles()
        val today = LocalDate.now()
        val monthWidth = 40.dp
        val usageStyle = MaterialTheme.typography.bodySmall.let { style ->
            style.copy(fontSize = style.fontSize * 0.8f)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                cycles.forEach { cycle ->
                    ApplianceDonut(
                        cycle = cycle,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.width(monthWidth))
            }
            EnergyRow(
                cycles = cycles,
                month = energyMonthLabel(today.withDayOfMonth(1).minusMonths(1)),
                monthWidth = monthWidth,
                style = usageStyle,
                value = { it.lastMonthWh },
            )
            EnergyRow(
                cycles = cycles,
                month = energyMonthLabel(today),
                monthWidth = monthWidth,
                style = usageStyle,
                value = { it.thisMonthWh },
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                cycles.forEach { cycle ->
                    Text(
                        text = detailText(cycle).ifBlank { " " },
                        modifier = Modifier.weight(1f),
                        color = if (cycle.running) {
                            Color(AppliancePalette.MUTED)
                        } else {
                            Color(AppliancePalette.IDLE_TEXT)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(monthWidth))
            }
        }

        Text(
            text = updatedLabel(snapshot.fetchedAtEpochMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        if (snapshot.error.isNotBlank()) {
            Text(
                text = snapshot.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = viewModel::refresh, enabled = !ui.refreshing) {
                Text(if (ui.refreshing) "Refreshing" else "Refresh")
            }
            OutlinedButton(onClick = onOpenSettings) {
                Text("LG token")
            }
        }

        OutlinedButton(
            onClick = {
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, LaundryWidgetReceiver::class.java)
                if (manager.isRequestPinAppWidgetSupported) {
                    manager.requestPinAppWidget(component, null, null)
                }
            },
        ) {
            Text("Add home screen widget")
        }

        Text(
            text = "You can also long-press the wallpaper, choose Widgets, then Chore Buddy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EnergyRow(
    cycles: List<ApplianceCycle>,
    month: String,
    monthWidth: Dp,
    style: androidx.compose.ui.text.TextStyle,
    value: (ApplianceCycle) -> Int?,
) {
    val color = Color(AppliancePalette.MUTED)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cycles.forEach { cycle ->
            Text(
                text = formatKwh(value(cycle)),
                modifier = Modifier.weight(1f),
                color = color,
                style = style,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = month,
            modifier = Modifier.width(monthWidth),
            color = color,
            style = style,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private fun updatedLabel(epochMs: Long): String {
    if (epochMs <= 0L) return "Not updated from LG yet"
    val formatted = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMs))
    return "Updated $formatted"
}
