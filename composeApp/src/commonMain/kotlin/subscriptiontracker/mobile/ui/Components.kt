@file:OptIn(ExperimentalMaterial3Api::class)

package subscriptiontracker.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A soft blue-grey panel with round corners, the way wallet apps group things. */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

/** A caption, a big value and a line of detail, with an optional action at the top right. */
@Composable
fun SummaryCard(
    caption: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    detailColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    action: (@Composable RowScope.() -> Unit)? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AppCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(caption, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            action?.invoke(this)
        }
        Spacer(Modifier.height(4.dp))
        // One line, shrinking to fit when a big amount meets a narrow panel.
        val style = MaterialTheme.typography.headlineMedium
        BasicText(
            value,
            style = style.copy(color = valueColor),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = style.fontSize, stepSize = 1.sp),
        )
        extra?.invoke(this)
        if (detail != null) {
            Spacer(Modifier.height(6.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = detailColor)
        }
    }
}

/** A rounded bar filled to [fraction] (0 to 1). */
@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(8.dp),
        color = color,
        trackColor = StatusColors.track,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** A muted line explaining an empty list. */
@Composable
fun EmptyNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp))
}

/** "Free trial" in a small pill: orange on a page, see-through white on a card. */
@Composable
fun TrialBadge(onCard: Boolean = false) {
    Text(
        "Free trial",
        style = MaterialTheme.typography.labelSmall,
        color = if (onCard) Color.White else StatusColors.warning,
        modifier = Modifier
            .background(if (onCard) Color.White.copy(alpha = 0.22f) else StatusColors.warningBackground, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Asks before doing something that can't easily be undone. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    dismiss: String = "Back",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (danger) StatusColors.danger else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}

/**
 * Asks for one line of text. [check] returns why the text can't be used,
 * or null if it can; [onDone] is then called with it.
 */
@Composable
fun TextInputDialog(
    title: String,
    explanation: String,
    initial: String,
    onDismiss: () -> Unit,
    check: (String) -> String?,
    onDone: (String) -> Unit,
    number: Boolean = false,
) {
    var text by remember { mutableStateOf(initial) }
    var problem by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(explanation, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; problem = null },
                    singleLine = true,
                    isError = problem != null,
                    supportingText = problem?.let { { Text(it) } },
                    keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val reason = check(text.trim())
                if (reason == null) {
                    onDone(text.trim())
                    onDismiss()
                } else {
                    problem = reason
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The main action on a screen: a blue pill. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, arrow: Boolean = false) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
        if (arrow) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * One of a list of choices, with a radio button: the picked one turns
 * light blue with a blue outline.
 */
@Composable
fun ChoiceCard(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            RadioButton(selected = selected, onClick = null)
        }
    }
}

/** The pill at the bottom right of each setup step that moves on. */
@Composable
fun NextButton(onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier, text: String = "Next") {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 28.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A subscription's initial in a circle: white on its card colour in lists,
 * or [onCard], the card colour on white.
 */
@Composable
fun Monogram(name: String, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp, onCard: Boolean = false) {
    val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier.size(size).background(if (onCard) Color.White else color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = if (onCard) color else Color.White, fontWeight = FontWeight.Medium,
            fontSize = (size.value * 0.45f).sp)
    }
}

/** How much of each card shows above the next one in a [CardStack]. */
val CardPeek = 72.dp

/**
 * A wallet card: [name], [subtitle] and [price] along the top (all that
 * shows of it in a stack), and a footer that shows on the front card.
 */
@Composable
fun WalletCard(
    name: String,
    subtitle: String,
    price: String,
    color: Color,
    modifier: Modifier = Modifier,
    priceDetail: String? = null,
    trial: Boolean = false,
    footerLabel: String = "",
    footerValue: String = "",
    footerEnd: String = "",
    footerEndDetail: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.medium
    val darker = lerp(color, Color.Black, 0.28f)
    val faded = Color.White.copy(alpha = 0.82f)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(color, darker)))
            .drawBehind {
                // Two faint rings at the bottom right, like a printed card's pattern.
                val ring = Color.White.copy(alpha = 0.07f)
                drawCircle(ring, radius = size.height * 0.62f, center = Offset(size.width * 0.98f, size.height * 1.05f))
                drawCircle(ring, radius = size.height * 0.42f, center = Offset(size.width * 0.80f, size.height * 1.12f))
            }
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Open", onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Monogram(name, color, onCard = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (trial) {
                            Spacer(Modifier.width(8.dp))
                            TrialBadge(onCard = true)
                        }
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = faded, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(price, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                    if (priceDetail != null) {
                        Text(priceDetail, style = MaterialTheme.typography.bodySmall, color = faded, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    if (footerLabel.isNotEmpty()) Text(footerLabel, style = MaterialTheme.typography.labelSmall, color = faded)
                    if (footerValue.isNotEmpty()) Text(footerValue, style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    if (footerEnd.isNotEmpty()) {
                        Text(footerEnd, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    if (!footerEndDetail.isNullOrEmpty()) {
                        Text(footerEndDetail, style = MaterialTheme.typography.bodySmall, color = faded, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * Cards stacked like a wallet: each overlaps the one before, leaving its top
 * [peek] in view, and the last is shown whole. Later cards sit on top, so a
 * tap on a card's visible strip opens that card.
 */
@Composable
fun CardStack(modifier: Modifier = Modifier, peek: Dp = CardPeek, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val step = peek.roundToPx()
        val height = if (placeables.isEmpty()) 0 else step * (placeables.size - 1) + placeables.last().height
        layout(placeables.maxOfOrNull { it.width } ?: constraints.minWidth, height) {
            placeables.forEachIndexed { i, placeable -> placeable.place(0, i * step) }
        }
    }
}

/**
 * Rows in one group: each on its own panel, two points apart, with the
 * group's outer corners rounder than the inner ones.
 */
@Composable
fun <T> ListGroup(items: List<T>, modifier: Modifier = Modifier, row: @Composable (T) -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { i, item ->
            val outer = 20.dp
            val inner = 4.dp
            val top = if (i == 0) outer else inner
            val bottom = if (i == items.lastIndex) outer else inner
            Surface(
                shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) { row(item) }
        }
    }
}

/** One row in a [ListGroup]: an optional circle at the start, two lines of text, and something at the end. */
@Composable
fun GroupRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    badge: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(headline, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (badge) {
                    Spacer(Modifier.width(8.dp))
                    TrialBadge()
                }
            }
        },
        supportingContent = supporting?.let { { Text(it, color = supportingColor) } },
        leadingContent = leading,
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
    )
}
