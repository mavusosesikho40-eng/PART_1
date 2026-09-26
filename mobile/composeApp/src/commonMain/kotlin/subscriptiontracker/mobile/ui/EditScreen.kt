package subscriptiontracker.mobile.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import subscriptiontracker.mobile.data.BillingCycle
import subscriptiontracker.mobile.data.Dates
import subscriptiontracker.mobile.data.Format
import subscriptiontracker.mobile.data.Money
import subscriptiontracker.mobile.data.Subscription
import subscriptiontracker.mobile.data.SubscriptionForm

/** Closes the form with the phone's back button or gesture (Android); iPhones use the X. */
@Composable
expect fun BackHandler(enabled: Boolean, onBack: () -> Unit)

/**
 * The form for adding a subscription, or editing, cancelling or removing
 * one. What's typed is checked and saved by [SubscriptionForm].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(state: AppState, editing: Subscription?) {
    val today = state.today
    val format = state.format
    var form by remember {
        mutableStateOf(if (editing == null) SubscriptionForm(date = today) else SubscriptionForm.from(editing, state.manager))
    }
    var problem by remember { mutableStateOf<String?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val close = { state.editing = null }

    BackHandler(enabled = true, onBack = close)

    fun save() {
        problem = form.problem
        if (problem != null) return
        if (editing == null) {
            val sub = form.add(state.manager, today)
            state.changed("Added ${sub.name}.")
        } else {
            val change = form.applyTo(editing, today, state.manager)
            state.changed("Saved ${editing.name}." + if (change == null) "" else " Price change recorded.")
        }
        close()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing == null) "Add subscription" else "Edit subscription") },
                navigationIcon = { IconButton(onClick = close) { Icon(Icons.Filled.Close, contentDescription = "Close") } },
                actions = { TextButton(onClick = ::save) { Text("Save", fontWeight = FontWeight.Bold) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { form = form.copy(name = it); problem = null },
                label = { Text("Name") },
                placeholder = { Text("e.g. Netflix") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            val pending = editing?.let { form.pendingPriceChange(it, today) }
            OutlinedTextField(
                value = form.cost,
                onValueChange = { form = form.copy(cost = it); problem = null },
                label = { Text("Cost") },
                placeholder = { Text("0.00") },
                prefix = when {
                    form.isForeign -> { { Text(form.currency.trim().uppercase() + " ") } }
                    format.currencySymbol.isEmpty() -> null
                    else -> { { Text(format.currencySymbol + " ") } }
                },
                supportingText = if (editing != null && pending != null) {
                    { Text("was ${format.moneyIn(editing.currency, editing.cost)}") }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (pending != null) {
                SwitchRow(
                    title = "Record this as a price change",
                    detail = if (form.recordPriceChange) format.describe(pending, editing.currency) + ", kept in your price history"
                    else "Just correcting a mistake: the old price is not kept",
                    checked = form.recordPriceChange,
                    onChange = { form = form.copy(recordPriceChange = it) },
                )
            }

            CurrencySection(form, format, onChange = { form = it; problem = null })

            Text("Billed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BillingCycle.entries.forEachIndexed { i, cycle ->
                    SegmentedButton(
                        selected = form.cycle == cycle,
                        onClick = { form = form.copy(cycle = cycle) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = BillingCycle.entries.size),
                        // No tick, so "Quarterly" fits on narrow phones.
                        icon = {},
                        label = { Text(cycle.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            SwitchRow(
                title = "Free trial",
                detail = "Remind me before it ends; the date below is when the first charge is taken",
                checked = form.freeTrial,
                onChange = { form = form.copy(freeTrial = it) },
            )

            Column {
                Text(
                    if (editing == null) "First payment" else "Next payment",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(form.date?.let { Format.date(it) } ?: "Choose a date", modifier = Modifier.weight(1f))
                }
                form.billingDescription(today)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                }
            }

            Column {
                OutlinedTextField(
                    value = form.category,
                    onValueChange = { form = form.copy(category = it) },
                    label = { Text("Category") },
                    placeholder = { Text("Other") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                val known = (state.manager.allIncludingCancelled.map { it.category } +
                    listOf("Entertainment", "Music", "Software", "Utilities", "Health", "Other"))
                    .distinctBy { it.lowercase() }
                    .filter { !it.equals(form.category.trim(), ignoreCase = true) }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    known.forEach { c ->
                        SuggestionChip(onClick = { form = form.copy(category = c) }, label = { Text(c) })
                    }
                }
            }

            OutlinedTextField(
                value = form.note,
                onValueChange = { form = form.copy(note = it.replace("\n", " ")) },
                label = { Text("Note (optional)") },
                placeholder = { Text("e.g. shared with family") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            problem?.let { Text(it, color = StatusColors.danger, style = MaterialTheme.typography.bodyMedium) }

            Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) {
                Text(if (editing == null) "Add subscription" else "Save changes")
            }

            if (editing != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { confirmCancel = true }, modifier = Modifier.weight(1f)) {
                        Text("Cancel subscription")
                    }
                    DangerButton("Remove", onClick = { confirmRemove = true })
                }
                Text(
                    "Cancelling keeps it in your cancelled list with what it saves you. Remove deletes it for good.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = form.date?.let { Dates.toPickerMillis(it) })
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { form = form.copy(date = Dates.fromPickerMillis(it)) }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = picker)
        }
    }

    if (editing != null && confirmCancel) {
        ConfirmDialog(
            title = "Cancel ${editing.name}?",
            text = "It moves to your cancelled list, and you'll save ${format.money(state.manager.homeMonthly(editing))} a month " +
                "(${format.money(state.manager.homeYearly(editing))} a year). Remember to cancel it with the provider too.",
            confirm = "Cancel it",
            danger = true,
            onConfirm = {
                editing.cancel(today)
                state.changed("Cancelled ${editing.name}. You'll save ${format.money(state.manager.homeMonthly(editing))} a month.")
                close()
            },
            onDismiss = { confirmCancel = false },
        )
    }
    if (editing != null && confirmRemove) {
        ConfirmDialog(
            title = "Remove ${editing.name}?",
            text = "It's deleted for good. To keep a record of what cancelling saves you, cancel it instead.",
            confirm = "Remove",
            danger = true,
            onConfirm = {
                state.manager.remove(editing.id)
                state.changed("Removed ${editing.name}.")
                close()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

/**
 * For a subscription billed in another currency (e.g. USD): its code, and
 * how much one unit is in your currency, with what the cost comes to.
 */
@Composable
private fun CurrencySection(form: SubscriptionForm, format: Format, onChange: (SubscriptionForm) -> Unit) {
    SwitchRow(
        title = "Billed in another currency",
        detail = if (form.isForeign) "Totals and the budget use the exchange rate below"
        else "e.g. a service that charges in US dollars",
        checked = form.isForeign,
        onChange = { on -> onChange(form.copy(foreign = on, currency = form.currency.ifBlank { "USD" })) },
    )
    if (!form.isForeign) return
    val code = form.currency.trim().uppercase()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = form.currency,
            onValueChange = { onChange(form.copy(currency = it.take(3).uppercase())) },
            label = { Text("Currency") },
            placeholder = { Text("USD") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = form.rate,
            onValueChange = { onChange(form.copy(rate = it)) },
            label = { Text("1 ${code.ifEmpty { "unit" }} is") },
            placeholder = { Text("18.25") },
            prefix = if (format.currencySymbol.isEmpty()) null else { { Text(format.currencySymbol + " ") } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1.4f),
        )
    }
    val cost = form.parsedCost
    val rate = form.parsedRate
    if (cost != null && rate != null) {
        Text(
            "${form.cycle.label} cost: about ${format.money(Money.divideRounded(cost * rate, 1_000_000))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
