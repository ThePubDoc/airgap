package app.airgap

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.airgap.security.SecureClipboard
import app.airgap.ui.AirgapApp
import app.airgap.ui.AirgapTheme
import app.airgap.ui.VaultViewModel
import kotlinx.coroutines.launch

// BiometricPrompt needs a FragmentActivity.
class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels()
    private lateinit var biometricPrompt: BiometricPrompt

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
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Lock whenever the app leaves the screen: home button, app switch, or screen off.
        if (!isChangingConfigurations && !viewModel.authInProgress) {
            viewModel.lock()
        }
    }

    private fun requestUnlock() {
        if (viewModel.authInProgress) return
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
            val cipher = result.cryptoObject?.cipher ?: return
            viewModel.completeUnlock(cipher)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            viewModel.authInProgress = false
            val dismissedByUser = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_CANCELED
            if (!dismissedByUser) viewModel.showError(errString.toString())
        }

        // onAuthenticationFailed (a finger that didn't match) needs nothing: the prompt stays open.
    }

    private companion object {
        const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    }
}
