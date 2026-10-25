package app.airgap.ui

import android.app.Application
import android.os.SystemClock
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.airgap.vault.Card
import app.airgap.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /**
     * The card form's contents while it's open, kept through a lock so the person
     * can unlock and carry on. Memory only; cleared on save, cancel or erase.
     */
    var draft: CardDraft? = null
        private set

    private var hiddenAt: Long? = null
    private var pendingLock: Job? = null
    private var idleLock: Job? = null

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
                        // Keeps the current screen, so an unsaved card form comes back as it was.
                        it.copy(vaultExists = true, unlocked = true, cards = cards, error = null)
                    }
                    onUserActivity()
                }
                .onFailure { showError("Couldn't open the vault. The file may be damaged.") }
        }
    }

    fun lock() {
        idleLock?.cancel()
        idleLock = null
        pendingLock?.cancel()
        pendingLock = null
        hiddenAt = null
        if (!_state.value.unlocked) return
        // An open card form stays put (its draft is kept) and reappears after unlocking.
        _state.update { it.copy(unlocked = false, cards = emptyList(), screen = if (draft != null) it.screen else Screen.Cards) }
        // Queued behind any save still in flight, so that save finishes first.
        viewModelScope.launch(vaultDispatcher) { repository.lock() }
    }

    /**
     * The app left the screen. Locks straight away, except while the card form is
     * open and the screen is still on: then the person is probably copying details
     * from another app, so the vault stays open for [EDIT_GRACE_MS].
     */
    fun onAppHidden(screenOn: Boolean) {
        // Time away from the app is handled here, not by the inactivity timer.
        idleLock?.cancel()
        idleLock = null
        val editing = _state.value.unlocked && _state.value.screen is Screen.EditCard
        if (!editing || !screenOn) {
            lock()
            return
        }
        hiddenAt = SystemClock.elapsedRealtime()
        pendingLock?.cancel()
        pendingLock = viewModelScope.launch {
            delay(EDIT_GRACE_MS)
            lock()
        }
    }

    /** The app is back on screen. Locks if the grace period ran out while it was away. */
    fun onAppShown() {
        pendingLock?.cancel()
        pendingLock = null
        val since = hiddenAt
        hiddenAt = null
        // The timer above doesn't run while Android has the app frozen, so check the clock too.
        if (since != null && SystemClock.elapsedRealtime() - since > EDIT_GRACE_MS) lock()
        onUserActivity()
    }

    /**
     * A touch on the app, or a change to the card form (keyboard typing goes to the
     * keyboard app, so the activity never sees it). Restarts the inactivity timer.
     */
    fun onUserActivity() {
        if (!_state.value.unlocked) return
        idleLock?.cancel()
        idleLock = viewModelScope.launch {
            delay(IDLE_LOCK_MS)
            lock()
        }
    }

    fun openEditor(cardId: String?) {
        draft = _state.value.cards.find { it.id == cardId }?.let(CardDraft::from) ?: CardDraft()
        _state.update { it.copy(screen = Screen.EditCard(cardId)) }
    }

    fun updateDraft(newDraft: CardDraft) {
        if (newDraft == draft) return
        draft = newDraft
        onUserActivity()
    }

    fun closeEditor() {
        draft = null
        _state.update { it.copy(screen = Screen.Cards) }
    }

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
            draft = null
            _state.value = VaultUiState()
        }
    }

    fun showError(message: String) = _state.update { it.copy(error = message) }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun persist(newCards: List<Card>) {
        val previous = _state.value.cards
        draft = null
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

    private companion object {
        /** How long the vault stays open in the background while the card form is open. */
        const val EDIT_GRACE_MS = 60_000L

        /** How long the vault stays open on screen with no touches or typing. */
        const val IDLE_LOCK_MS = 2 * 60_000L
    }
}
