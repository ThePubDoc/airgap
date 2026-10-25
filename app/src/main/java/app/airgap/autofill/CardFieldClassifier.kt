package app.airgap.autofill

/** A card detail a form field can hold. */
enum class CardField { NUMBER, EXPIRY_DATE, EXPIRY_MONTH, EXPIRY_YEAR, SECURITY_CODE, HOLDER_NAME }

/**
 * Works out which card detail a form field wants. Explicit markup wins: Android
 * autofill hints, then the web `autocomplete` attribute. Apps that label nothing
 * fall back to matching the field's id, hint and name text.
 */
object CardFieldClassifier {

    fun classify(hints: List<String>, autocomplete: String?, texts: List<String>): CardField? {
        for (raw in hints + listOfNotNull(autocomplete)) {
            // autocomplete can hold several tokens, e.g. "billing cc-number".
            raw.trim().lowercase().split(Regex("\\s+")).forEach { token ->
                HINTS[token]?.let { return it }
            }
        }
        val text = texts.joinToString(" ").lowercase()
        if (text.isBlank()) return null
        return HEURISTICS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second
    }

    private val HINTS = mapOf(
        // Android View.AUTOFILL_HINT_* values, lowercased.
        "creditcardnumber" to CardField.NUMBER,
        "creditcardsecuritycode" to CardField.SECURITY_CODE,
        "creditcardexpirationdate" to CardField.EXPIRY_DATE,
        "creditcardexpirationmonth" to CardField.EXPIRY_MONTH,
        "creditcardexpirationyear" to CardField.EXPIRY_YEAR,
        // HTML autocomplete tokens.
        "cc-number" to CardField.NUMBER,
        "cc-csc" to CardField.SECURITY_CODE,
        "cc-exp" to CardField.EXPIRY_DATE,
        "cc-exp-month" to CardField.EXPIRY_MONTH,
        "cc-exp-year" to CardField.EXPIRY_YEAR,
        "cc-name" to CardField.HOLDER_NAME,
    )

    // Separators include "_" because ids look like "et_card_number". Short tokens are
    // bounded by non-letters, not \b, for the same reason. Order matters: the more
    // specific patterns ("expiry month") must be checked before the general ones.
    private const val SEP = "[\\W_]*"
    private val HEURISTICS = listOf(
        Regex("(?<![a-z])(cvv2?|cvc2?|csc)(?![a-z])|security${SEP}code|verification${SEP}(code|value|number)") to CardField.SECURITY_CODE,
        Regex("mm$SEP/${SEP}yy|exp\\w*${SEP}date|valid$SEP(thru|through|till|until)") to CardField.EXPIRY_DATE,
        Regex("exp\\w*${SEP}(month|mm)|(?<![a-z])(month|mm)(?![a-z])") to CardField.EXPIRY_MONTH,
        Regex("exp\\w*${SEP}(year|yy)|(?<![a-z])(year|yyyy|yy)(?![a-z])") to CardField.EXPIRY_YEAR,
        Regex("(?<![a-z])exp(iry|iration)?(?![a-z])") to CardField.EXPIRY_DATE,
        Regex("name${SEP}on${SEP}card|card${SEP}holder|cc${SEP}name|holder${SEP}name") to CardField.HOLDER_NAME,
        Regex("card$SEP(no(?![a-z])|num)|cc$SEP(no(?![a-z])|num)|(?<![a-z])pan(?![a-z])") to CardField.NUMBER,
    )
}
