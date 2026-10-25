package app.airgap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.airgap.vault.Card
import app.airgap.vault.CardKind
import app.airgap.vault.CardNetwork
import app.airgap.vault.CardValidation
import java.util.UUID

// "Password" keyboard types tell the keyboard not to learn or suggest what's typed.
private val SecretDigits = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditScreen(
    existing: Card?,
    onSave: (Card) -> Unit,
    onDelete: (cardId: String) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)

    var nickname by remember { mutableStateOf(existing?.nickname.orEmpty()) }
    var holder by remember { mutableStateOf(existing?.holderName.orEmpty()) }
    var number by remember { mutableStateOf(existing?.number.orEmpty()) }
    var month by remember { mutableStateOf(existing?.expiryMonth?.toString()?.padStart(2, '0').orEmpty()) }
    var year by remember { mutableStateOf(existing?.expiryYear?.rem(100)?.toString()?.padStart(2, '0').orEmpty()) }
    var cvv by remember { mutableStateOf(existing?.cvv.orEmpty()) }
    var kind by remember { mutableStateOf(existing?.kind ?: CardKind.CREDIT) }
    var showErrors by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val network = CardNetwork.detect(number)
    val monthValue = month.toIntOrNull()?.takeIf { it in 1..12 }
    val yearValue = year.takeIf { it.length == 2 }?.toIntOrNull()?.let { 2000 + it }
    val numberError = if (CardValidation.isLuhnValid(number)) null else "Check the card number"
    val holderError = if (holder.isBlank()) "Required" else null
    val expiryError = if (monthValue == null || yearValue == null) "Enter as MM and YY" else null
    val cvvError = if (CardValidation.isCvvValid(cvv, network)) null else "${network.cvvLength} digits, or leave empty"

    fun save() {
        if (monthValue == null || yearValue == null || listOfNotNull(numberError, holderError, cvvError).isNotEmpty()) {
            showErrors = true
            return
        }
        onSave(
            Card(
                id = existing?.id ?: UUID.randomUUID().toString(),
                nickname = nickname.trim(),
                holderName = holder.trim(),
                number = number,
                expiryMonth = monthValue,
                expiryYear = yearValue,
                cvv = cvv,
                kind = kind,
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add card" else "Edit card") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } },
                actions = { TextButton(onClick = ::save) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(40) },
                label = { Text("Nickname (optional)") },
                placeholder = { Text("e.g. HDFC travel card") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = number,
                onValueChange = { number = CardValidation.digitsOnly(it).take(19) },
                label = { Text("Card number") },
                supportingText = {
                    Text(if (showErrors && numberError != null) numberError else network.displayName)
                },
                isError = showErrors && numberError != null,
                singleLine = true,
                keyboardOptions = SecretDigits,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = holder,
                onValueChange = { holder = it.take(60) },
                label = { Text("Name on card") },
                supportingText = if (showErrors && holderError != null) ({ Text(holderError) }) else null,
                isError = showErrors && holderError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = month,
                    onValueChange = { month = CardValidation.digitsOnly(it).take(2) },
                    label = { Text("MM") },
                    isError = showErrors && expiryError != null,
                    singleLine = true,
                    keyboardOptions = SecretDigits,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = CardValidation.digitsOnly(it).take(2) },
                    label = { Text("YY") },
                    isError = showErrors && expiryError != null,
                    singleLine = true,
                    keyboardOptions = SecretDigits,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = cvv,
                    onValueChange = { cvv = CardValidation.digitsOnly(it).take(4) },
                    label = { Text("CVV") },
                    isError = showErrors && cvvError != null,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = SecretDigits,
                    modifier = Modifier.weight(1f),
                )
            }
            if (showErrors && (expiryError != null || cvvError != null)) {
                Text(
                    listOfNotNull(expiryError, cvvError?.let { "CVV: $it" }).joinToString("\n"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "CVV is optional. Leave it empty if you'd rather not store it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardKind.entries.forEach { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { kind = option },
                        label = { Text(option.label) },
                    )
                }
            }
            if (existing != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete card", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this card?") },
            text = { Text("${existing.nickname.ifBlank { existing.network.displayName }} ending ${existing.last4} will be removed.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(existing.id) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
