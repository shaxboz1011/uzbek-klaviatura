package uz.alifbo.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogTest {
    @Test
    fun appendKeepsEverythingWhenSmall() {
        assertEquals("abcdef", CrashLog.appendTrimmed("abc", "def", 100))
    }

    @Test
    fun appendDropsOldestWhenTooLarge() {
        val r = CrashLog.appendTrimmed("a".repeat(50), "b".repeat(30), 40)
        assertEquals(40, r.length)
        assertTrue(r.endsWith("b".repeat(30)))
        assertTrue(r.startsWith("a".repeat(10)))
    }
}
