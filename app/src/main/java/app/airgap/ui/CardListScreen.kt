package app.airgap.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.airgap.vault.Card
import app.airgap.vault.CardValidation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardListScreen(
    cards: List<Card>,
    onAdd: () -> Unit,
    onEdit: (Card) -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    onLock: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Airgap") },
                actions = {
                    PayButton()
                    TextButton(onClick = onLock) { Text("Lock") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) { Text("Add card") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // Bottom padding keeps the last card clear of the floating button.
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "autofill") { AutofillBanner() }
            if (cards.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No cards yet.\nTap Add card to store your first one.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 160.dp),
                    )
                }
            }
            items(cards, key = { it.id }) { card ->
                CardItem(card = card, onEdit = { onEdit(card) }, onCopy = onCopy)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardItem(card: Card, onEdit: () -> Unit, onCopy: (String, String) -> Unit) {
    // Details stay hidden until tapped, so the list is safe to open in public.
    var revealed by remember { mutableStateOf(false) }
    var lostCardOpen by remember { mutableStateOf(false) }
    val expiry = CardValidation.formatExpiry(card.expiryMonth, card.expiryYear)
    val expired = CardValidation.isExpired(card.expiryMonth, card.expiryYear)

    ElevatedCard(
        onClick = { revealed = !revealed },
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
            Spacer(Modifier.height(12.dp))
            Text(
                if (revealed) CardValidation.formatNumber(card.number) else CardValidation.mask(card.number),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Row {
                Text(card.holderName.uppercase(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    if (expired) "$expiry · Expired" else expiry,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (expired) ExpiredOnCard else Color.White,
                )
            }
            if (revealed) {
                card.dueDay?.let { day ->
                    Spacer(Modifier.height(4.dp))
                    Text("Bill due on the ${ordinal(day)}", style = MaterialTheme.typography.bodyMedium)
                }
                if (card.cvv.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("CVV ${card.cvv}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(8.dp))
                val onCardButton = ButtonDefaults.textButtonColors(contentColor = Color.White)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onCopy("Card number", card.number) }, colors = onCardButton) { Text("Copy number") }
                    TextButton(onClick = { onCopy("Expiry", expiry) }, colors = onCardButton) { Text("Copy expiry") }
                    if (card.cvv.isNotEmpty()) {
                        TextButton(onClick = { onCopy("CVV", card.cvv) }, colors = onCardButton) { Text("Copy CVV") }
                    }
                    TextButton(onClick = { onCopy("Name on card", card.holderName) }, colors = onCardButton) { Text("Copy name") }
                    TextButton(onClick = onEdit, colors = onCardButton) { Text("Edit") }
                    TextButton(onClick = { lostCardOpen = true }, colors = onCardButton) { Text("Lost card?") }
                }
            }
        }
    }

    if (lostCardOpen) LostCardDialog(card, onDismiss = { lostCardOpen = false })
}

/** Light red that stays readable on every tile colour. */
private val ExpiredOnCard = Color(0xFFFFB4AB)

/**
 * Quick ways to block a lost or stolen card. Opens the dialler or the SMS app with
 * everything filled in; the person still presses call or send, so Airgap needs no
 * phone or SMS permission.
 */
@Composable
private fun LostCardDialog(card: Card, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val smsText = card.blockSmsText.ifBlank { card.defaultBlockSms }
    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
            onDismiss()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app on this phone can do that.", Toast.LENGTH_SHORT).show()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lost or stolen card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Block ${card.label} as soon as you can.")
                if (card.helpline.isNotBlank()) {
                    OutlinedButton(
                        onClick = { open(Intent(Intent.ACTION_DIAL, ("tel:" + Uri.encode(card.helpline)).toUri())) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Call bank: ${card.helpline}") }
                }
                if (card.blockSmsNumber.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            open(
                                Intent(Intent.ACTION_SENDTO, ("smsto:" + Uri.encode(card.blockSmsNumber)).toUri())
                                    .putExtra("sms_body", smsText),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Text \"$smsText\" to ${card.blockSmsNumber}") }
                }
                if (card.helpline.isBlank() && card.blockSmsNumber.isBlank()) {
                    Text(
                        "No bank numbers saved for this card yet. Add the helpline and block-by-SMS number under Edit, " +
                            "so they're here when you need them.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** 1st, 2nd, 3rd, 4th … 11th, 12th, 13th … 21st. */
private fun ordinal(day: Int): String {
    val suffix = if (day % 100 in 11..13) "th" else when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}

/** Opens a wallet app for tap-to-pay. Hidden when none is installed. */
@Composable
private fun PayButton() {
    val context = LocalContext.current
    val apps = remember { PaymentApps.installed(context) }
    var menuOpen by remember { mutableStateOf(false) }
    when (apps.size) {
        0 -> Unit
        1 -> TextButton(onClick = { PaymentApps.open(context, apps.single()) }) { Text("Pay") }
        else -> Box {
            TextButton(onClick = { menuOpen = true }) { Text("Pay") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                apps.forEach { app ->
                    DropdownMenuItem(
                        text = { Text(app.name) },
                        onClick = {
                            menuOpen = false
                            PaymentApps.open(context, app)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Invites the person to turn on card autofill for checkout forms. Hidden once
 * Airgap is the autofill service, or after "Not now".
 */
@Composable
private fun AutofillBanner() {
    val context = LocalContext.current
    val autofill = remember { context.getSystemService(AutofillManager::class.java) }
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(autofill?.hasEnabledAutofillServices() == true) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean(KEY_AUTOFILL_DISMISSED, false)) }
    // Re-check on return from the system's "use Airgap for autofill?" screen.
    LifecycleResumeEffect(Unit) {
        enabled = autofill?.hasEnabledAutofillServices() == true
        onPauseOrDispose { }
    }
    if (enabled || dismissed || autofill?.isAutofillSupported != true) return

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Fill cards in online checkout", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Airgap can fill card details into checkout forms in Chrome and other apps, after your fingerprint. " +
                    "Android allows one autofill app at a time, so this replaces Samsung Pass or Google autofill for passwords.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    prefs.edit { putBoolean(KEY_AUTOFILL_DISMISSED, true) }
                    dismissed = true
                }) { Text("Not now") }
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, "package:${context.packageName}".toUri()),
                    )
                }) { Text("Turn on") }
            }
        }
    }
}

private const val PREFS = "airgap_prefs"
private const val KEY_AUTOFILL_DISMISSED = "autofill_banner_dismissed"
