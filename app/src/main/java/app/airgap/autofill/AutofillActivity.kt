package app.airgap.autofill

import android.app.Application
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Bundle
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.view.WindowManager
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import app.airgap.ui.AirgapTheme
import app.airgap.vault.Card
import app.airgap.vault.CardValidation
import app.airgap.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

/**
 * Opened by [CardAutofillService] when the person taps "Fill card with Airgap".
 * Unlocks the vault with the usual prompt, shows the cards and where they'd be
 * filled, and returns the chosen card to the form as an autofill dataset.
 */
class AutofillActivity : FragmentActivity() {

    private val viewModel: AutofillViewModel by viewModels()
    private lateinit var biometricPrompt: BiometricPrompt
    private var form: CardForm? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setResult(RESULT_CANCELED)

        form = IntentCompat.getParcelableExtra(intent, AutofillManager.EXTRA_ASSIST_STRUCTURE, AssistStructure::class.java)
            ?.let(CardForm::parse)
            ?.takeIf { it.targets.isNotEmpty() }
        if (form == null) {
            finish()
            return
        }

        biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), UnlockCallback())

        setContent {
            AirgapTheme {
                CardPicker(
                    requester = form?.requester.orEmpty(),
                    cards = viewModel.cards,
                    message = viewModel.message,
                    onPick = ::fill,
                    onCancel = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!viewModel.promptStarted) {
            viewModel.promptStarted = true
            requestUnlock()
        }
    }

    private fun requestUnlock() {
        if (!viewModel.vaultExists) {
            viewModel.message = "There's no Airgap vault on this phone yet. Open Airgap to add your cards."
            return
        }
        if (BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) {
            viewModel.message = "Set up a screen lock in Android settings to use Airgap."
            return
        }
        lifecycleScope.launch {
            val cipher = viewModel.prepareUnlock() ?: return@launch
            val prompt = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Fill card with Airgap")
                .setSubtitle(form?.requester.orEmpty())
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
            biometricPrompt.authenticate(prompt, BiometricPrompt.CryptoObject(cipher))
        }
    }

    private fun fill(card: Card) {
        val dataset = form?.datasetFor(card)
        if (dataset == null) {
            viewModel.message = "This form has no field Airgap can fill with that card."
            return
        }
        setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
        finish()
    }

    private inner class UnlockCallback : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            val cipher = result.cryptoObject?.cipher ?: return
            viewModel.loadCards(cipher)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            // Closing the prompt means "don't fill": go straight back to the form.
            finish()
        }
    }

    private companion object {
        const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    }
}

/**
 * Holds the cards for the picker. The vault is locked again as soon as they're
 * read, so the data key is only in memory for that moment.
 */
class AutofillViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VaultRepository(application)

    var cards by mutableStateOf<List<Card>?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    var promptStarted = false

    val vaultExists: Boolean get() = repository.isCreated

    suspend fun prepareUnlock(): Cipher? = withContext(Dispatchers.IO) {
        try {
            repository.unlockCipher()
        } catch (e: KeyPermanentlyInvalidatedException) {
            message = "Your vault key is gone. Open Airgap to set up a new vault."
            null
        } catch (e: Exception) {
            message = "Couldn't prepare the vault key."
            null
        }
    }

    fun loadCards(approvedCipher: Cipher) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.unlock(approvedCipher) }.also { repository.lock() }
            }
            result
                .onSuccess { cards = it }
                .onFailure { message = "Couldn't open the vault." }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardPicker(
    requester: String,
    cards: List<Card>?,
    message: String?,
    onPick: (Card) -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose a card") },
                actions = { TextButton(onClick = onCancel) { Text("Cancel") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Shown prominently so a look-alike site or app is easier to spot.
            Text(
                "Filling into $requester",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                message != null -> CenteredText(message)
                cards == null -> Unit
                cards.isEmpty() -> CenteredText("No cards yet. Open Airgap to add one.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(cards, key = { it.id }) { card -> PickableCard(card, onClick = { onPick(card) }) }
                }
            }
        }
    }
}

@Composable
private fun PickableCard(card: Card, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(card.color.argb), contentColor = Color.White),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.nickname.ifBlank { card.network.displayName },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text("${card.network.displayName} · ${card.kind.label}", style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.padding(top = 8.dp)) {
                Text(
                    CardValidation.mask(card.number),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                Text(CardValidation.formatExpiry(card.expiryMonth, card.expiryYear), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}
