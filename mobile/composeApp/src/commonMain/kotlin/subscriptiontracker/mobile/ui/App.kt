package subscriptiontracker.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path
import subscriptiontracker.mobile.data.CsvExporter
import subscriptiontracker.mobile.data.CsvImporter
import subscriptiontracker.mobile.data.Format
import subscriptiontracker.mobile.data.TextDecoding

private enum class Tab(val title: String, val label: String, val icon: ImageVector) {
    SUBSCRIPTIONS("Subscriptions", "Subscriptions", Icons.Filled.Subscriptions),
    UPCOMING("Upcoming & trials", "Upcoming", Icons.Filled.CalendarMonth),
    SPENDING("Spending & budget", "Spending", Icons.Filled.PieChart),
    CANCELLED("Cancelled & savings", "Cancelled", Icons.Filled.Savings),
}

/**
 * The whole app: four tabs along the bottom, a menu at the top right for
 * importing, exporting and the currency symbol, and the add/edit form over
 * the top when it's open. [file] is where the subscriptions are saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(fileSystem: FileSystem, file: Path) {
    val state = remember { AppState(fileSystem, file) }
    var tab by rememberSaveable { mutableStateOf(Tab.SUBSCRIPTIONS) }
    var menuOpen by remember { mutableStateOf(false) }
    var choosingCurrency by remember { mutableStateOf(false) }
    var importReport by remember { mutableStateOf<String?>(null) }
    var showLoadError by remember { mutableStateOf(state.loadError != null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    AppTheme {
        LaunchedEffect(state.message) {
            state.message?.let {
                snackbar.showSnackbar(it)
                state.message = null
            }
        }

        val importer = rememberFilePickerLauncher(type = FileKitType.File(setOf("csv", "txt"))) { picked ->
            if (picked == null) return@rememberFilePickerLauncher
            scope.launch {
                importReport = try {
                    val text = TextDecoding.decode(picked.readBytes())
                    val result = CsvImporter.importText(text, state.manager, state.today)
                    if (result.imported > 0) state.changed(null)
                    buildString {
                        append("Imported ${result.imported} subscription(s).")
                        if (result.problems.isNotEmpty()) {
                            append("\n\nSkipped ${result.problems.size} row(s):")
                            result.problems.take(10).forEach { append("\n• ").append(it) }
                            if (result.problems.size > 10) append("\n…and ${result.problems.size - 10} more.")
                        }
                    }
                } catch (e: Exception) {
                    "Couldn't read that file: ${e.message}"
                }
            }
        }
        @Suppress("DEPRECATION") // Only deprecated for web targets, which this app doesn't have.
        val exporter = rememberFileSaverLauncher { target ->
            if (target == null) return@rememberFileSaverLauncher
            scope.launch {
                state.message = try {
                    target.writeString(CsvExporter.toCsv(state.manager.all))
                    "Exported ${state.manager.all.size} subscription(s)."
                } catch (e: Exception) {
                    "Couldn't export: ${e.message}"
                }
            }
        }

        val editing = state.editing
        if (editing != null) {
            EditScreen(state, editing.subscription)
        } else Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(tab.title) },
                    actions = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Import CSV…") }, onClick = {
                                    menuOpen = false
                                    importer.launch()
                                })
                                DropdownMenuItem(text = { Text("Export CSV…") }, enabled = !state.manager.isEmpty, onClick = {
                                    menuOpen = false
                                    exporter.launch(suggestedName = "subscriptions", extension = "csv")
                                })
                                DropdownMenuItem(text = { Text("Currency symbol…") }, onClick = {
                                    menuOpen = false
                                    choosingCurrency = true
                                })
                            }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab == Tab.SUBSCRIPTIONS) {
                    ExtendedFloatingActionButton(
                        onClick = { state.editing = AppState.Editing(null) },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("Add") },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            when (tab) {
                Tab.SUBSCRIPTIONS -> SubscriptionsScreen(state, padding)
                Tab.UPCOMING -> UpcomingScreen(state, padding)
                Tab.SPENDING -> SpendingScreen(state, padding)
                Tab.CANCELLED -> CancelledScreen(state, padding)
            }
        }

        if (choosingCurrency) {
            TextInputDialog(
                title = "Currency symbol",
                explanation = "Shown on every amount, e.g. R or $. Leave it empty for no symbol.",
                initial = state.manager.currencySymbol,
                onDismiss = { choosingCurrency = false },
                check = { if (it.isEmpty() || Format.isValidCurrencySymbol(it)) null else "Please use up to 5 letters or symbols, e.g. R or $." },
                onDone = {
                    state.manager.currencySymbol = it
                    state.changed(if (it.isEmpty()) "Currency symbol removed." else "Amounts now show $it.")
                },
            )
        }
        if (showLoadError) {
            AlertDialog(
                onDismissRequest = { showLoadError = false },
                title = { Text("Couldn't read your subscriptions") },
                text = { Text(state.loadError ?: "") },
                confirmButton = { TextButton(onClick = { showLoadError = false }) { Text("OK") } },
            )
        }
        importReport?.let { report ->
            AlertDialog(
                onDismissRequest = { importReport = null },
                title = { Text("Import") },
                text = { Text(report) },
                confirmButton = { TextButton(onClick = { importReport = null }) { Text("OK") } },
            )
        }
    }
}
