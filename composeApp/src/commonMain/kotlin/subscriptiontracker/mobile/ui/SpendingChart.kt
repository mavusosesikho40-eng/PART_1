package subscriptiontracker.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import subscriptiontracker.mobile.data.ChartScale
import subscriptiontracker.mobile.data.Format
import subscriptiontracker.mobile.data.SubscriptionManager

/** Bars for months within the budget, and for months over it (always with a written note too). */
private val BarColor = AppColors.Green
private val OverColor = AppColors.Orange

/**
 * What was actually spent each month, as columns, with the monthly budget
 * as a reference line. Tap or hover a month to read it; "Table" shows every
 * month as text. Months over the budget are orange and are also named
 * under the chart, so colour is never the only sign.
 */
@Composable
fun SpendingChart(months: List<SubscriptionManager.Month>, budget: Long?, format: Format, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf<Int?>(null) }
    var asTable by rememberSaveable { mutableStateOf(false) }
    val over = if (budget == null) emptyList() else months.filter { it.total > budget }
    val total = months.sumOf { it.total }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Spent each month", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(format.money(total), style = MaterialTheme.typography.headlineMedium)
                Text("in the last ${months.size} months", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { asTable = !asTable }) { Text(if (asTable) "Chart" else "Table") }
        }
        Spacer(Modifier.height(12.dp))
        if (asTable) {
            MonthTable(months, budget, format)
        } else {
            // The readout: values lead, the month follows.
            val pick = selected?.let { months.getOrNull(it) }
            Text(
                if (pick == null) "Tap a month to see it" else
                    format.money(pick.total) + "  ·  " + Format.monthName(pick.start) +
                        (if (budget == null) "" else if (pick.total > budget) "  ·  ${format.money(pick.total - budget)} over budget"
                        else "  ·  ${format.money(budget - pick.total)} under budget"),
                style = if (pick == null) MaterialTheme.typography.bodySmall
                else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (pick == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Columns(
                months, budget, format, selected,
                onSelect = { selected = it },
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
        }
        if (over.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusColors.warning, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Over budget in " + over.joinToString(", ") { Format.monthShort(it.start) },
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusColors.warning,
                )
            }
        }
    }
}

/** The columns themselves, drawn on a canvas. */
@Composable
private fun Columns(
    months: List<SubscriptionManager.Month>,
    budget: Long?,
    format: Format,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val axisStyle = TextStyle(fontSize = 11.sp, color = muted)
    val ticks = ChartScale.ticks(maxOf(months.maxOfOrNull { it.total } ?: 0L, budget ?: 0L))
    val top = ticks.last().toFloat()
    val description = "Spent each month: " + months.joinToString("; ") { "${Format.monthName(it.start)} ${format.money(it.total)}" } +
        (budget?.let { ". Budget ${format.money(it)} a month." } ?: "")

    // The plot's left edge sits after the widest axis label.
    val axisWidthPx = ticks.maxOf { measurer.measure(format.moneyWhole(it), axisStyle).size.width }.toFloat()

    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(months.size, axisWidthPx) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val x = event.changes.firstOrNull()?.position?.x ?: continue
                        if (event.type == PointerEventType.Exit) {
                            onSelect(null)
                            continue
                        }
                        val left = axisWidthPx + 8.dp.toPx()
                        val slot = (size.width - left) / months.size
                        val i = ((x - left) / slot).toInt()
                        onSelect(if (x >= left && i in months.indices) i else null)
                    }
                }
            },
    ) {
        val left = axisWidthPx + 8.dp.toPx()
        val bottomLabels = 18.dp.toPx()
        val plotTop = 14.dp.toPx()
        val plotBottom = size.height - bottomLabels
        val plotHeight = plotBottom - plotTop
        fun yOf(cents: Long) = plotBottom - plotHeight * (cents.toFloat() / top)

        // Hairline gridlines and their labels.
        for (tick in ticks) {
            val y = yOf(tick)
            drawLine(grid, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            val label = measurer.measure(format.moneyWhole(tick), axisStyle)
            drawText(label, topLeft = Offset(axisWidthPx - label.size.width, y - label.size.height / 2f))
        }

        val slot = (size.width - left) / months.size
        val barWidth = minOf(24.dp.toPx(), slot * 0.62f)
        val radius = 4.dp.toPx()
        val short = slot < 28.dp.toPx()
        months.forEachIndexed { i, month ->
            val cx = left + slot * (i + 0.5f)
            if (month.total > 0) {
                val yTop = yOf(month.total)
                val color = if (budget != null && month.total > budget) OverColor else BarColor
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = cx - barWidth / 2f, top = yTop, right = cx + barWidth / 2f, bottom = plotBottom,
                            topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                            bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                        ),
                    )
                }
                // The picked month stands out; the others step back.
                drawPath(path, color, alpha = if (selected == null || selected == i) 1f else 0.4f)
            }
            // Month names: "Sep", or "S" when the columns are narrow.
            val name = Format.monthShort(month.start).let { if (short) it.take(1) else it }
            val label = measurer.measure(name, axisStyle.copy(textAlign = TextAlign.Center))
            drawText(label, topLeft = Offset(cx - label.size.width / 2f, plotBottom + 4.dp.toPx()))
        }

        // The latest month's value, on its column's cap.
        months.lastOrNull()?.takeIf { it.total > 0 }?.let { last ->
            val label = measurer.measure(format.moneyWhole(last.total), axisStyle.copy(color = ink, fontWeight = FontWeight.Bold))
            val cx = left + slot * (months.size - 0.5f)
            val x = (cx - label.size.width / 2f).coerceAtMost(size.width - label.size.width)
            drawText(label, topLeft = Offset(x, (yOf(last.total) - label.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
        }

        // The budget, as a reference line with its name at the left.
        if (budget != null) {
            val y = yOf(budget)
            drawLine(ink, Offset(left, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
            val label = measurer.measure("Budget ${format.moneyWhole(budget)}", axisStyle.copy(color = ink))
            drawText(label, topLeft = Offset(left + 4.dp.toPx(), y - label.size.height - 2.dp.toPx()))
        }
    }
}

/** Every month as text: the table view, so no value is only in the chart. */
@Composable
private fun MonthTable(months: List<SubscriptionManager.Month>, budget: Long?, format: Format) {
    Column {
        months.asReversed().forEachIndexed { i, month ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Format.monthName(month.start), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (budget != null && month.total > budget) {
                    Icon(Icons.Filled.Warning, contentDescription = "Over budget", tint = StatusColors.warning,
                        modifier = Modifier.size(16.dp).padding(end = 2.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(format.money(month.total), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
