package com.brian.chorebuddy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.brian.chorebuddy.data.ApplianceRole

object AppliancePalette {
    const val WASH = 0xFF5B8CFF.toInt()
    const val DRY = 0xFFE6A15C.toInt()
    const val DISH = 0xFF3ECFB2.toInt()
    const val TRACK = 0xFF2C333C.toInt()
    const val IDLE_TEXT = 0xFF8B939E.toInt()
    const val ACTIVE_TEXT = 0xFFF4F7FB.toInt()
    const val BACKGROUND = 0xFF101114.toInt()
    const val MUTED = 0xFF9AA3AD.toInt()
    const val GOLD = 0xFFE1B03A.toInt()
    const val PRIOR_YEAR = 0xFF4A7EC2.toInt()
    const val AXIS = 0xFFFFFFFF.toInt()

    fun accent(role: ApplianceRole): Int = when (role) {
        ApplianceRole.WASHER -> WASH
        ApplianceRole.DRYER -> DRY
        ApplianceRole.DISHWASHER -> DISH
    }
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(AppliancePalette.WASH),
    onPrimary = Color.White,
    secondary = Color(AppliancePalette.DISH),
    tertiary = Color(AppliancePalette.DRY),
    background = Color(AppliancePalette.BACKGROUND),
    surface = Color(0xFF171A1F),
    onBackground = Color(AppliancePalette.ACTIVE_TEXT),
    onSurface = Color(AppliancePalette.ACTIVE_TEXT),
    surfaceVariant = Color(0xFF22272E),
    onSurfaceVariant = Color(AppliancePalette.MUTED),
    error = Color(0xFFE36B6B),
)

@Composable
fun ChoreBuddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
