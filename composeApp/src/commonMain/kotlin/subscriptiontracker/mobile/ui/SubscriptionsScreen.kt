@file:OptIn(ExperimentalMaterial3Api::class)

package subscriptiontracker.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import subscriptiontracker.mobile.data.BillingCycle
import subscriptiontracker.mobile.data.Format
import subscriptiontracker.mobile.data.ListView
import subscriptiontracker.mobile.data.Subscription
import subscriptiontracker.mobile.data.SubscriptionManager.BudgetStatus

/**
 * The "Subscriptions" tab: a reminder for trials ending soon, what you
 * spend, then every subscription with search, a billing-cycle filter and
 * sorting. Tapping one opens it for editing.
 */
@Composable
fun SubscriptionsScreen(state: AppState, padding: PaddingValues, listState: LazyListState = rememberLazyListState()) {
    state.version // Redraw after every change.
    val manager = state.manager
    val format = state.format
    val today = state.today

    var search by rememberSaveable { mutableStateOf("") }
    var cycleIndex by rememberSaveable { mutableStateOf(-1) }
    var sortIndex by rememberSaveable { mutableStateOf(0) }
    val dismissedTrials = remember { mutableStateListOf<Int>() }
    var cancelling by remember { mutableStateOf<Subscription?>(null) }

    val cycle = BillingCycle.entries.getOrNull(cycleIndex)
    val sort = ListView.Sort.entries[sortIndex]
    val all = manager.all
    val shown = ListView.sort(all.filter { ListView.matches(it, search, cycle) }, sort, manager)
    val trials = manager.trialsEndingWithin(today, 7).filter { it.id !in dismissedTrials }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        trials.firstOrNull()?.let { trial ->
            item(key = "trial") {
                TrialReminder(trial, trials.size - 1, format, today,
                    onCancel = { cancelling = trial },
                    onDismiss = { dismissedTrials += trial.id })
            }
        }
        item(key = "spending") { SpendingCards(state) }
        item(key = "search") {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search name or category") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (search.isEmpty()) null else {
                    { IconButton(onClick = { search = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") } }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "filters") {
            FilterRow(cycleIndex, { cycleIndex = it }, sort, { sortIndex = it.ordinal })
        }
        if (all.isEmpty()) {
            item(key = "empty") {
                EmptyNote("No subscriptions yet. Tap Add to put in your first one, or import your list from a CSV " +
                    "file (menu at the top right).")
            }
        } else if (shown.isEmpty()) {
            item(key = "none") { EmptyNote("Nothing matches.") }
        }
        items(shown, key = { it.id }) { sub ->
            SubscriptionRow(sub, format, today, home = manager.homeCost(sub), onClick = { state.editing = AppState.Editing(sub) })
        }
        if (all.isNotEmpty()) {
            item(key = "count") {
                Text(
                    if (shown.size == all.size) "${all.size} subscription${if (all.size == 1) "" else "s"}"
                    else "${shown.size} of ${all.size} subscriptions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    cancelling?.let { sub ->
        ConfirmDialog(
            title = "Cancel ${sub.name}?",
            text = "It moves to your cancelled list. Remember to cancel it with the provider too" +
                (if (sub.freeTrial) ", before ${Format.date(sub.nextPayment)}." else "."),
            confirm = "Cancel it",
            danger = true,
            onConfirm = {
                sub.cancel(today)
                state.changed("Cancelled ${sub.name}.")
            },
            onDismiss = { cancelling = null },
        )
    }
}

@Composable
private fun TrialReminder(
    trial: Subscription,
    more: Int,
    format: Format,
    today: kotlinx.datetime.LocalDate,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = StatusColors.warningBackground),
        border = BorderStroke(1.dp, AppColors.WarningBorder),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                "${trial.name} free trial ends ${Format.dueIn(trial.nextPayment, today)}",
                fontWeight = FontWeight.Bold,
                color = StatusColors.warning,
            )
            Text(
                "${Format.date(trial.nextPayment)}. You'll be charged ${format.moneyIn(trial.currency, trial.cost)} unless you cancel." +
                    if (more > 0) " ($more more ending this week.)" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = StatusColors.warning,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Dismiss", color = StatusColors.warning) }
                TextButton(onClick = onCancel) { Text("Cancel it", color = StatusColors.warning, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Per month (against the budget), due in the next 7 days, and per year. */
@Composable
private fun SpendingCards(state: AppState) {
    val manager = state.manager
    val format = state.format
    val monthly = manager.monthlyTotal
    val budget = manager.monthlyBudget

    val barColor = when (manager.budgetStatus) {
        BudgetStatus.OVER -> StatusColors.danger
        BudgetStatus.NEAR -> StatusColors.warningBar
        else -> MaterialTheme.colorScheme.primary
    }
    val detailColor = when (manager.budgetStatus) {
        BudgetStatus.OVER -> StatusColors.danger
        BudgetStatus.NEAR -> StatusColors.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column {
    SummaryCard(
        caption = "Per month",
        value = format.money(monthly),
        modifier = Modifier.fillMaxWidth(),
        detail = format.budgetLine(monthly, budget),
        detailColor = detailColor,
        extra = if (budget == null) null else {
            {
                Spacer(Modifier.height(8.dp))
                Bar(monthly.toFloat() / budget.toFloat(), barColor)
            }
        },
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val due = manager.upcoming(state.today, 7)
        SummaryCard(
            caption = "Next 7 days",
            value = format.money(due.sumOf { manager.homeCost(it) }),
            detail = if (due.isEmpty()) "Nothing due" else "${due.size} payment${if (due.size == 1) "" else "s"}",
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            caption = "Per year",
            value = format.money(manager.yearlyTotal),
            detail = "${manager.all.size} active",
            modifier = Modifier.weight(1f),
        )
    }
    }
}

@Composable
private fun FilterRow(
    cycleIndex: Int,
    onCycle: (Int) -> Unit,
    sort: ListView.Sort,
    onSort: (ListView.Sort) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = cycleIndex < 0, onClick = { onCycle(-1) }, label = { Text("All") })
            BillingCycle.entries.forEachIndexed { i, c ->
                FilterChip(selected = cycleIndex == i, onClick = { onCycle(if (cycleIndex == i) -1 else i) },
                    label = { Text(c.label) })
            }
        }
        var open by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort by ${sort.label}") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Text("Sort by", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                ListView.Sort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label, fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { onSort(option); open = false },
                    )
                }
            }
        }
    }
}

/** One subscription: name, category and note on the left; cost and next payment on the right. */
@Composable
fun SubscriptionRow(sub: Subscription, format: Format, today: kotlinx.datetime.LocalDate, home: Long, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sub.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    if (sub.freeTrial) {
                        Spacer(Modifier.width(8.dp))
                        TrialBadge()
                    }
                }
                Text(
                    sub.category + if (sub.hasNote) " · " + sub.note else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(format.moneyIn(sub.currency, sub.cost), fontWeight = FontWeight.SemiBold)
                if (sub.isForeign) {
                    Text("≈ " + format.money(home), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    sub.cycle.label + " · " + Format.shortDate(sub.nextPayment),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    Format.dueIn(sub.nextPayment, today),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** An outlined button in the danger colour, for Remove. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier,
        border = BorderStroke(1.dp, StatusColors.danger.copy(alpha = 0.5f))) {
        Text(text, color = StatusColors.danger)
    }
}
