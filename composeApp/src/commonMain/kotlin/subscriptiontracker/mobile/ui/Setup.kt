@file:OptIn(ExperimentalMaterial3Api::class)

package subscriptiontracker.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import subscriptiontracker.mobile.data.CsvImporter

/** The steps shown the first time the app is opened. */
enum class SetupStep { WELCOME, CURRENCY, BUDGET, START }

/** How to begin, chosen in the last step. */
private enum class Start(val text: String, val detail: String?) {
    ADD("Add my first subscription", null),
    IMPORT("Import a list from a CSV file", "From a spreadsheet, or exported from another device"),
    RESTORE("Restore a backup", "Everything from another phone, or the desktop app's subscriptions.txt"),
    LOOK("Just look around", null),
}

private val CURRENCIES = listOf(
    "R" to "South African rand",
    "$" to "US dollar",
    "€" to "Euro",
    "£" to "British pound",
    "" to "No currency symbol",
)

/**
 * First-run setup: a welcome, then three questions (currency, budget, how
 * to start), each answered by picking a card and pressing Next.
 * [onImport] and [onRestore] open the file pickers.
 */
@Composable
fun SetupFlow(state: AppState, firstStep: SetupStep, onImport: () -> Unit, onRestore: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(firstStep) }
    var currency by rememberSaveable { mutableStateOf<String?>(null) }
    var wantsBudget by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var budget by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf<Start?>(null) }

    val budgetCents = try {
        CsvImporter.parseAmount(budget).takeIf { it > 0 }
    } catch (e: IllegalArgumentException) {
        null
    }

    BackHandler(enabled = step != SetupStep.WELCOME) {
        step = SetupStep.entries[step.ordinal - 1]
    }

    when (step) {
        SetupStep.WELCOME -> Welcome(onStart = { step = SetupStep.CURRENCY }, onRestore = onRestore)
        SetupStep.CURRENCY -> Question(
            number = 1,
            title = "Which currency do you pay in?",
            explanation = "Amounts are shown with its symbol. A subscription billed in another currency can be set up on its own.",
            canGoOn = currency != null,
            onBack = { step = SetupStep.WELCOME },
            onNext = { step = SetupStep.BUDGET },
        ) {
            CURRENCIES.forEach { (symbol, name) ->
                ChoiceCard(
                    text = name,
                    detail = symbol.ifEmpty { null }?.let { "Shown like ${it}${if (it.last().isLetter()) " " else ""}199.00" },
                    selected = currency == symbol,
                    onClick = { currency = symbol },
                )
            }
        }
        SetupStep.BUDGET -> Question(
            number = 2,
            title = "Would you like a monthly budget?",
            explanation = "You'll see how much of it your subscriptions use, and be warned when they come close.",
            canGoOn = wantsBudget == false || (wantsBudget == true && budgetCents != null),
            onBack = { step = SetupStep.CURRENCY },
            onNext = { step = SetupStep.START },
        ) {
            ChoiceCard("Yes, set a budget", selected = wantsBudget == true, onClick = { wantsBudget = true })
            if (wantsBudget == true) {
                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it },
                    label = { Text("Each month") },
                    placeholder = { Text("1500") },
                    prefix = if (currency.isNullOrEmpty()) null else { { Text("$currency ") } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ChoiceCard("Not now", detail = "You can set one later on the Spending tab",
                selected = wantsBudget == false, onClick = { wantsBudget = false })
        }
        SetupStep.START -> Question(
            number = 3,
            title = "How would you like to start?",
            explanation = "You can do any of these later from the menu at the top right.",
            canGoOn = start != null,
            onBack = { step = SetupStep.BUDGET },
            onNext = {
                val chosen = start
                if (chosen != null) {
                    state.finishSetup(currency ?: "", if (wantsBudget == true) budgetCents else null)
                    when (chosen) {
                        Start.ADD -> state.editing = AppState.Editing(null)
                        Start.IMPORT -> onImport()
                        Start.RESTORE -> onRestore()
                        Start.LOOK -> {}
                    }
                }
            },
        ) {
            Start.entries.forEach { option ->
                ChoiceCard(option.text, detail = option.detail, selected = start == option, onClick = { start = option })
            }
        }
    }
}

/** The welcome: the app's name, a fan of cards, and a button to begin. */
@Composable
private fun Welcome(onStart: () -> Unit, onRestore: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Subscription Tracker",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            Artwork(Modifier.weight(1f).fillMaxWidth().padding(vertical = 24.dp))
            Text(
                "Every subscription,\nin one place",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Know what you pay, when it's due, and what you save by cancelling. No account, no fees: " +
                    "your list stays on this device.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
            PrimaryButton("Get started", onClick = onStart)
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Restore a backup")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * The picture on the welcome: four subscription cards fanned out like a
 * wallet's, each with a round initial and lines standing for its text.
 */
@Composable
private fun Artwork(modifier: Modifier) {
    val colors = listOf(CardColors[3], CardColors[5], CardColors[1], CardColors[0])
    Canvas(modifier) {
        val w = minOf(size.width * 0.82f, size.height * 1.1f)
        val h = w / 1.586f
        val step = h * 0.3f
        val x = (size.width - w) / 2f
        val top = (size.height - (h + step * (colors.size - 1))) / 2f
        val r = CornerRadius(h * 0.09f)
        colors.forEachIndexed { i, color ->
            val y = top + i * step
            rotate((i - 1.5f) * -2.5f, pivot = Offset(x + w / 2f, y + h / 2f)) {
                drawRoundRect(Color.Black.copy(alpha = 0.10f), Offset(x, y + h * 0.04f), Size(w, h), r)
                drawRoundRect(
                    Brush.linearGradient(listOf(color, lerp(color, Color.Black, 0.28f)), Offset(x, y), Offset(x + w, y + h)),
                    Offset(x, y), Size(w, h), r,
                )
                val pad = h * 0.09f
                val dot = h * 0.11f
                drawCircle(Color.White, radius = dot, center = Offset(x + pad + dot, y + pad + dot))
                val line = h * 0.07f
                drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(x + pad * 2 + dot * 2, y + pad + dot * 0.35f),
                    Size(w * 0.34f, line), CornerRadius(line / 2f))
                drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(x + pad * 2 + dot * 2, y + pad + dot * 1.15f),
                    Size(w * 0.2f, line * 0.8f), CornerRadius(line / 2f))
                drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(x + w - pad - w * 0.2f, y + pad + dot * 0.35f),
                    Size(w * 0.2f, line), CornerRadius(line / 2f))
            }
        }
    }
}

/**
 * One setup question: a back arrow with the step and a progress bar, then
 * the question, an explanation, the [choices], and a pill to go on.
 */
@Composable
private fun Question(
    number: Int,
    title: String,
    explanation: String,
    canGoOn: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    choices: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Step $number of 3", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge)
            }
            LinearProgressIndicator(
                progress = { number / 3f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(explanation, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                choices()
            }
            NextButton(
                onClick = onNext,
                enabled = canGoOn,
                text = if (number == 3) "Done" else "Next",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(24.dp),
            )
        }
    }
}
