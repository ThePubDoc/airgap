package app.airgap.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.Presentations
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.widget.RemoteViews
import app.airgap.R

/**
 * Offers to fill card details into checkout forms in other apps and browsers.
 *
 * The service never touches the vault. It only spots card fields and shows a
 * "Fill card with Airgap" suggestion; tapping it opens [AutofillActivity], which
 * asks for the fingerprint, lets the person pick a card, and returns the values.
 */
class CardAutofillService : AutofillService() {

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val form = structure?.let(CardForm::parse)
        if (form == null || form.targets.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        // The system adds the form's structure to this intent before starting the
        // activity, so the PendingIntent has to stay mutable.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val unlock = PendingIntent.getActivity(
            this,
            request.id,
            Intent(this, AutofillActivity::class.java),
            PendingIntent.FLAG_CANCEL_CURRENT or mutable,
        ).intentSender

        // One locked suggestion with empty values. Locking the suggestion itself (not the
        // whole response) lets AutofillActivity hand back a filled-in dataset directly.
        val suggestion = RemoteViews(packageName, R.layout.autofill_suggestion)
        val locked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Dataset.Builder(Presentations.Builder().setMenuPresentation(suggestion).build()).apply {
                form.ids.forEach { setField(it, null) }
            }
        } else {
            @Suppress("DEPRECATION")
            Dataset.Builder(suggestion).apply {
                form.ids.forEach { setValue(it, null) }
            }
        }.setAuthentication(unlock).build()
        val response = FillResponse.Builder().addDataset(locked).build()
        callback.onSuccess(response)
    }

    /** Airgap never saves cards typed into other apps. */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }
}
