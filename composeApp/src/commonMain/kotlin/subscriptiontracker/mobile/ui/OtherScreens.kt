@file:OptIn(ExperimentalMaterial3Api::class)

package subscriptiontracker.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import subscriptiontracker.mobile.data.CsvImporter
import subscriptiontracker.mobile.data.Format
import subscriptiontracker.mobile.data.Money
import subscriptiontracker.mobile.data.Subscription
import subscriptiontracker.mobile.data.SubscriptionManager
import subscriptiontracker.mobile.data.SubscriptionManager.BudgetStatus

/** A scrolling screen with the usual side margins and room above the bottom bar. */
@Composable
private fun ScreenList(padding: PaddingValues, content: LazyListScope.() -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

private val RANGES = listOf(7, 14, 30, 90)

/** Why a typed amount can't be used, or null if it can. */
private fun amountProblem(text: String): String? = try {
    CsvImporter.parseAmount(text)
    null
} catch (e: IllegalArgumentException) {
    "Please enter an amount, e.g. 199.00."
}

/** The "Upcoming" tab: payments due in the next few days, and free trials. */
@Composable
fun UpcomingScreen(state: AppState, padding: PaddingValues) {
    state.version
    val manager = state.manager
    val format = state.format
    val today = state.today
    var days by rememberSaveable { mutableStateOf(30) }
    var cancelling by remember { mutableStateOf<Subscription?>(null) }
    var paying by remember { mutableStateOf<Subscription?>(null) }

    val payments = manager.paymentsWithin(today, days)
    // Each subscription's next payment, when it's within a week, can be marked as paid.
    val payable = payments.filter { it.date == it.subscription.nextPayment && it.date <= today.plus(7, DateTimeUnit.DAY) }.toSet()
    val trials = manager.freeTrials

    ScreenList(padding) {
        item(key = "range") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RANGES.forEach { d ->
                    FilterChip(selected = days == d, onClick = { days = d }, label = { Text("$d days") })
                }
            }
        }
        item(key = "total") {
            SummaryCard(
                caption = "Due in the next $days days",
                value = format.money(payments.sumOf { manager.homeCost(it.subscription) }),
                detail = if (payments.isEmpty()) "Nothing due" else
                    "${payments.size} payment${if (payments.size == 1) "" else "s"}",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "payments-title") { SectionTitle("Payments") }
        if (payments.isEmpty()) item(key = "no-payments") { EmptyNote("Nothing is due in the next $days days.") }
        items(payments, key = { "p-${it.subscription.id}-${it.date}" }) { p ->
            AppCard(Modifier.fillMaxWidth().clickable { state.editing = AppState.Editing(p.subscription) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(76.dp)) {
                        Text(Format.shortDate(p.date), fontWeight = FontWeight.Bold)
                        Text(Format.dueIn(p.date, today), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.subscription.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false))
                            if (p.subscription.freeTrial) {
                                Spacer(Modifier.width(8.dp))
                                TrialBadge()
                            }
                        }
                        Text(p.subscription.category, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(format.moneyIn(p.subscription.currency, p.subscription.cost), fontWeight = FontWeight.SemiBold)
                        if (p in payable) {
                            TextButton(onClick = { paying = p.subscription }) { Text("Paid…") }
                        }
                    }
                }
            }
        }
        item(key = "trials-title") { SectionTitle("Free trials") }
        if (trials.isEmpty()) {
            item(key = "no-trials") {
                EmptyNote("No free trials. Turn on \"Free trial\" when adding one to be reminded before it ends.")
            }
        }
        items(trials, key = { "t-${it.id}" }) { trial ->
            val soon = trial.nextPayment <= today.plus(7, DateTimeUnit.DAY)
            AppCard(Modifier.fillMaxWidth()) {
                Text(trial.name, fontWeight = FontWeight.Bold)
                Text(
                    "Ends ${Format.date(trial.nextPayment)} (${Format.dueIn(trial.nextPayment, today)}), " +
                        "then ${format.moneyIn(trial.currency, trial.cost)} ${trial.cycle.per}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (soon) StatusColors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        trial.freeTrial = false
                        state.changed("${trial.name} is no longer a trial. First payment: ${Format.date(trial.nextPayment)}.")
                    }) { Text("Keep it (I'll pay)") }
                    TextButton(onClick = { cancelling = trial }) { Text("Cancel trial", color = StatusColors.danger) }
                }
            }
        }
    }

    paying?.let { sub ->
        TextInputDialog(
            title = "${sub.name}: paid",
            explanation = "What did you pay on ${Format.date(sub.nextPayment)}? It goes into your spending history, " +
                "and the next payment moves on.",
            initial = Money.toPlainString(sub.cost),
            number = true,
            onDismiss = { paying = null },
            check = { amountProblem(it) },
            onDone = { text ->
                val paid = sub.markPaid(CsvImporter.parseAmount(text))
                state.changed("Recorded ${format.moneyIn(sub.currency, paid.amount)} for ${sub.name}.")
            },
        )
    }

    cancelling?.let { trial ->
        ConfirmDialog(
            title = "Cancel the ${trial.name} trial?",
            text = "Remember to cancel it with the provider too, before ${Format.date(trial.nextPayment)}.",
            confirm = "Cancel trial",
            danger = true,
            onConfirm = {
                trial.cancel(today)
                state.changed("Cancelled the ${trial.name} free trial.")
            },
            onDismiss = { cancelling = null },
        )
    }
}

/** The "Spending" tab: totals, the monthly budget, spending per category and the price history. */
@Composable
fun SpendingScreen(state: AppState, padding: PaddingValues) {
    state.version
    val manager = state.manager
    val format = state.format
    val today = state.today
    var editingBudget by remember { mutableStateOf(false) }
    var editingRate by remember { mutableStateOf<String?>(null) }
    var correcting by remember { mutableStateOf<SubscriptionManager.Spent?>(null) }

    val monthly = manager.monthlyTotal
    val budget = manager.monthlyBudget
    val changed = manager.monthlyPriceChangeSince(today.minus(1, DateTimeUnit.YEAR))
    val categories = manager.monthlyByCategory
    val history = manager.priceChanges
    val months = manager.spentByMonth(today, 12)
    val recent = manager.spent.take(10)
    val currencies = (manager.rates.keys + manager.currenciesWithoutRate).sorted()

    ScreenList(padding) {
        item(key = "totals") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("Per month", format.money(monthly), Modifier.weight(1f))
                SummaryCard("Per year", format.money(manager.yearlyTotal), Modifier.weight(1f))
            }
        }
        item(key = "budget") {
            val color = when (manager.budgetStatus) {
                BudgetStatus.OVER -> StatusColors.danger
                BudgetStatus.NEAR -> StatusColors.warningBar
                else -> MaterialTheme.colorScheme.primary
            }
            SummaryCard(
                caption = "Monthly budget",
                value = budget?.let { format.money(it) } ?: "Not set",
                valueColor = if (budget == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                detail = if (budget == null) "Set one to see when you're spending too much" else format.budgetLine(monthly, budget),
                detailColor = when (manager.budgetStatus) {
                    BudgetStatus.OVER -> StatusColors.danger
                    BudgetStatus.NEAR -> StatusColors.warning
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.fillMaxWidth(),
                action = { TextButton(onClick = { editingBudget = true }) { Text(if (budget == null) "Set" else "Change") } },
                extra = if (budget == null) null else {
                    {
                        Spacer(Modifier.height(8.dp))
                        Bar(monthly.toFloat() / budget.toFloat(), color)
                    }
                },
            )
        }
        item(key = "rises") {
            SummaryCard(
                caption = "Price changes, last 12 months",
                value = format.signedMoney(changed) + " a month",
                valueColor = when {
                    changed > 0 -> StatusColors.danger
                    changed < 0 -> StatusColors.saved
                    else -> MaterialTheme.colorScheme.onSurface
                },
                detail = if (changed == 0L) "No change to what you pay" else format.signedMoney(changed * 12) + " a year",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "categories") {
            AppCard(Modifier.fillMaxWidth()) {
                Text("Per month by category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (categories.isEmpty()) EmptyNote("Add subscriptions to see where your money goes.")
                categories.forEach { (name, amount) ->
                    val share = if (monthly == 0L) 0f else amount.toFloat() / monthly.toFloat()
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row {
                            Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${format.money(amount)} · ${(share * 100).toInt()}%",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Bar(share, MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item(key = "spent") {
            AppCard(Modifier.fillMaxWidth()) {
                SpendingChart(months, budget, format, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text("Payments are counted at the list price when their date passes. Use \"Paid…\" on the " +
                    "Upcoming tab, or tap a payment below, to record what you actually paid.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "recent-title") { SectionTitle("Recent payments") }
        if (recent.isEmpty()) {
            item(key = "no-recent") { EmptyNote("No payments yet. They're recorded as their dates pass.") }
        } else {
            item(key = "recent") {
                AppCard(Modifier.fillMaxWidth()) {
                    recent.forEachIndexed { i, entry ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Row(Modifier.fillMaxWidth().clickable { correcting = entry }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.subscription.name, fontWeight = FontWeight.Bold)
                                Text(Format.date(entry.paid.date) + if (entry.paid.confirmed) "" else " · list price",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(format.moneyIn(entry.subscription.currency, entry.paid.amount))
                        }
                    }
                }
            }
        }
        if (currencies.isNotEmpty()) {
            item(key = "rates") {
                AppCard(Modifier.fillMaxWidth()) {
                    Text("Exchange rates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    currencies.forEach { code ->
                        val rate = manager.rates[code]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (rate == null) "1 $code: no rate yet, counted 1 to 1"
                                else "1 $code = ${format.moneyRate(rate)}",
                                color = if (rate == null) StatusColors.warning else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { editingRate = code }) { Text(if (rate == null) "Set" else "Change") }
                        }
                    }
                }
            }
        }
        item(key = "history-title") { SectionTitle("Price history") }
        if (history.isEmpty()) {
            item(key = "no-history") {
                EmptyNote("No price changes yet. Change a subscription's cost and the change is kept here.")
            }
        } else {
            item(key = "history") {
                AppCard(Modifier.fillMaxWidth()) {
                    history.forEachIndexed { i, entry ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row {
                            Text(entry.subscription.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(Format.date(entry.change.date), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(format.describe(entry.change, entry.subscription.currency), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    editingRate?.let { code ->
        TextInputDialog(
            title = "Exchange rate for $code",
            explanation = "How much one $code is in your currency. Update it now and then; totals and the budget use it.",
            initial = manager.rates[code]?.let { Money.rateToString(it) } ?: "",
            number = true,
            onDismiss = { editingRate = null },
            check = { text ->
                val micro = try { Money.parseScaled(text.replace(',', '.'), 6) } catch (e: NumberFormatException) { 0L }
                if (micro > 0) null else "Please enter a rate, e.g. 18.25."
            },
            onDone = { text ->
                manager.setRate(code, Money.parseScaled(text.replace(',', '.'), 6))
                state.changed("1 $code is now ${state.format.moneyRate(manager.rates.getValue(code))}.")
            },
        )
    }
    correcting?.let { entry ->
        TextInputDialog(
            title = "${entry.subscription.name}, ${Format.date(entry.paid.date)}",
            explanation = "What did you actually pay?",
            initial = Money.toPlainString(entry.paid.amount),
            number = true,
            onDismiss = { correcting = null },
            check = { amountProblem(it) },
            onDone = { text ->
                entry.subscription.correctPayment(entry.paid.date, CsvImporter.parseAmount(text))
                state.changed("Payment updated.")
            },
        )
    }

    if (editingBudget) {
        TextInputDialog(
            title = "Monthly budget",
            explanation = "How much you want to spend on subscriptions each month. Leave it empty (or 0) to remove it.",
            initial = budget?.let { Money.toPlainString(it) } ?: "",
            number = true,
            onDismiss = { editingBudget = false },
            check = { text ->
                if (text.isEmpty()) null else try {
                    CsvImporter.parseAmount(text)
                    null
                } catch (e: IllegalArgumentException) {
                    "Please enter an amount, e.g. 500."
                }
            },
            onDone = { text ->
                manager.monthlyBudget = if (text.isEmpty()) null else CsvImporter.parseAmount(text)
                state.changed(manager.monthlyBudget?.let { "Monthly budget set to ${state.format.money(it)}." }
                    ?: "Monthly budget removed.")
            },
        )
    }
}

/** The "Cancelled" tab: what cancelling has saved, and the cancelled subscriptions. */
@Composable
fun CancelledScreen(state: AppState, padding: PaddingValues) {
    state.version
    val manager = state.manager
    val format = state.format
    val today = state.today
    var removing by remember { mutableStateOf<Subscription?>(null) }
    val cancelled = manager.cancelled

    ScreenList(padding) {
        item(key = "saved") {
            SummaryCard(
                caption = "Saved so far",
                value = format.money(manager.savedSoFar(today)),
                valueColor = StatusColors.saved,
                detail = "Payments you didn't have to make",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "rates") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("Saving per month", format.money(manager.cancelledMonthlyTotal), Modifier.weight(1f))
                SummaryCard("Saving per year", format.money(cancelled.sumOf { manager.homeYearly(it) }), Modifier.weight(1f))
            }
        }
        item(key = "title") { SectionTitle("Cancelled") }
        if (cancelled.isEmpty()) {
            item(key = "empty") {
                EmptyNote("Nothing cancelled yet. Cancel a subscription and what it saves you shows up here.")
            }
        }
        items(cancelled, key = { it.id }) { sub ->
            AppCard(Modifier.fillMaxWidth()) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(sub.name, fontWeight = FontWeight.Bold)
                        Text("${sub.category} · cancelled ${Format.date(sub.cancelledOn!!)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(format.money(manager.toHome(sub.currency, sub.savedSoFar(today))), fontWeight = FontWeight.SemiBold, color = StatusColors.saved)
                        Text("was ${format.moneyIn(sub.currency, sub.monthlyCost)} a month", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        sub.reactivate(today)
                        state.changed("Restored ${sub.name}. Next payment: ${Format.date(sub.nextPayment)}.")
                    }) { Text("Restore") }
                    DangerButton("Remove", onClick = { removing = sub })
                }
            }
        }
    }

    removing?.let { sub ->
        ConfirmDialog(
            title = "Remove ${sub.name}?",
            text = "It's deleted for good, and what it saved will no longer be counted.",
            confirm = "Remove",
            danger = true,
            onConfirm = {
                manager.remove(sub.id)
                state.changed("Removed ${sub.name}.")
            },
            onDismiss = { removing = null },
        )
    }
}
