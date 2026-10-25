package app.airgap.autofill

import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Formats expiry dates the way a given field expects. A form's max length and
 * placeholder ("MM/YYYY", "YY") are the only clues, so these are best guesses
 * that match the common checkout layouts.
 */
object CardFillValues {

    /** A combined expiry field: "MM/YY" unless the field says otherwise. */
    fun expiryText(month: Int, year: Int, maxLength: Int, hint: String): String {
        val fourDigitYear = "yyyy" in hint.lowercase() || maxLength == 6 || maxLength == 7
        val separator = if (maxLength == 4 || maxLength == 6) "" else "/"
        return monthText(month) + separator + yearText(year, fourDigitYear)
    }

    fun monthText(month: Int): String = month.toString().padStart(2, '0')

    /** A separate year field: two digits only when the field clearly wants them. */
    fun yearText(year: Int, maxLength: Int, hint: String): String {
        val lower = hint.lowercase()
        val twoDigits = when {
            "yyyy" in lower -> false
            "yy" in lower -> true
            else -> maxLength == 2
        }
        return yearText(year, fourDigitYear = !twoDigits)
    }

    /** Index of the drop-down option for this month, e.g. "03", "3", "Mar" or "March". */
    fun monthOption(options: List<String>, month: Int): Int? {
        val name = Month.of(month)
        val candidates = listOf(monthText(month), month.toString()) +
            listOf(Locale.ENGLISH, Locale.getDefault()).flatMap { locale ->
                listOf(name.getDisplayName(TextStyle.FULL, locale), name.getDisplayName(TextStyle.SHORT, locale))
            }
        return optionIndex(options, candidates)
    }

    /** Index of the drop-down option for this year, e.g. "2029" or "29". */
    fun yearOption(options: List<String>, year: Int): Int? =
        optionIndex(options, listOf(year.toString(), (year % 100).toString().padStart(2, '0')))

    private fun yearText(year: Int, fourDigitYear: Boolean): String =
        if (fourDigitYear) year.toString() else (year % 100).toString().padStart(2, '0')

    /** Exact match first, then an option that starts with a candidate, like "03 - March". */
    private fun optionIndex(options: List<String>, candidates: List<String>): Int? {
        val trimmed = options.map { it.trim().lowercase() }
        val wanted = candidates.map { it.lowercase() }
        trimmed.indexOfFirst { it in wanted }.takeIf { it >= 0 }?.let { return it }
        return trimmed.indexOfFirst { option ->
            wanted.any { candidate ->
                option.startsWith(candidate) && option.getOrNull(candidate.length)?.isLetterOrDigit() != true
            }
        }.takeIf { it >= 0 }
    }
}
