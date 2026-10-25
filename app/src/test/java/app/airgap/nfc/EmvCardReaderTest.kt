package app.airgap.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmvCardReaderTest {

    /** Encodes one TLV object; [value] is raw bytes or nested objects. */
    private fun tlv(tag: String, value: ByteArray): ByteArray {
        val length = if (value.size < 0x80) byteArrayOf(value.size.toByte()) else byteArrayOf(0x81.toByte(), value.size.toByte())
        return tag.hexToBytes() + length + value
    }
    private fun tlv(tag: String, hex: String) = tlv(tag, hex.hexToBytes())
    private fun tlv(tag: String, vararg children: ByteArray) = tlv(tag, children.fold(ByteArray(0)) { a, b -> a + b })
    private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)
    private val ok = "9000".hexToBytes()
    private val notFound = "6A82".hexToBytes()

    /** A scripted card: replies by command prefix, "file not found" otherwise. */
    private class FakeCard(private val script: List<Pair<String, ByteArray>>) {
        val sent = mutableListOf<String>()
        fun transceive(apdu: ByteArray): ByteArray {
            val hex = apdu.toHex()
            sent += hex
            return script.firstOrNull { hex.startsWith(it.first) }?.second ?: "6A82".hexToBytes()
        }
    }

    @Test
    fun `visa card - PPSE, PDOL, data in records`() {
        val visa = "A0000000031010"
        val card = FakeCard(listOf(
            "00A404000E" + ascii("2PAY.SYS.DDF01").toHex() to tlv("6F",
                tlv("84", ascii("2PAY.SYS.DDF01")),
                tlv("A5", tlv("BF0C", tlv("61", tlv("4F", visa), tlv("87", "01"))))) + ok,
            "00A4040007$visa" to tlv("6F", tlv("84", visa), tlv("A5", tlv("50", ascii("VISA")), tlv("9F38", "9F66049F02069F3704"))) + ok,
            "80A80000" to tlv("77", tlv("82", "2000"), tlv("94", "08010100")) + ok,
            "00B2010C" to tlv("70",
                tlv("57", "4111111111111111D29032011234567890000F"),
                tlv("5F20", ascii("AGRAWAL/AAYUSH"))) + ok,
        ))
        val read = EmvCardReader(card::transceive).read()
        assertEquals(CardRead("4111111111111111", 3, 2029, "AAYUSH AGRAWAL"), read)
        // PDOL asked for 4 + 6 + 4 bytes, so the GPO carries 14 bytes of terminal data.
        val gpo = card.sent.first { it.startsWith("80A8") }
        assertEquals("80A8000010830E", gpo.take(14))
    }

    @Test
    fun `mastercard - no PPSE, format 1 GPO, PAN and expiry tags, placeholder name`() {
        val mastercard = "A0000000041010"
        val card = FakeCard(listOf(
            "00A404000E" to notFound,
            "00A4040007$mastercard" to tlv("6F", tlv("84", mastercard), tlv("A5", tlv("50", ascii("MASTERCARD")))) + ok,
            "80A80000" to tlv("80", "1980" + "10010200") + ok,
            "00B20114" to tlv("70", tlv("5A", "5555555555554444"), tlv("5F24", "280531"), tlv("5F20", ascii("CARDHOLDER/MASTERCARD"))) + ok,
        ))
        assertEquals(CardRead("5555555555554444", 5, 2028, null), EmvCardReader(card::transceive).read())
    }

    @Test
    fun `visa contactless - track 2 already in the GPO reply`() {
        val visa = "A0000000031010"
        val card = FakeCard(listOf(
            "00A4040007$visa" to tlv("6F", tlv("84", visa)) + ok,
            "80A80000" to tlv("77", tlv("82", "0000"), tlv("57", "4012888888881881D30122011000000000000F")) + ok,
        ))
        val read = EmvCardReader(card::transceive).read()
        assertEquals(CardRead("4012888888881881", 12, 2030, null), read)
        assertTrue("should not read records when the GPO had everything", card.sent.none { it.startsWith("00B2") })
    }

    @Test
    fun `follows 61xx by fetching the rest of the reply`() {
        val rupay = "A0000005241010"
        val fci = tlv("6F", tlv("84", rupay))
        val card = FakeCard(listOf(
            "00A4040007$rupay" to "611A".hexToBytes(),
            "00C000001A" to fci + ok,
            "80A80000" to tlv("77", tlv("57", "6521000000000007D27082010000000000000F")) + ok,
        ))
        assertEquals(CardRead("6521000000000007", 8, 2027, null), EmvCardReader(card::transceive).read())
    }

    @Test
    fun `unreadable card gives nothing`() {
        assertNull(EmvCardReader(FakeCard(emptyList())::transceive).read())
    }

    @Test
    fun `names are tidied and placeholders dropped`() {
        assertEquals("AAYUSH AGRAWAL", EmvCardReader.cleanName("AGRAWAL/AAYUSH  "))
        assertEquals("AAYUSH AGRAWAL", EmvCardReader.cleanName("AAYUSH AGRAWAL"))
        assertNull(EmvCardReader.cleanName(" / "))
        assertNull(EmvCardReader.cleanName("CARDHOLDER/VISA"))
        assertNull(EmvCardReader.cleanName("VALUED CUSTOMER"))
        assertNull(EmvCardReader.cleanName("   "))
    }

    @Test
    fun `tlv parser handles multi-byte tags, long lengths and padding`() {
        val nodes = Tlv.parse(("00" + "BF0C" + "03" + "870101" + "FF" + "5F20" + "81" + "02" + "4142").hexToBytes())
        assertEquals("01", Tlv.find(nodes, 0x87)!!.value.toHex())
        assertEquals("AB", String(Tlv.find(nodes, 0x5F20)!!.value))
        assertEquals(listOf(0x9F66 to 4, 0x9F02 to 6, 0x95 to 5), Tlv.parseDol("9F66049F0206 9505".hexToBytes()))
    }
}
