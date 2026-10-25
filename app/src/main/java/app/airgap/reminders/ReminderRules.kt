package app.airgap.reminders

import app.airgap.vault.Card
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The little a reminder needs to know about a card. Deliberately no card
 * number or CVV: reminders run in the background, where the vault stays locked.
 */
@Serializable
data class ReminderCard(
    val cardId: String,
    val label: String,
    val dueDay: Int? = null,
    val expiryMonth: Int,
    val expiryYear: Int,
) {
    companion object {
        fun from(card: Card) = ReminderCard(card.id, card.label, card.dueDay, card.expiryMonth, card.expiryYear)
    }
}

/** One notification to show. [key] makes sure each one is shown only once. */
data class Reminder(val key: String, val title: String, val text: String)

object ReminderRules {
    const val DAYS_BEFORE_DUE = 3

    /**
     * Reminders that apply today. Each covers a window rather than a single day
     * (bill: the 3 days before; expiry: from the start of the month before), so
     * one missed daily check, phone off say, doesn't lose the reminder.
     */
    fun due(cards: List<ReminderCard>, today: LocalDate): List<Reminder> =
        cards.flatMap { billReminders(it, today) + expiryReminders(it, today) }

    private fun billReminders(card: ReminderCard, today: LocalDate): List<Reminder> {
        val day = card.dueDay?.takeIf { it in 1..31 } ?: return emptyList()
        val thisMonth = YearMonth.from(today)
        // Next month too: a bill due on the 1st is reminded about at the end of this month.
        return listOf(thisMonth, thisMonth.plusMonths(1)).mapNotNull { month ->
            val dueDate = month.atDay(minOf(day, month.lengthOfMonth()))
            when (val daysLeft = ChronoUnit.DAYS.between(today, dueDate)) {
                0L -> Reminder(
                    key = "due-today:${card.cardId}:$dueDate",
                    title = "Card bill due today",
                    text = "${card.label}: pay today to avoid late fees.",
                )
                in 1..DAYS_BEFORE_DUE -> Reminder(
                    key = "due-soon:${card.cardId}:$dueDate",
                    title = "Card bill due ${dueDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))}",
                    text = "${card.label}: payment due in $daysLeft ${if (daysLeft == 1L) "day" else "days"}.",
                )
                else -> null
            }
        }
    }

    private fun expiryReminders(card: ReminderCard, today: LocalDate): List<Reminder> {
        val expiry = YearMonth.of(card.expiryYear, card.expiryMonth)
        if (today.isBefore(expiry.minusMonths(1).atDay(1)) || today.isAfter(expiry.atEndOfMonth())) return emptyList()
        val monthName = expiry.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        return listOf(
            Reminder(
                key = "expiry:${card.cardId}:$expiry",
                title = "Card expiring soon",
                text = "${card.label} expires at the end of $monthName ${expiry.year}. Check that your replacement has arrived.",
            ),
        )
    }
}
