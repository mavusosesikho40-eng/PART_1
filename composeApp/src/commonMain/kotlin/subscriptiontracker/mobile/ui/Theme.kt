package subscriptiontracker.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.Font
import subscriptiontracker.mobile.resources.Res
import subscriptiontracker.mobile.resources.dm_serif_display

/**
 * The look: black and white with plenty of contrast, white cards on an
 * off-white page, solid black buttons, big serif headlines, and an orange
 * and a green for accents.
 */
object AppColors {
    val Ink = Color(0xFF0B0B0B)
    val Paper = Color(0xFFF6F6F4)
    val Orange = Color(0xFFF0563A)
    val Green = Color(0xFF2E9E6E)
    val Check = Color(0xFF2EA36B)
    val Danger = Color(0xFFC2361F)
    val Saved = Color(0xFF1E7A4F)
    val WarningText = Color(0xFF9A3412)
    val WarningBackground = Color(0xFFFFF0EA)
    val WarningBorder = Color(0xFFF7B7A6)
    val Track = Color(0xFFE9E9E5)
}

private val Light = lightColorScheme(
    primary = AppColors.Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDEDEA),
    onPrimaryContainer = AppColors.Ink,
    // Selected tabs, filter chips and billing-cycle buttons: solid black.
    secondaryContainer = AppColors.Ink,
    onSecondaryContainer = Color.White,
    secondary = AppColors.Green,
    tertiary = AppColors.Orange,
    background = AppColors.Paper,
    onBackground = AppColors.Ink,
    surface = AppColors.Paper,
    onSurface = AppColors.Ink,
    surfaceVariant = Color(0xFFEDEDEA),
    onSurfaceVariant = Color(0xFF5F5F5A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F0ED),
    surfaceContainerHighest = Color(0xFFE6E6E2),
    outline = Color(0xFFB9B9B3),
    outlineVariant = Color(0xFFE3E3DF),
    error = AppColors.Danger,
)

private val Dark = darkColorScheme(
    primary = Color.White,
    onPrimary = AppColors.Ink,
    primaryContainer = Color(0xFF2A2A28),
    onPrimaryContainer = Color.White,
    secondaryContainer = Color.White,
    onSecondaryContainer = AppColors.Ink,
    secondary = Color(0xFF6FD3A4),
    tertiary = Color(0xFFFF8A70),
    background = AppColors.Ink,
    onBackground = Color(0xFFF2F2EF),
    surface = AppColors.Ink,
    onSurface = Color(0xFFF2F2EF),
    surfaceVariant = Color(0xFF242422),
    onSurfaceVariant = Color(0xFFA9A9A3),
    surfaceContainerLowest = Color(0xFF1A1A19),
    surfaceContainerLow = Color(0xFF1A1A19),
    surfaceContainer = Color(0xFF1A1A19),
    surfaceContainerHigh = Color(0xFF222221),
    surfaceContainerHighest = Color(0xFF2B2B29),
    outline = Color(0xFF5E5E59),
    outlineVariant = Color(0xFF2C2C2A),
    error = Color(0xFFFF8A75),
)

/** Squared-off corners, like the reference's cards and buttons. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** Headlines and big numbers in DM Serif Display (bundled, SIL Open Font License); the rest in the system's sans. */
@Composable
private fun appTypography(): Typography {
    val serif = FontFamily(Font(Res.font.dm_serif_display))
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = serif),
        displayMedium = base.displayMedium.copy(fontFamily = serif),
        displaySmall = base.displaySmall.copy(fontFamily = serif),
        headlineLarge = base.headlineLarge.copy(fontFamily = serif),
        headlineMedium = base.headlineMedium.copy(fontFamily = serif),
        headlineSmall = base.headlineSmall.copy(fontFamily = serif),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = appTypography(),
        shapes = AppShapes,
        content = content,
    )
}

/** Colours for warnings and savings that read well in light and dark mode. */
object StatusColors {
    val danger: Color @Composable get() = MaterialTheme.colorScheme.error
    val saved: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF6FD3A4) else AppColors.Saved
    val check: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF6FD3A4) else AppColors.Check
    val warning: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFB199) else AppColors.WarningText
    val warningBar: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF8A70) else AppColors.Orange
    val warningBackground: Color @Composable get() =
        if (isSystemInDarkTheme()) Color(0xFF3A1F18) else AppColors.WarningBackground
    val track: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF2B2B29) else AppColors.Track
}
