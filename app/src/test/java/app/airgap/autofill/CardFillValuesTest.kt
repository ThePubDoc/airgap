package app.airgap.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardFillValuesTest {

    @Test
    fun `combined expiry follows the field's length and placeholder`() {
        assertEquals("03/29", CardFillValues.expiryText(3, 2029, maxLength = -1, hint = ""))
        assertEquals("03/29", CardFillValues.expiryText(3, 2029, maxLength = 5, hint = "MM/YY"))
        assertEquals("0329", CardFillValues.expiryText(3, 2029, maxLength = 4, hint = ""))
        assertEquals("03/2029", CardFillValues.expiryText(3, 2029, maxLength = 7, hint = ""))
        assertEquals("03/2029", CardFillValues.expiryText(3, 2029, maxLength = -1, hint = "MM/YYYY"))
        assertEquals("032029", CardFillValues.expiryText(3, 2029, maxLength = 6, hint = ""))
    }

    @Test
    fun `separate year uses two digits only when asked`() {
        assertEquals("2029", CardFillValues.yearText(2029, maxLength = -1, hint = ""))
        assertEquals("29", CardFillValues.yearText(2029, maxLength = 2, hint = ""))
        assertEquals("29", CardFillValues.yearText(2029, maxLength = -1, hint = "YY"))
        assertEquals("2029", CardFillValues.yearText(2029, maxLength = 2, hint = "YYYY"))
        assertEquals("08", CardFillValues.monthText(8))
    }

    @Test
    fun `drop-down options are matched in their common shapes`() {
        assertEquals(3, CardFillValues.monthOption(listOf("Month", "01", "02", "03", "04"), 3))
        assertEquals(3, CardFillValues.monthOption(listOf("--", "1", "2", "3", "10", "11"), 3))
        assertEquals(4, CardFillValues.monthOption(listOf("--", "1", "2", "3", "10"), 10))
        assertEquals(2, CardFillValues.monthOption(listOf("Select", "January", "February"), 2))
        assertEquals(3, CardFillValues.monthOption(listOf("", "Jan", "Feb", "Mar"), 3))
        assertEquals(1, CardFillValues.monthOption(listOf("Month", "03 - March", "04 - April"), 3))
        assertEquals(2, CardFillValues.yearOption(listOf("Year", "2028", "2029"), 2029))
        assertEquals(2, CardFillValues.yearOption(listOf("YY", "28", "29"), 2029))
        assertNull(CardFillValues.yearOption(listOf("2024", "2025"), 2029))
    }
}
