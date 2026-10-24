package uz.alifbo.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AlphabetToolsTest {
    private val O = "ʻ"
    private val T = "ʼ"

    @Test
    fun normalizeApostrophes() {
        assertEquals("o${O}zbek g${O}oya", AlphabetTools.normalize("o'zbek g'oya"))
        assertEquals("o${O}zbek", AlphabetTools.normalize("o‘zbek"))
        assertEquals("ma${T}no", AlphabetTools.normalize("ma'no"))
        assertEquals("she${T}r", AlphabetTools.normalize("she'r"))
    }

    @Test
    fun oldToNewLatin() {
        assertEquals("özbek", AlphabetTools.toNew("o${O}zbek"))
        assertEquals("Özbekiston", AlphabetTools.toNew("O${O}zbekiston"))
        assertEquals("Toşkent", AlphabetTools.toNew("Toshkent"))
        assertEquals("çiroyli", AlphabetTools.toNew("chiroyli"))
        assertEquals("ğalaba", AlphabetTools.toNew("g${O}alaba"))
        assertEquals("ŞAHAR", AlphabetTools.toNew("SHAHAR"))
    }

    @Test
    fun newToOldLatin() {
        assertEquals("o${O}zbek chay", AlphabetTools.toOld("özbek çay"))
        assertEquals("Sh", AlphabetTools.toOld("Ş"))
    }

    @Test
    fun latinToCyrillic() {
        assertEquals("салом", AlphabetTools.latToCyr("salom"))
        assertEquals("Ўзбекистон", AlphabetTools.latToCyr("O${O}zbekiston"))
        assertEquals("яхши", AlphabetTools.latToCyr("yaxshi"))
        assertEquals("шаҳар", AlphabetTools.latToCyr("shahar"))
        assertEquals("маъно", AlphabetTools.latToCyr("ma${T}no"))
        assertEquals("электрон", AlphabetTools.latToCyr("elektron"))
        assertEquals("лекин", AlphabetTools.latToCyr("lekin"))
        assertEquals("Тошкент", AlphabetTools.latToCyr("Toshkent"))
        assertEquals("қўшиқ", AlphabetTools.latToCyr("qo${O}shiq"))
        assertEquals("йўл", AlphabetTools.latToCyr("yo${O}l"))
        assertEquals("дунё", AlphabetTools.latToCyr("dunyo"))
        assertEquals("ШАҲАР", AlphabetTools.latToCyr("SHAHAR"))
        // yangi alifbo ham kirillga oʻgiriladi
        assertEquals("Ўзбекистон", AlphabetTools.latToCyr("Özbekiston"))
    }

    @Test
    fun cyrillicToLatin() {
        assertEquals("salom", AlphabetTools.cyrToLat("салом"))
        assertEquals("O${O}zbekiston", AlphabetTools.cyrToLat("Ўзбекистон"))
        assertEquals("shahar", AlphabetTools.cyrToLat("шаҳар"))
        assertEquals("yaxshi", AlphabetTools.cyrToLat("яхши"))
        assertEquals("elektron", AlphabetTools.cyrToLat("электрон"))
        assertEquals("ma${T}no", AlphabetTools.cyrToLat("маъно"))
        assertEquals("yomon", AlphabetTools.cyrToLat("ёмон"))
    }

    @Test
    fun roundTripLatinCyrillic() {
        val words = listOf("salom", "shahar", "yaxshi", "o${O}zbek", "g${O}oya", "ma${T}no", "qo${O}shiq", "choy",
            "yo${O}l", "yuz", "elektron", "kitob", "dunyo", "shifokor", "lekin", "yangi")
        for (w in words) {
            val cyr = AlphabetTools.convert(w, AlphabetTools.TO_CYRILLIC)
            val back = AlphabetTools.convert(cyr, AlphabetTools.TO_OLD_LATIN)
            assertEquals("aylanma: $w -> $cyr", w, back)
        }
    }

    @Test
    fun convertDetectsSourceScript() {
        assertEquals("özbek", AlphabetTools.convert("ўзбек", AlphabetTools.TO_NEW_LATIN))
        assertEquals("ўзбек", AlphabetTools.convert("ўзбек", AlphabetTools.TO_CYRILLIC)) // allaqachon kirill
        assertEquals("o${O}zbek", AlphabetTools.convert("özbek", AlphabetTools.TO_OLD_LATIN))
        assertEquals("özbek", AlphabetTools.convert("o'zbek", AlphabetTools.TO_NEW_LATIN))
    }

    @Test
    fun reflowJoinsLinesAndHyphenation() {
        assertEquals("bir ikki uch", AlphabetTools.reflow(listOf("bir ikki", "uch")))
        assertEquals("kitobxonlik", AlphabetTools.reflow(listOf("kitob-", "xonlik")))
        assertEquals("Salom, Dunyo", AlphabetTools.reflow(listOf("Salom,", "", "Dunyo")))
    }
}
