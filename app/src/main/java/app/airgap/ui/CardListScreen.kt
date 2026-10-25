package app.airgap.ui

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
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
        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No cards yet.\nTap Add card to store your first one.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                // Bottom padding keeps the last card clear of the floating button.
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(cards, key = { it.id }) { card ->
                    CardItem(card = card, onEdit = { onEdit(card) }, onCopy = onCopy)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardItem(card: Card, onEdit: () -> Unit, onCopy: (String, String) -> Unit) {
    // Details stay hidden until tapped, so the list is safe to open in public.
    var revealed by remember { mutableStateOf(false) }
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
                }
            }
        }
    }
}

/** Light red that stays readable on every tile colour. */
private val ExpiredOnCard = Color(0xFFFFB4AB)

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
