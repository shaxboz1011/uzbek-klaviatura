package uz.alifbo.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class ShortcutLogicTest {
    @Test
    fun normalizesAbbreviations() {
        assertEquals("mnm", ShortcutLogic.normalizeAbbr("  MNM "))
        assertEquals("oʻz", ShortcutLogic.normalizeAbbr("Oʻz"))
        assertNull(ShortcutLogic.normalizeAbbr(""))
        assertNull(ShortcutLogic.normalizeAbbr("a b"))
        assertNull(ShortcutLogic.normalizeAbbr("a1"))
        assertNull(ShortcutLogic.normalizeAbbr("a".repeat(17)))
    }

    @Test
    fun findsCaseInsensitivelyAndKeepsCase() {
        val l = listOf(ShortcutLogic.Shortcut("mnm", "menga ham kerak"))
        assertEquals("menga ham kerak", ShortcutLogic.find(l, "MnM")?.text)
        assertNull(ShortcutLogic.find(l, "mnmm"))
        assertEquals("Menga ham kerak", ShortcutLogic.applyCase("Mnm", "menga ham kerak"))
        assertEquals("menga ham kerak", ShortcutLogic.applyCase("mnm", "menga ham kerak"))
        assertEquals("OK", ShortcutLogic.applyCase("Ok", "OK"))
    }

    @Test
    fun expandsDateTimeVariables() {
        val cal = GregorianCalendar(2026, Calendar.NOVEMBER, 5, 9, 7) // payshanba
        assertEquals("Bugun 05.11.2026, 09:07 (payshanba)", ShortcutLogic.expandVars("Bugun {sana}, {vaqt} ({hafta})", cal))
        assertEquals("oddiy matn", ShortcutLogic.expandVars("oddiy matn", cal))
    }

    @Test
    fun jsonRoundTripSkipsBadEntries() {
        val l = listOf(ShortcutLogic.Shortcut("ass", "Assalomu alaykum"), ShortcutLogic.Shortcut("sn", "{sana}"))
        val back = ShortcutLogic.parse(ShortcutLogic.toJson(l))
        assertEquals(listOf("ass", "sn"), back.map { it.abbr })
        assertEquals("Assalomu alaykum", back[0].text)
        assertTrue(ShortcutLogic.parse("not json").isEmpty())
        assertTrue(ShortcutLogic.parse("""[{"a":"b c","t":"x"},{"a":"ok","t":""}]""").isEmpty())
    }

    @Test
    fun samplesAreValid() {
        for (s in ShortcutLogic.SAMPLES) assertEquals(s.abbr, ShortcutLogic.normalizeAbbr(s.abbr))
    }
}
