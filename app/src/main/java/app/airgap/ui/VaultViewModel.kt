package app.airgap.ui

import android.app.Application
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.airgap.vault.Card
import app.airgap.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

sealed interface Screen {
    data object Cards : Screen
    data class EditCard(val cardId: String?) : Screen
}

data class VaultUiState(
    val vaultExists: Boolean = false,
    val unlocked: Boolean = false,
    val cards: List<Card> = emptyList(),
    val screen: Screen = Screen.Cards,
    val keyInvalidated: Boolean = false,
    val error: String? = null,
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VaultRepository(application)

    // One thread for all vault work, so saves, locks and unlocks run in the order they're issued.
    private val vaultDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val _state = MutableStateFlow(VaultUiState(vaultExists = repository.isCreated))
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    /**
     * True while BiometricPrompt, or the PIN screen it opens, is showing. That PIN
     * screen stops the activity, which would otherwise lock the vault mid-unlock.
     */
    var authInProgress = false

    /**
     * Whether the next time the app comes on screen should open the unlock prompt
     * by itself. Set each time the vault locks and cleared once the prompt opens,
     * so cancelling the prompt doesn't immediately bring it back.
     */
    var autoUnlockArmed = true

    suspend fun prepareUnlock(): Cipher? = withContext(vaultDispatcher) {
        try {
            repository.unlockCipher()
        } catch (e: KeyPermanentlyInvalidatedException) {
            _state.update { it.copy(keyInvalidated = true) }
            null
        } catch (e: Exception) {
            showError("Couldn't prepare the vault key. ${e.message.orEmpty()}")
            null
        }
    }

    fun completeUnlock(approvedCipher: Cipher) {
        viewModelScope.launch {
            val result = withContext(vaultDispatcher) { runCatching { repository.unlock(approvedCipher) } }
            result
                .onSuccess { cards ->
                    _state.update {
                        it.copy(vaultExists = true, unlocked = true, cards = cards, screen = Screen.Cards, error = null)
                    }
                }
                .onFailure { showError("Couldn't open the vault. The file may be damaged.") }
        }
    }

    fun lock() {
        if (!_state.value.unlocked) return
        _state.update { it.copy(unlocked = false, cards = emptyList(), screen = Screen.Cards) }
        // Queued behind any save still in flight, so that save finishes first.
        viewModelScope.launch(vaultDispatcher) { repository.lock() }
    }

    fun openEditor(cardId: String?) = _state.update { it.copy(screen = Screen.EditCard(cardId)) }

    fun closeEditor() = _state.update { it.copy(screen = Screen.Cards) }

    fun saveCard(card: Card) {
        val cards = _state.value.cards
        val index = cards.indexOfFirst { it.id == card.id }
        persist(if (index >= 0) cards.toMutableList().apply { set(index, card) } else cards + card)
    }

    fun deleteCard(cardId: String) = persist(_state.value.cards.filterNot { it.id == cardId })

    /** Deletes every card and the key, returning to first-run state. */
    fun eraseVault() {
        viewModelScope.launch {
            withContext(vaultDispatcher) { repository.erase() }
            _state.value = VaultUiState()
        }
    }

    fun showError(message: String) = _state.update { it.copy(error = message) }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun persist(newCards: List<Card>) {
        val previous = _state.value.cards
        _state.update { it.copy(cards = newCards, screen = Screen.Cards) }
        viewModelScope.launch {
            val result = withContext(vaultDispatcher) { runCatching { repository.save(newCards) } }
            result.onFailure {
                _state.update {
                    if (it.unlocked) it.copy(cards = previous, error = "Couldn't save. Your change wasn't stored.") else it
                }
            }
        }
    }

    override fun onCleared() {
        repository.lock()
    }
}
