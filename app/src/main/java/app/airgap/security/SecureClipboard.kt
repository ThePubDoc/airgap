package app.airgap.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * Copies card details for pasting into a checkout form, then wipes the
 * clipboard after a short delay. The clip is flagged sensitive so Android 13+
 * hides it from the "copied" preview and keyboard clipboard suggestions.
 */
object SecureClipboard {
    private const val CLEAR_AFTER_MS = 30_000L
    private val clearToken = Any()
    private val handler = Handler(Looper.getMainLooper())

    fun copy(context: Context, label: String, text: String) {
        val clipboard = context.applicationContext.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(label, text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(sensitiveExtraKey(), true)
            }
        }
        clipboard.setPrimaryClip(clip)

        // Each new copy restarts the countdown.
        handler.removeCallbacksAndMessages(clearToken)
        handler.postDelayed({ clipboard.clearPrimaryClip() }, clearToken, CLEAR_AFTER_MS)
    }

    private fun sensitiveExtraKey(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            // Same key, honoured by many keyboards before it became public API.
            "android.content.extra.IS_SENSITIVE"
        }
}
