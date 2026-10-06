@file:OptIn(ExperimentalMaterial3Api::class)

package subscriptiontracker.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * to start), each answered by picking a card and pressing the round arrow.
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

/** The welcome: black, with the app's name, a picture, and a button to begin. */
@Composable
private fun Welcome(onStart: () -> Unit, onRestore: () -> Unit) {
    Box(Modifier.fillMaxSize().background(AppColors.Ink), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "subscription\ntracker",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Artwork(Modifier.weight(1f).fillMaxWidth().padding(vertical = 24.dp))
            Text(
                "Every subscription,\nin one place",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Know what you pay, when it's due, and what you save by cancelling. No account, no fees: " +
                    "your list stays on this device.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFD6D6D1),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AppColors.Ink),
            ) {
                Text("Get started", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(start = 10.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
            Spacer(Modifier.height(18.dp))
            Row {
                Text("Moving from another device? ", color = Color(0xFFD6D6D1), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Restore a backup",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(onClick = onRestore),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * The picture on the welcome: subscription cards and coins, drawn in the
 * app's orange, green and white.
 */
@Composable
private fun Artwork(modifier: Modifier) {
    Canvas(modifier) {
        val unit = minOf(size.width, size.height) / 10f
        val c = Offset(size.width / 2f, size.height / 2f)
        fun card(dx: Float, dy: Float, degrees: Float, color: Color, outline: Boolean = false) {
            val w = unit * 5.6f
            val h = unit * 3.5f
            val topLeft = Offset(c.x + dx * unit - w / 2f, c.y + dy * unit - h / 2f)
            rotate(degrees, pivot = Offset(topLeft.x + w / 2f, topLeft.y + h / 2f)) {
                if (outline) {
                    drawRoundRect(color, topLeft, Size(w, h), CornerRadius(unit * 0.4f), style = Stroke(width = unit * 0.12f))
                } else {
                    drawRoundRect(color, topLeft, Size(w, h), CornerRadius(unit * 0.4f))
                    // A stripe and two lines of "text" on the card.
                    drawRect(AppColors.Ink.copy(alpha = 0.85f), Offset(topLeft.x, topLeft.y + h * 0.22f), Size(w, h * 0.14f))
                    drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(topLeft.x + w * 0.1f, topLeft.y + h * 0.58f),
                        Size(w * 0.45f, h * 0.08f), CornerRadius(h * 0.04f))
                    drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(topLeft.x + w * 0.1f, topLeft.y + h * 0.74f),
                        Size(w * 0.28f, h * 0.08f), CornerRadius(h * 0.04f))
                }
            }
        }
        card(-0.9f, -1.2f, -14f, Color.White, outline = true)
        card(-0.6f, -0.4f, -9f, AppColors.Green)
        card(0.8f, 0.9f, 7f, AppColors.Orange)
        // Coins.
        drawCircle(Color.White, radius = unit * 0.95f, center = Offset(c.x + unit * 3.1f, c.y - unit * 2.2f))
        drawCircle(AppColors.Ink, radius = unit * 0.55f, center = Offset(c.x + unit * 3.1f, c.y - unit * 2.2f),
            style = Stroke(width = unit * 0.14f))
        drawCircle(AppColors.Orange, radius = unit * 0.6f, center = Offset(c.x - unit * 3.4f, c.y + unit * 2.4f))
        drawCircle(AppColors.Green, radius = unit * 0.38f, center = Offset(c.x + unit * 3.6f, c.y + unit * 2.9f))
    }
}

/**
 * One setup question: a black strip with the step and a back arrow, then the
 * question, an explanation, the [choices] as cards, and the round arrow to go on.
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
        Column(Modifier.fillMaxWidth().background(AppColors.Ink).windowInsetsPadding(WindowInsets.statusBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text("Step $number of 3", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            LinearProgressIndicator(
                progress = { number / 3f },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Color.White,
                trackColor = Color(0xFF3A3A38),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(title, style = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground)
                Text(explanation, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                choices()
            }
            NextButton(
                onClick = onNext,
                enabled = canGoOn,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(24.dp),
            )
        }
    }
}
