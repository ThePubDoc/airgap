package app.airgap

import android.Manifest
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.airgap.nfc.EmvCardReader
import app.airgap.reminders.Reminders
import app.airgap.security.SecureClipboard
import app.airgap.ui.AirgapApp
import app.airgap.ui.AirgapTheme
import app.airgap.ui.NfcStatus
import app.airgap.ui.Screen
import app.airgap.ui.VaultUiState
import app.airgap.ui.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// BiometricPrompt needs a FragmentActivity.
class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels()
    private lateinit var biometricPrompt: BiometricPrompt
    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private var readerModeOn = false

    // Once allowed, show anything already due instead of waiting for the next daily check.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) lifecycleScope.launch(Dispatchers.IO) { runCatching { Reminders.check(applicationContext) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blocks screenshots and screen recording, and blanks the recent-apps preview.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), UnlockCallback())

        setContent {
            AirgapTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                AirgapApp(
                    state = state,
                    viewModel = viewModel,
                    onUnlock = ::requestUnlock,
                    onCopy = { label, value -> SecureClipboard.copy(this, label, value) },
                    onCardSaved = ::askForNotificationsOnce,
                    onOpenNfcSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                )
            }
        }

        // Reader mode only while the Add card screen is open, so a tapped card fills the form.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.state.collect { updateReaderMode(it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppShown()
        ContextCompat.registerReceiver(
            this,
            userPresentReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            // SystemUI sends USER_PRESENT under its own app ID, which NOT_EXPORTED
            // would block. Only the system may send it, and the receiver reads
            // nothing from it, so exporting is safe.
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter
        viewModel.setNfcStatus(
            when {
                adapter == null -> NfcStatus.UNAVAILABLE
                !adapter.isEnabled -> NfcStatus.OFF
                else -> NfcStatus.READY
            },
        )
        maybeAutoUnlock()
    }

    override fun onPause() {
        super.onPause()
        if (readerModeOn) {
            nfcAdapter?.disableReaderMode(this)
            readerModeOn = false
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(userPresentReceiver)
        // Lock whenever the app leaves the screen: home button, app switch, or screen off.
        // The card form gets a short grace period; see VaultViewModel.onAppHidden.
        if (!isChangingConfigurations && !viewModel.authInProgress) {
            viewModel.onAppHidden(screenOn = getSystemService(PowerManager::class.java).isInteractive)
            viewModel.autoUnlockArmed = true
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.onUserActivity()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Home or app switch. This also closes an open unlock prompt, which Android
        // reports as if the person had tapped Cancel, so arm the prompt here instead.
        viewModel.autoUnlockArmed = true
    }

    /**
     * Android resumes the activity a moment before it marks the phone as unlocked,
     * so the check in [maybeAutoUnlock] can still see a locked phone. USER_PRESENT
     * arrives once the lock screen is fully gone; try again then.
     */
    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = maybeAutoUnlock()
    }

    /**
     * Opens the unlock prompt by itself when the app comes back on screen. On first
     * run there's no vault yet, so the person reads the screen and taps "Create vault".
     */
    private fun maybeAutoUnlock() {
        val state = viewModel.state.value
        if (viewModel.autoUnlockArmed && state.vaultExists && !state.unlocked && !state.keyInvalidated &&
            !state.privacyCheckOpen && !isDeviceLocked()
        ) {
            viewModel.autoUnlockArmed = false
            requestUnlock()
        }
    }

    private fun updateReaderMode(state: VaultUiState) {
        val adapter = nfcAdapter ?: return
        val screen = state.screen
        val addingCard = state.unlocked && screen is Screen.EditCard && screen.cardId == null
        val wanted = addingCard && adapter.isEnabled
        if (wanted && !readerModeOn) {
            adapter.enableReaderMode(
                this,
                ::readTappedCard,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null,
            )
            readerModeOn = true
        } else if (!wanted && readerModeOn) {
            adapter.disableReaderMode(this)
            readerModeOn = false
        }
    }

    /** Runs on an NFC thread. Reads the card's public number and expiry, nothing else. */
    private fun readTappedCard(tag: Tag) {
        runOnUiThread { viewModel.onCardTapReading() }
        val read = runCatching {
            IsoDep.get(tag)?.use { card ->
                card.connect()
                card.timeout = 5_000
                EmvCardReader(card::transceive).read()
            }
        }.getOrNull()
        runOnUiThread { viewModel.onCardTapResult(read) }
    }

    /** Asks once, after the first card is saved, when reminders first become useful. */
    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("airgap_prefs", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return
        prefs.edit { putBoolean(KEY_ASKED_NOTIFICATIONS, true) }
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * The vault key only works while the phone is unlocked. The activity can start
     * behind the lock screen (for example when opened from a notification or the
     * lock screen itself), and asking for the key then fails with DEVICE_LOCKED.
     */
    private fun isDeviceLocked(): Boolean = getSystemService(KeyguardManager::class.java).isDeviceLocked

    private fun requestUnlock() {
        if (viewModel.authInProgress || isDeviceLocked()) return
        if (BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) {
            viewModel.showError("Set up a screen lock (PIN, pattern, password or fingerprint) in Android settings to use Airgap.")
            return
        }
        viewModel.authInProgress = true
        lifecycleScope.launch {
            val cipher = viewModel.prepareUnlock()
            if (cipher == null) {
                viewModel.authInProgress = false
                return@launch
            }
            val prompt = BiometricPrompt.PromptInfo.Builder()
                .setTitle(if (viewModel.state.value.vaultExists) "Unlock Airgap" else "Create your vault")
                .setSubtitle("Confirm it's you")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
            biometricPrompt.authenticate(prompt, BiometricPrompt.CryptoObject(cipher))
        }
    }

    private inner class UnlockCallback : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            viewModel.authInProgress = false
            // A success that lands after the app has left the screen would unlock the
            // vault in the background. Drop it; the prompt reopens when the app returns.
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                viewModel.autoUnlockArmed = true
                return
            }
            val cipher = result.cryptoObject?.cipher ?: return
            viewModel.completeUnlock(cipher)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            viewModel.authInProgress = false
            when (errorCode) {
                // The system closed the prompt, usually because the app left the screen.
                // Open it again next time the app comes back.
                BiometricPrompt.ERROR_CANCELED -> viewModel.autoUnlockArmed = true
                // The person closed it; wait for them to tap Unlock.
                BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON -> Unit
                else -> viewModel.showError(errString.toString())
            }
        }

        // onAuthenticationFailed (a finger that didn't match) needs nothing: the prompt stays open.
    }

    private companion object {
        const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
    }
}
