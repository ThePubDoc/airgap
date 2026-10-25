package app.airgap.vault

import kotlinx.serialization.Serializable

@Serializable
enum class CardKind(val label: String) {
    CREDIT("Credit"),
    DEBIT("Debit"),
}

/** Tile colours. All dark enough for white text; picked to look like real cards. */
@Serializable
enum class CardColor(val label: String, val argb: Long) {
    GRAPHITE("Graphite", 0xFF2B2F36),
    NAVY("Navy", 0xFF1F3A68),
    TEAL("Teal", 0xFF0F5E5C),
    PLUM("Plum", 0xFF5B2A62),
    CRIMSON("Crimson", 0xFF8A1C2B),
    BRONZE("Bronze", 0xFF7A5418),
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
    val color: CardColor = CardColor.GRAPHITE,
    /** Day of the month the bill is due (credit cards), for reminders. */
    val dueDay: Int? = null,
    /** The bank's number to call if the card is lost or stolen. */
    val helpline: String = "",
    /** Where to text to block the card, and what to send (blank means [defaultBlockSms]). */
    val blockSmsNumber: String = "",
    val blockSmsText: String = "",
) {
    val network: CardNetwork get() = CardNetwork.detect(number)

    val last4: String get() = number.takeLast(4)

    /** The most common format among Indian banks; the person can change it per card. */
    val defaultBlockSms: String get() = "BLOCK $last4"

    /** Shown in reminders and dialogs: nickname, or network, plus the last four digits. */
    val label: String get() = "${nickname.ifBlank { network.displayName }} •• $last4"

    /** Kept free of card data so a stray log line or crash report can't leak it. */
    override fun toString() = "Card(id=$id, ${network.displayName} ••••$last4)"
}

/** The whole vault, as serialized before encryption. */
@Serializable
data class VaultContents(
    val version: Int = 1,
    val cards: List<Card> = emptyList(),
)
