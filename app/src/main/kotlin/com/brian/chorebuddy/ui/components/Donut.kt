package com.brian.chorebuddy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brian.chorebuddy.data.ApplianceCycle
import com.brian.chorebuddy.data.centerText
import com.brian.chorebuddy.data.donutSweepDegrees
import com.brian.chorebuddy.ui.theme.AppliancePalette

@Composable
fun ApplianceDonut(
    cycle: ApplianceCycle,
    modifier: Modifier = Modifier,
) {
    val accent = Color(AppliancePalette.accent(cycle.role))
    val active = cycle.running
    val labelColor = if (active) accent else Color(AppliancePalette.IDLE_TEXT)
    val timeColor = if (active) Color(AppliancePalette.ACTIVE_TEXT) else Color(AppliancePalette.IDLE_TEXT)

    val titleStyle = MaterialTheme.typography.titleSmall
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = cycle.role.title,
            color = labelColor,
            style = titleStyle.copy(fontSize = titleStyle.fontSize * 0.8f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                val stroke = size.minDimension * 0.10f
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                drawArc(
                    color = Color(AppliancePalette.TRACK),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                val sweep = donutSweepDegrees(cycle.progress, cycle.running)
                if (sweep > 0f) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(
                            width = stroke,
                            cap = if (sweep >= 359f) StrokeCap.Butt else StrokeCap.Round,
                        ),
                    )
                }
            }
            Text(
                text = centerText(cycle),
                color = timeColor,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
