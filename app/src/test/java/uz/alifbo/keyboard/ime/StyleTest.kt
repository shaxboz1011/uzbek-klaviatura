package uz.alifbo.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleTest {
    @Test
    fun stylesAreConsistent() {
        for ((i, s) in Theme.STYLES.withIndex()) {
            assertEquals(i, s.id)
            assertTrue(s.radius in 0..18)
            assertTrue(s.gap in 1..6)
            assertTrue(s.textScale in 80..130)
            assertEquals(s.light == null, s.dark == null)
            s.light?.let { assertEquals(8, it.size) }
            s.dark?.let { assertEquals(8, it.size) }
        }
    }

    @Test
    fun firstStyleKeepsTheOriginalLook() {
        val s = Theme.STYLES[0]
        assertEquals(9, s.radius)
        assertEquals(3, s.gap)
        assertEquals(100, s.textScale)
        assertTrue(s.light == null)
    }
}
