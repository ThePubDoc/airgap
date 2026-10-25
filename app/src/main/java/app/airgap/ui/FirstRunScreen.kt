package app.airgap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Shown once, before the vault is created. Airgap has no backup by design, so the
 * ways cards can be lost are spelled out up front rather than discovered later.
 */
@Composable
fun FirstRunScreen(onContinue: () -> Unit) {
    var understood by rememberSaveable { mutableStateOf(false) }
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Before you start", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Airgap keeps your cards on this phone and nowhere else. That's what keeps them private, " +
                    "and it also means:",
                style = MaterialTheme.typography.bodyLarge,
            )
            Point(
                "There's no backup.",
                "If you lose this phone, reset it or uninstall Airgap, your saved cards are gone. Your bank can always reissue a card.",
            )
            Point(
                "Turning off your screen lock erases the vault.",
                "Android deletes the key that protects your cards when the screen lock is removed. Keep a PIN, pattern or password set.",
            )
            Point(
                "A new phone means adding your cards again.",
                "Tapping each card on the back of the new phone takes a few seconds.",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = understood, role = Role.Checkbox, onValueChange = { understood = it }),
            ) {
                // The whole row toggles; the checkbox itself only shows the state.
                Checkbox(checked = understood, onCheckedChange = null)
                Text(
                    "I understand there's no backup",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Button(onClick = onContinue, enabled = understood, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun Point(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium)
    }
}
