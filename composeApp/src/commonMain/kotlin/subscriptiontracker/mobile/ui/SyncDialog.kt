package subscriptiontracker.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Setting up sync: one file in a cloud drive (Google Drive, iCloud Drive,
 * OneDrive) that every device reads and writes.
 */
@Composable
fun SyncDialog(
    state: AppState,
    onCreate: () -> Unit,
    onPick: () -> Unit,
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = state.syncFileName
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (name == null) "Sync with your other devices" else "Syncing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (name == null) {
                    Text(
                        "Keep one list on your phone and computer through a file in your Google Drive, iCloud Drive " +
                            "or OneDrive. Create the file on one device, then choose the same file on the others. " +
                            "Changes from every device are merged, subscription by subscription.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "On a computer, the drive's own app (e.g. Google Drive for desktop) needs to be installed so " +
                            "its folder appears among your files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrimaryButton("Create a sync file", onClick = { onCreate(); onDismiss() })
                    OutlinedButton(onClick = { onPick(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Use an existing sync file")
                    }
                } else {
                    Text("Syncing with $name.", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "The app syncs when it opens and after each change.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.syncStatus?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = if (it.startsWith("Couldn't")) StatusColors.danger else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PrimaryButton("Sync now", onClick = onSyncNow, arrow = false)
                    OutlinedButton(onClick = { state.stopSyncing(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Stop syncing")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
