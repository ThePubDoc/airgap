package app.airgap.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderRulesTest {

    private fun card(dueDay: Int? = null, expiry: Pair<Int, Int> = 12 to 2030) =
        ReminderCard("c1", "HDFC •• 1111", dueDay, expiry.first, expiry.second)

    private fun keysOn(date: String, card: ReminderCard) = ReminderRules.due(listOf(card), LocalDate.parse(date)).map { it.key }

    @Test
    fun `bill reminder in the 3 days before and on the day`() {
        val c = card(dueDay = 20)
        assertEquals(emptyList<String>(), keysOn("2026-10-16", c))
        assertEquals(listOf("due-soon:c1:2026-10-20"), keysOn("2026-10-17", c))
        assertEquals(listOf("due-soon:c1:2026-10-20"), keysOn("2026-10-19", c))
        assertEquals(listOf("due-today:c1:2026-10-20"), keysOn("2026-10-20", c))
        assertEquals(emptyList<String>(), keysOn("2026-10-21", c))
    }

    @Test
    fun `due on the 1st is reminded at the end of the previous month`() {
        assertEquals(listOf("due-soon:c1:2026-11-01"), keysOn("2026-10-30", card(dueDay = 1)))
    }

    @Test
    fun `due day past the end of a short month falls on its last day`() {
        assertEquals(listOf("due-today:c1:2027-02-28"), keysOn("2027-02-28", card(dueDay = 31)))
    }

    @Test
    fun `expiry reminder runs from the start of the month before to the end of the expiry month`() {
        val c = card(expiry = 3 to 2027)
        assertEquals(emptyList<String>(), keysOn("2027-01-31", c))
        assertEquals(listOf("expiry:c1:2027-03"), keysOn("2027-02-01", c))
        assertEquals(listOf("expiry:c1:2027-03"), keysOn("2027-03-31", c))
        assertEquals(emptyList<String>(), keysOn("2027-04-01", c))
    }

    @Test
    fun `no due day means no bill reminders, and text reads naturally`() {
        assertTrue(keysOn("2026-10-20", card()).isEmpty())
        val reminder = ReminderRules.due(listOf(card(dueDay = 20)), LocalDate.parse("2026-10-19")).single()
        assertEquals("HDFC •• 1111: payment due in 1 day.", reminder.text)
    }
}
