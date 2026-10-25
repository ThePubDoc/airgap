package app.airgap.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AirgapApp(
    state: VaultUiState,
    viewModel: VaultViewModel,
    onUnlock: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    onCardSaved: () -> Unit,
    onOpenNfcSettings: () -> Unit,
) {
    val nfcStatus by viewModel.nfcStatus.collectAsStateWithLifecycle()
    val cardTap by viewModel.cardTap.collectAsStateWithLifecycle()

    if (state.privacyCheckOpen) {
        val context = LocalContext.current
        val report = remember { PrivacyReport.from(context) }
        PrivacyCheckScreen(report, onBack = viewModel::closePrivacyCheck)
    } else if (!state.firstRunDone) {
        FirstRunScreen(onContinue = viewModel::finishFirstRun)
    } else if (!state.unlocked) {
        LockScreen(
            vaultExists = state.vaultExists,
            hasUnsavedCard = state.screen is Screen.EditCard,
            keyInvalidated = state.keyInvalidated,
            onUnlock = onUnlock,
            onErase = viewModel::eraseVault,
            onPrivacyCheck = viewModel::openPrivacyCheck,
        )
    } else {
        when (val screen = state.screen) {
            Screen.Cards -> CardListScreen(
                cards = state.cards,
                onAdd = { viewModel.openEditor(null) },
                onEdit = { viewModel.openEditor(it.id) },
                onCopy = onCopy,
                onLock = viewModel::lock,
                onPrivacyCheck = viewModel::openPrivacyCheck,
            )
            is Screen.EditCard -> CardEditScreen(
                existing = state.cards.find { it.id == screen.cardId },
                initialDraft = viewModel.draft ?: CardDraft(),
                onDraftChange = viewModel::updateDraft,
                nfcStatus = nfcStatus,
                cardTap = cardTap,
                onCardTapUsed = viewModel::onCardTapUsed,
                onOpenNfcSettings = onOpenNfcSettings,
                onSave = { card ->
                    viewModel.saveCard(card)
                    onCardSaved()
                },
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
