package app.airgap.security

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock

/**
 * Copies card details for pasting into a checkout form, then wipes the
 * clipboard after a short delay. The clip is flagged sensitive so Android 13+
 * hides it from the "copied" preview and keyboard clipboard suggestions.
 */
object SecureClipboard {
    private const val CLEAR_AFTER_MS = 30_000L

    fun copy(context: Context, label: String, text: String) {
        val appContext = context.applicationContext
        val clip = ClipData.newPlainText(label, text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(sensitiveExtraKey(), true)
            }
        }
        appContext.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        scheduleClear(appContext)
    }

    /**
     * Uses an alarm rather than an in-process timer: once the person switches
     * to the app they're pasting into, Airgap sits in the background, where
     * Android (Samsung especially) freezes or kills it and a timer never fires.
     * An alarm wakes the app up, or starts it again, to deliver the clear.
     *
     * The alarm is inexact so it needs no special permission. Android delivers
     * it 30 to about 50 seconds after the copy. Copying again replaces it.
     */
    private fun scheduleClear(context: Context) {
        val clearIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ClipboardClearReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        context.getSystemService(AlarmManager::class.java).set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + CLEAR_AFTER_MS,
            clearIntent,
        )
    }

    private fun sensitiveExtraKey(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            // Same key, honoured by many keyboards before it became public API.
            "android.content.extra.IS_SENSITIVE"
        }
}

/** Receives the alarm set by [SecureClipboard] and empties the clipboard. */
class ClipboardClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Writing the clipboard is allowed from the background; only reading needs focus.
        context.getSystemService(ClipboardManager::class.java).clearPrimaryClip()
    }
}
