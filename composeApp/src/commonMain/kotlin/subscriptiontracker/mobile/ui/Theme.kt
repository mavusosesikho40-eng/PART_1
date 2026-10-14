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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The look, after a phone wallet: a light page, soft blue-grey panels with
 * round corners, pill-shaped buttons in one blue, and every subscription
 * as a colourful card.
 */
object AppColors {
    val Blue = Color(0xFF0B57D0)
    val Green = Color(0xFF2E9E6E)
    val Orange = Color(0xFFF0563A)
    val Danger = Color(0xFFB3261E)
    val Saved = Color(0xFF146C2E)
    val WarningText = Color(0xFF8C3A0F)
    val WarningBackground = Color(0xFFFFEDE5)
}

/**
 * The card colours, one per subscription, picked from its name so it keeps
 * its colour on every device. Each is dark enough for white text (at least
 * 6.4 to 1).
 */
val CardColors = listOf(
    Color(0xFF0B57D0), // blue
    Color(0xFF146C2E), // green
    Color(0xFFB3261E), // red
    Color(0xFF6750A4), // purple
    Color(0xFF00677D), // teal
    Color(0xFF9A4600), // amber
    Color(0xFFA2306A), // pink
    Color(0xFF3949AB), // indigo
    Color(0xFF38662A), // forest
    Color(0xFF3C4043), // slate
)

/** A cancelled subscription's card: grey. */
val CancelledCardColor = Color(0xFF5F6368)

/** [name]'s card colour: the same on every device, whatever its letters' case or spacing. */
fun cardColor(name: String): Color {
    var hash = 0
    for (c in name.trim().lowercase()) hash = hash * 31 + c.code
    return CardColors[(hash % CardColors.size + CardColors.size) % CardColors.size]
}

private val Light = lightColorScheme(
    primary = AppColors.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF00639B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC2E7FF),
    onSecondaryContainer = Color(0xFF001D35),
    tertiary = Color(0xFF146C2E),
    tertiaryContainer = Color(0xFFC4EED0),
    onTertiaryContainer = Color(0xFF072711),
    background = Color.White,
    onBackground = Color(0xFF1F1F1F),
    surface = Color.White,
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE1E3E1),
    onSurfaceVariant = Color(0xFF444746),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color(0xFFF0F4F9),
    surfaceContainerHigh = Color(0xFFE9EEF6),
    surfaceContainerHighest = Color(0xFFDDE3EA),
    outline = Color(0xFF747775),
    outlineVariant = Color(0xFFC4C7C5),
    error = AppColors.Danger,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFF7FCFFF),
    onSecondary = Color(0xFF003355),
    secondaryContainer = Color(0xFF004A77),
    onSecondaryContainer = Color(0xFFC2E7FF),
    tertiary = Color(0xFF6DD58C),
    tertiaryContainer = Color(0xFF0F5223),
    onTertiaryContainer = Color(0xFFC4EED0),
    background = Color(0xFF131314),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF131314),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF444746),
    onSurfaceVariant = Color(0xFFC4C7C5),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF1B1B1B),
    surfaceContainer = Color(0xFF1E1F20),
    surfaceContainerHigh = Color(0xFF282A2C),
    surfaceContainerHighest = Color(0xFF333537),
    outline = Color(0xFF8E918F),
    outlineVariant = Color(0xFF444746),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** Round corners throughout: panels 24, cards 16, small things 8. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The system's own sans-serif (Roboto on Android), with titles a touch heavier. */
private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Normal),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Colours for warnings and savings that read well in light and dark mode. */
object StatusColors {
    val danger: Color @Composable get() = MaterialTheme.colorScheme.error
    val saved: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF6DD58C) else AppColors.Saved
    val check: Color @Composable get() = MaterialTheme.colorScheme.primary
    val warning: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFB599) else AppColors.WarningText
    val warningBar: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF8A70) else AppColors.Orange
    val warningBackground: Color @Composable get() =
        if (isSystemInDarkTheme()) Color(0xFF3A2219) else AppColors.WarningBackground
    val track: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest
}
