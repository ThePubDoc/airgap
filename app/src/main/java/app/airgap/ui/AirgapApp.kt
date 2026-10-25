package app.airgap.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun AirgapApp(
    state: VaultUiState,
    viewModel: VaultViewModel,
    onUnlock: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
) {
    if (!state.unlocked) {
        LockScreen(
            vaultExists = state.vaultExists,
            keyInvalidated = state.keyInvalidated,
            onUnlock = onUnlock,
            onErase = viewModel::eraseVault,
        )
    } else {
        when (val screen = state.screen) {
            Screen.Cards -> CardListScreen(
                cards = state.cards,
                onAdd = { viewModel.openEditor(null) },
                onEdit = { viewModel.openEditor(it.id) },
                onCopy = onCopy,
                onLock = viewModel::lock,
            )
            is Screen.EditCard -> CardEditScreen(
                existing = state.cards.find { it.id == screen.cardId },
                onSave = viewModel::saveCard,
                onDelete = viewModel::deleteCard,
                onCancel = viewModel::closeEditor,
            )
        }
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
}
