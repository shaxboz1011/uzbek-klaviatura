package uz.alifbo.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TextTransformsTest {
    private val uz = Locale.forLanguageTag("uz")

    @Test
    fun upperAndLower() {
        assertEquals("SALOM DUNYO", TextTransforms.apply(TextTransforms.UPPER, "salom dunyo", uz))
        assertEquals("salom dunyo", TextTransforms.apply(TextTransforms.LOWER, "SALOM Dunyo", uz))
    }

    @Test
    fun titleCaseKeepsApostropheLetters() {
        assertEquals("Oʻzbek Tili Va Adabiyoti", TextTransforms.title("oʻzbek TILI va adabiyoti", uz))
        assertEquals("Men O'qiyman", TextTransforms.title("men o'qiyman", uz))
    }

    @Test
    fun sentenceCase() {
        assertEquals("Salom. Qalaysiz? Yaxshi!", TextTransforms.sentence("SALOM. QALAYSIZ? YAXSHI!", uz))
        assertEquals("Birinchi qator\nIkkinchi qator", TextTransforms.sentence("birinchi qator\nikkinchi qator", uz))
    }

    @Test
    fun tidyFixesSpacing() {
        assertEquals("Salom, dunyo. Qalaysiz?", TextTransforms.tidy("  Salom ,   dunyo .  Qalaysiz ?  "))
        assertEquals("a\n\nb", TextTransforms.tidy("a  \n\n\n\nb"))
    }

    @Test
    fun clipKinds() {
        assertTrue(ClipKinds.isLink("https://github.com/shaxboz1011"))
        assertTrue(ClipKinds.isLink("www.example.uz"))
        assertTrue(ClipKinds.isLink("ali@example.com"))
        assertFalse(ClipKinds.isLink("salom dunyo"))
        assertTrue(ClipKinds.isNumber("+998 90 123-45-67"))
        assertTrue(ClipKinds.isNumber("8600 1234 5678 9012"))
        assertFalse(ClipKinds.isNumber("12345")) // juda qisqa
        assertFalse(ClipKinds.isNumber("tel 998901234567"))
        assertTrue(ClipKinds.matches(ClipKinds.PINNED, ClipStore.Item("x", true)))
        assertFalse(ClipKinds.matches(ClipKinds.PINNED, ClipStore.Item("x", false)))
    }
}
