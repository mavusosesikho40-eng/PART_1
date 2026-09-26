package subscriptiontracker.mobile.ui

import androidx.compose.runtime.Composable

/** iPhones have no back button; the form closes with its X. */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
}
