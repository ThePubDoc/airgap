package app.airgap.ui

import android.content.Context
import android.content.Intent

/**
 * Wallet apps that can make contactless payments, which Airgap itself can't (see
 * README). Airgap only opens them; nothing is shared with them. Each package must
 * also be listed under <queries> in the manifest, or Android hides it from us.
 */
data class PaymentApp(val name: String, val packageName: String)

object PaymentApps {
    private val known = listOf(
        PaymentApp("Google Wallet", "com.google.android.apps.walletnfcrel"),
        PaymentApp("Samsung Wallet", "com.samsung.android.spay"),
    )

    fun installed(context: Context): List<PaymentApp> =
        known.filter { context.packageManager.getLaunchIntentForPackage(it.packageName) != null }

    fun open(context: Context, app: PaymentApp) {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
