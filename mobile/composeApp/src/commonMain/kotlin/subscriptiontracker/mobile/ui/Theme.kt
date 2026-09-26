package subscriptiontracker.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The desktop app's colours: warm greys, white cards and one blue accent. */
object AppColors {
    val Accent = Color(0xFF245BA8)
    val Warning = Color(0xFFC07A12)
    val WarningText = Color(0xFF6B4200)
    val WarningBackground = Color(0xFFFFF4E0)
    val WarningBorder = Color(0xFFEFC98A)
    val Danger = Color(0xFFA3261A)
    val Saved = Color(0xFF1F6B3F)
    val Track = Color(0xFFEFECE4)
}

private val Light = lightColorScheme(
    primary = AppColors.Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EFF9),
    onPrimaryContainer = Color(0xFF123A70),
    secondaryContainer = Color(0xFFE8EFF9),
    onSecondaryContainer = Color(0xFF123A70),
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF1C1F23),
    surface = Color(0xFFFAFAF8),
    onSurface = Color(0xFF1C1F23),
    surfaceVariant = Color(0xFFF1F0EB),
    onSurfaceVariant = Color(0xFF5B6068),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF1F0EB),
    surfaceContainerHigh = Color(0xFFECEAE3),
    surfaceContainerHighest = Color(0xFFE6E4DC),
    outline = Color(0xFFC9C6BC),
    outlineVariant = Color(0xFFE3E1DA),
    error = AppColors.Danger,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9DBEF0),
    onPrimary = Color(0xFF0B2E5C),
    primaryContainer = Color(0xFF1C467F),
    onPrimaryContainer = Color(0xFFD7E3F8),
    secondaryContainer = Color(0xFF1C467F),
    onSecondaryContainer = Color(0xFFD7E3F8),
    background = Color(0xFF16181B),
    surface = Color(0xFF16181B),
    surfaceVariant = Color(0xFF2A2C30),
    onSurfaceVariant = Color(0xFFB9BDC4),
    surfaceContainerLowest = Color(0xFF1F2124),
    surfaceContainerLow = Color(0xFF1F2124),
    surfaceContainer = Color(0xFF232529),
    outlineVariant = Color(0xFF34373C),
    error = Color(0xFFF2A69C),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** Colours for warnings and savings that read well in light and dark mode. */
object StatusColors {
    val danger: Color @Composable get() = MaterialTheme.colorScheme.error
    val saved: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF8FD1A8) else AppColors.Saved
    val warning: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF0C27A) else AppColors.WarningText
    val warningBar: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF0C27A) else AppColors.Warning
    val warningBackground: Color @Composable get() =
        if (isSystemInDarkTheme()) Color(0xFF3A2E17) else AppColors.WarningBackground
    val track: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF34373C) else AppColors.Track
}
