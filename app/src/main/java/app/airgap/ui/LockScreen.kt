package app.airgap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LockScreen(
    vaultExists: Boolean,
    hasUnsavedCard: Boolean,
    keyInvalidated: Boolean,
    onUnlock: () -> Unit,
    onErase: () -> Unit,
    onPrivacyCheck: () -> Unit,
) {
    var confirmErase by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Airgap", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    keyInvalidated ->
                        "Android deleted the key that protects your vault. This happens when the " +
                            "screen lock is removed. The saved cards can't be decrypted any more."
                    hasUnsavedCard -> "Unlock to carry on with your card. What you typed is still here."
                    vaultExists -> "Your cards are encrypted on this phone only."
                    else -> "Cards you add are encrypted with a key in this phone's secure hardware. " +
                        "Nothing is ever sent anywhere: this app has no internet access."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            if (keyInvalidated) {
                Button(
                    onClick = { confirmErase = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Erase vault and start over")
                }
            } else {
                Button(onClick = onUnlock) {
                    Text(if (vaultExists) "Unlock" else "Create vault")
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onPrivacyCheck) { Text("Privacy check") }
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase all cards?") },
            text = { Text("Every saved card is deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmErase = false; onErase() }) { Text("Erase") }
            },
            dismissButton = {
                TextButton(onClick = { confirmErase = false }) { Text("Cancel") }
            },
        )
    }
}
