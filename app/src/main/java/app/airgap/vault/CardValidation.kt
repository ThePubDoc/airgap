package app.airgap.vault

import java.time.YearMonth

enum class CardNetwork(val displayName: String, val cvvLength: Int) {
    VISA("Visa", 3),
    MASTERCARD("Mastercard", 3),
    AMEX("American Express", 4),
    RUPAY("RuPay", 3),
    DISCOVER("Discover", 3),
    DINERS("Diners Club", 3),
    JCB("JCB", 3),
    UNKNOWN("Card", 3);

    companion object {
        /** Best guess from the number's prefix. Only used for display and CVV length. */
        fun detect(number: String): CardNetwork {
            val digits = CardValidation.digitsOnly(number)
            fun prefix(length: Int) = digits.take(length).takeIf { it.length == length }?.toInt() ?: -1

            return when {
                digits.startsWith("4") -> VISA
                prefix(2) in 51..55 || prefix(4) in 2221..2720 -> MASTERCARD
                prefix(2) == 34 || prefix(2) == 37 -> AMEX
                prefix(4) == 6011 || prefix(3) in 644..649 -> DISCOVER
                prefix(4) == 6521 || prefix(4) == 6522 -> RUPAY
                prefix(2) == 60 || prefix(3) == 508 || prefix(2) == 81 || prefix(2) == 82 -> RUPAY
                prefix(2) == 65 -> DISCOVER
                prefix(2) == 36 || prefix(3) in 300..305 || prefix(2) == 38 || prefix(2) == 39 -> DINERS
                prefix(4) in 3528..3589 -> JCB
                else -> UNKNOWN
            }
        }
    }
}

object CardValidation {

    fun digitsOnly(input: String): String = input.filter { it in '0'..'9' }

    fun isLuhnValid(number: String): Boolean {
        val digits = digitsOnly(number)
        if (digits.length != number.length || digits.length !in 12..19) return false
        val sum = digits.reversed().mapIndexed { index, char ->
            val digit = char - '0'
            if (index % 2 == 1) (digit * 2).let { if (it > 9) it - 9 else it } else digit
        }.sum()
        return sum % 10 == 0
    }

    fun isExpired(month: Int, year: Int, now: YearMonth = YearMonth.now()): Boolean =
        YearMonth.of(year, month).isBefore(now)

    /** The CVV is optional; when present it must match the network's length. */
    fun isCvvValid(cvv: String, network: CardNetwork): Boolean =
        cvv.isEmpty() || (digitsOnly(cvv) == cvv && cvv.length == network.cvvLength)

    /** Where a space goes, as digit counts: 4-6-5 for Amex, fours for everything else. */
    fun groupBoundaries(number: String): List<Int> =
        if (CardNetwork.detect(number) == CardNetwork.AMEX) listOf(4, 10, 15) else listOf(4, 8, 12, 16)

    /** Groups digits the way they're printed, e.g. `4111 1111 1111 1111`. */
    fun formatNumber(number: String): String {
        val digits = digitsOnly(number)
        val spaces = spacesIn(digits)
        return buildString {
            digits.forEachIndexed { index, digit ->
                if (index in spaces) append(' ')
                append(digit)
            }
        }
    }

    /** Cursor position in [formatNumber]'s output for a position in the raw digits. */
    fun formattedOffset(digits: String, digitOffset: Int): Int =
        digitOffset + spacesIn(digits).count { it < digitOffset }

    /** Cursor position in the raw digits for a position in [formatNumber]'s output. */
    fun digitOffset(digits: String, formattedOffset: Int): Int {
        // The k-th space sits at index boundary + k in the formatted text.
        val spacesBefore = spacesIn(digits).withIndex().count { (k, boundary) -> boundary + k < formattedOffset }
        return (formattedOffset - spacesBefore).coerceIn(0, digits.length)
    }

    private fun spacesIn(digits: String) = groupBoundaries(digits).filter { it < digits.length }

    fun mask(number: String): String = "•••• " + number.takeLast(4)

    fun formatExpiry(month: Int, year: Int): String =
        month.toString().padStart(2, '0') + "/" + (year % 100).toString().padStart(2, '0')
}
