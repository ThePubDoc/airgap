package app.airgap.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import app.airgap.vault.Card
import java.time.LocalDate
import java.time.ZoneOffset

/** One fillable field in the requesting app's form. */
class FieldTarget(
    val id: AutofillId,
    val field: CardField,
    val autofillType: Int,
    val maxLength: Int,
    val hint: String,
    val options: List<String>,
)

/** The card fields found in a form, and who is asking. */
class CardForm(
    val targets: List<FieldTarget>,
    val packageName: String,
    val webDomain: String?,
) {
    val ids: Array<AutofillId> get() = targets.map { it.id }.toTypedArray()

    /** Where the card would go, for the person to check before filling. */
    val requester: String get() = webDomain ?: packageName

    /** A dataset that fills [card] into every field it has a value for, or null if none. */
    fun datasetFor(card: Card): Dataset? {
        val values = targets.mapNotNull { target -> valueFor(target, card)?.let { target.id to it } }
        if (values.isEmpty()) return null
        val builder = Dataset.Builder()
        values.forEach { (id, value) ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder.setField(id, Field.Builder().setValue(value).build())
            } else {
                @Suppress("DEPRECATION")
                builder.setValue(id, value)
            }
        }
        return builder.build()
    }

    private fun valueFor(target: FieldTarget, card: Card): AutofillValue? {
        val type = target.autofillType
        return when (target.field) {
            CardField.NUMBER -> text(type, card.number)
            CardField.HOLDER_NAME -> text(type, card.holderName)
            CardField.SECURITY_CODE -> card.cvv.takeIf { it.isNotEmpty() }?.let { text(type, it) }
            CardField.EXPIRY_DATE -> when (type) {
                View.AUTOFILL_TYPE_TEXT -> AutofillValue.forText(
                    CardFillValues.expiryText(card.expiryMonth, card.expiryYear, target.maxLength, target.hint),
                )
                View.AUTOFILL_TYPE_DATE -> AutofillValue.forDate(
                    LocalDate.of(card.expiryYear, card.expiryMonth, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                )
                else -> null
            }
            CardField.EXPIRY_MONTH -> when (type) {
                View.AUTOFILL_TYPE_TEXT -> AutofillValue.forText(CardFillValues.monthText(card.expiryMonth))
                View.AUTOFILL_TYPE_LIST -> CardFillValues.monthOption(target.options, card.expiryMonth)?.let(AutofillValue::forList)
                else -> null
            }
            CardField.EXPIRY_YEAR -> when (type) {
                View.AUTOFILL_TYPE_TEXT -> AutofillValue.forText(CardFillValues.yearText(card.expiryYear, target.maxLength, target.hint))
                View.AUTOFILL_TYPE_LIST -> CardFillValues.yearOption(target.options, card.expiryYear)?.let(AutofillValue::forList)
                else -> null
            }
        }
    }

    private fun text(type: Int, value: String) =
        if (type == View.AUTOFILL_TYPE_TEXT) AutofillValue.forText(value) else null

    companion object {
        /**
         * Finds the card fields in a form. Returns an empty form unless there's a card
         * number or security code, so a lone "month" or "name" field elsewhere never
         * triggers Airgap.
         */
        fun parse(structure: AssistStructure): CardForm {
            val targets = mutableListOf<FieldTarget>()
            var webDomain: String? = null

            fun visit(node: AssistStructure.ViewNode) {
                if (webDomain == null) webDomain = node.webDomain?.takeIf { it.isNotBlank() }
                val id = node.autofillId
                if (id != null && node.autofillType != View.AUTOFILL_TYPE_NONE && targets.none { it.id == id }) {
                    val html = node.htmlInfo?.attributes.orEmpty().associate { it.first.lowercase() to it.second }
                    val field = CardFieldClassifier.classify(
                        hints = node.autofillHints?.toList().orEmpty(),
                        autocomplete = html["autocomplete"],
                        texts = listOfNotNull(
                            node.idEntry,
                            node.hint,
                            node.contentDescription?.toString(),
                            html["name"],
                            html["id"],
                            html["placeholder"],
                            html["aria-label"],
                            html["label"],
                        ),
                    )
                    if (field != null) {
                        targets += FieldTarget(
                            id = id,
                            field = field,
                            autofillType = node.autofillType,
                            maxLength = html["maxlength"]?.toIntOrNull() ?: node.maxTextLength.takeIf { it > 0 } ?: -1,
                            hint = listOfNotNull(node.hint, html["placeholder"]).joinToString(" "),
                            options = node.autofillOptions?.map { it.toString() }.orEmpty(),
                        )
                    }
                }
                for (i in 0 until node.childCount) visit(node.getChildAt(i))
            }

            for (i in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(i).rootViewNode)

            val isCardForm = targets.any { it.field == CardField.NUMBER || it.field == CardField.SECURITY_CODE }
            return CardForm(
                targets = if (isCardForm) targets else emptyList(),
                packageName = structure.activityComponent?.packageName.orEmpty(),
                webDomain = webDomain,
            )
        }
    }
}
