package app.airgap.vault

import kotlinx.serialization.Serializable

@Serializable
enum class CardKind(val label: String) {
    CREDIT("Credit"),
    DEBIT("Debit"),
}

@Serializable
data class Card(
    val id: String,
    val nickname: String,
    val holderName: String,
    /** Digits only, no spaces. */
    val number: String,
    val expiryMonth: Int,
    /** Four-digit year. */
    val expiryYear: Int,
    /** Empty when the user chose not to store it. */
    val cvv: String = "",
    val kind: CardKind,
) {
    val network: CardNetwork get() = CardNetwork.detect(number)

    val last4: String get() = number.takeLast(4)

    /** Kept free of card data so a stray log line or crash report can't leak it. */
    override fun toString() = "Card(id=$id, ${network.displayName} ••••$last4)"
}

/** The whole vault, as serialized before encryption. */
@Serializable
data class VaultContents(
    val version: Int = 1,
    val cards: List<Card> = emptyList(),
)
