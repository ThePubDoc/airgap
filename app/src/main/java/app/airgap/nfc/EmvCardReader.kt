package app.airgap.nfc

import app.airgap.vault.CardValidation
import java.security.SecureRandom
import java.time.LocalDateTime

/** What a contactless card tells any reader: number and expiry, sometimes the name. */
data class CardRead(
    val number: String,
    val expiryMonth: Int?,
    val expiryYear: Int?,
    val holderName: String?,
) {
    /** Kept free of card data so a stray log line can't leak it. */
    override fun toString() = "CardRead(••••${number.takeLast(4)})"
}

/**
 * Reads the card number and expiry from a contactless EMV card, the same public
 * data a shop terminal sees before any payment. No PIN, CVV or money is involved,
 * and nothing is sent anywhere: [transceive] talks to the card over NFC only.
 *
 * Steps: find the payment application (PPSE, or a list of known ones), select
 * it, ask for its processing options (some cards return the data right there),
 * then read the records the card points to.
 */
class EmvCardReader(private val transceive: (ByteArray) -> ByteArray) {

    fun read(): CardRead? {
        for (aid in applicationIds()) {
            val fci = select(aid) ?: continue
            readApplication(fci)?.let { return it }
        }
        return null
    }

    /** Payment applications on the card, from its PPSE directory, else the well-known ones. */
    private fun applicationIds(): List<ByteArray> {
        val listed = select(PPSE)?.let { ppse ->
            Tlv.findAll(ppse, 0x61)
                .mapNotNull { entry ->
                    val aid = Tlv.find(entry.children, 0x4F)?.value ?: return@mapNotNull null
                    val priority = Tlv.find(entry.children, 0x87)?.value?.firstOrNull()?.toInt()?.and(0x0F) ?: 15
                    priority to aid
                }
                .sortedBy { it.first }
                .map { it.second }
        }.orEmpty()
        return listed + KNOWN_AIDS.filter { known -> listed.none { it.contentEquals(known) } }
    }

    private fun readApplication(fci: List<TlvNode>): CardRead? {
        val found = Found()
        val pdol = Tlv.find(fci, 0x9F38)?.value
        val pdolData = pdol?.let { buildPdolData(Tlv.parseDol(it)) } ?: ByteArray(0)
        val gpo = command(
            byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0, 0, (pdolData.size + 2).toByte(), 0x83.toByte(), pdolData.size.toByte()) +
                pdolData + LE,
        )

        var afl: ByteArray? = null
        if (gpo != null) {
            val nodes = Tlv.parse(gpo)
            found.collect(nodes)
            // Format 2 has the AFL in tag 94; format 1 (tag 80) is the 2-byte AIP followed by the AFL.
            afl = Tlv.find(nodes, 0x94)?.value
                ?: Tlv.find(nodes, 0x80)?.value?.let { format1 -> if (format1.size > 2) format1.copyOfRange(2, format1.size) else null }
        }
        if (found.isComplete) return found.toCardRead()

        // Records the card points to (4 bytes each: SFI, first record, last record, count).
        afl?.toList()?.chunked(4)?.filter { it.size == 4 }?.forEach { entry ->
            val sfi = (entry[0].toInt() and 0xFF) shr 3
            val first = entry[1].toInt() and 0xFF
            val last = entry[2].toInt() and 0xFF
            for (record in first..last) {
                readRecord(sfi, record)?.let { found.collect(it) }
                if (found.isComplete) return found.toCardRead()
            }
        }
        // Some cards give no usable AFL; their data usually sits in the first few records.
        if (found.number == null) {
            for (sfi in 1..3) for (record in 1..4) {
                readRecord(sfi, record)?.let { found.collect(it) }
                if (found.isComplete) return found.toCardRead()
            }
        }
        return found.toCardRead()
    }

    private fun readRecord(sfi: Int, record: Int): List<TlvNode>? =
        command(byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), ((sfi shl 3) or 4).toByte(), 0x00))?.let(Tlv::parse)

    private fun select(aid: ByteArray): List<TlvNode>? =
        command(byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + LE)?.let(Tlv::parse)

    /** Sends a command and returns the reply data, or null unless the card answered 90 00. */
    private fun command(apdu: ByteArray): ByteArray? {
        var reply = transceive(apdu)
        repeat(3) {
            if (reply.size < 2) return null
            val sw1 = reply[reply.size - 2].toInt() and 0xFF
            val sw2 = reply[reply.size - 1]
            when (sw1) {
                // More data waiting: fetch it.
                0x61 -> reply = transceive(byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, sw2))
                // Wrong expected length: resend with the length the card asked for.
                0x6C -> reply = transceive(apdu.copyOf().also { it[it.size - 1] = sw2 })
                else -> return if (sw1 == 0x90 && sw2.toInt() == 0) reply.copyOf(reply.size - 2) else null
            }
        }
        return null
    }

    /** Terminal data the card asked for in its PDOL. Amount zero: nothing is being paid. */
    private fun buildPdolData(dol: List<Pair<Int, Int>>): ByteArray {
        val now = LocalDateTime.now()
        fun bcd(value: Int) = (((value / 10) shl 4) or (value % 10)).toByte()
        val terminal = mapOf(
            0x9F66 to byteArrayOf(0x36, 0x00, 0x40, 0x00), // Terminal transaction qualifiers: contactless EMV
            0x9F1A to byteArrayOf(0x03, 0x56), // Terminal country: India
            0x5F2A to byteArrayOf(0x03, 0x56), // Currency: INR
            0x9A to byteArrayOf(bcd(now.year % 100), bcd(now.monthValue), bcd(now.dayOfMonth)),
            0x9F21 to byteArrayOf(bcd(now.hour), bcd(now.minute), bcd(now.second)),
            0x9C to byteArrayOf(0x00), // Transaction type: purchase
            0x9F35 to byteArrayOf(0x22), // Terminal type: attended, online
            0x9F33 to byteArrayOf(0xE0.toByte(), 0xB0.toByte(), 0xC8.toByte()), // Terminal capabilities
            0x9F37 to ByteArray(4).also { SecureRandom().nextBytes(it) }, // Unpredictable number
        )
        return dol.fold(ByteArray(0)) { data, (tag, length) ->
            val value = terminal[tag] ?: ByteArray(length)
            data + if (value.size == length) value else value.copyOf(length)
        }
    }

    /** Collects the fields of interest across replies. */
    private class Found {
        var number: String? = null
        var month: Int? = null
        var year: Int? = null
        var name: String? = null

        val isComplete get() = number != null && month != null

        fun collect(nodes: List<TlvNode>) {
            // Track 2 equivalent: PAN, separator D, YYMM expiry.
            Tlv.find(nodes, 0x57)?.value?.toHex()?.let { track2 ->
                val separator = track2.indexOf('D')
                if (separator > 0) {
                    track2.substring(0, separator).takeIf(CardValidation::isLuhnValid)?.let { number = number ?: it }
                    parseYymm(track2.substring(separator + 1).take(4))
                }
            }
            Tlv.find(nodes, 0x5A)?.value?.toHex()?.trimEnd('F')?.takeIf(CardValidation::isLuhnValid)?.let { number = number ?: it }
            Tlv.find(nodes, 0x5F24)?.value?.toHex()?.let { parseYymm(it.take(4)) }
            Tlv.find(nodes, 0x5F20)?.value?.let { name = name ?: cleanName(String(it, Charsets.US_ASCII)) }
        }

        private fun parseYymm(yymm: String) {
            if (month != null || yymm.length < 4 || !yymm.all(Char::isDigit)) return
            val mm = yymm.substring(2, 4).toInt()
            if (mm in 1..12) {
                year = 2000 + yymm.substring(0, 2).toInt()
                month = mm
            }
        }

        fun toCardRead(): CardRead? = number?.let { CardRead(it, month, year, name) }
    }

    companion object {
        private val PPSE = "2PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII)

        /** "Return as much as you have": the trailing Le byte of a command. */
        private val LE = byteArrayOf(0x00)

        private val KNOWN_AIDS = listOf(
            "A0000000031010", // Visa
            "A0000000041010", // Mastercard
            "A0000000043060", // Maestro
            "A0000005241010", // RuPay
            "A00000002501", // American Express
            "A0000001523010", // Discover
            "A0000000651010", // JCB
        ).map(String::hexToBytes)

        /**
         * Cards store "SURNAME/GIVEN" or a placeholder. Returns "GIVEN SURNAME",
         * or null for placeholders like "CARDHOLDER/VISA" or a blank name.
         */
        internal fun cleanName(raw: String): String? {
            val trimmed = raw.trim().replace(Regex("\\s+"), " ")
            if (trimmed.isBlank() || trimmed == "/" || "CARDHOLDER" in trimmed.uppercase() || "VALUED" in trimmed.uppercase()) return null
            val parts = trimmed.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            return when (parts.size) {
                0 -> null
                1 -> parts[0]
                else -> "${parts[1]} ${parts[0]}"
            }
        }
    }
}
