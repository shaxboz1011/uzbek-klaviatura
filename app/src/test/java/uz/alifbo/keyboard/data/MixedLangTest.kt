package uz.alifbo.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MixedLangTest {
    private fun lex(lang: String, letters: Set<Char>?, vararg w: String) =
        LexiconBuilder.build(w.mapIndexed { i, s -> "$s\t${1000 - i * 100}" }, lang, letters)

    @Test
    fun foreignLexiconIgnoresPackLetters() {
        // oʻzbekcha lotin paketida c, w, x kabi harflar tugmasiz boʻlishi mumkin — lekin inglizcha soʻz baribir olinadi
        val uzLetters = "abdefghijklmnopqrstuvxyzşçöğ".toSet()
        val strict = lex("en", uzLetters, "welcome", "city")
        val open = lex("en", null, "welcome", "city")
        assertFalse(strict.contains("welcome"))
        assertTrue(open.contains("welcome") && open.contains("city"))
    }

    @Test
    fun foreignLexiconSkipsNonWords() {
        val open = lex("en", null, "hello", "don't", "a1b", "x")
        assertTrue(open.contains("hello"))
        assertFalse(open.contains("don't"))
        assertFalse(open.contains("a1b"))
        assertFalse(open.contains("x")) // bir harfli
    }

    @Test
    fun extraMatchesRankBelowSameFrequencyMain() {
        val main = lex("uz", "abcdefghijklmnopqrstuvxyz".toSet(), "meros", "metin")
        val en = lex("en", null, "meeting", "message", "method")
        val scored = HashMap<String, Float>()
        Predictor.addMatches(scored, "me", main, 0.9f, 9)
        Predictor.addMatches(scored, "me", en, Predictor.EXTRA_WEIGHT, 9)
        val order = scored.entries.sortedByDescending { it.value }.map { it.key }
        assertEquals("meros", order.first())
        assertTrue("meeting" in order && "message" in order)
        // 2 harfdan keyin ham main soʻzlar bor, inglizcha soʻzlar ham nomzod
        assertTrue(scored.getValue("meeting") < scored.getValue("meros"))
    }

    @Test
    fun noMatchForSingleLetterIsHandledByCaller() {
        // addMatches prefiksi bor soʻzlarnigina qaytaradi (uzunroq va boshlanadigan)
        val en = lex("en", null, "hello")
        val scored = HashMap<String, Float>()
        Predictor.addMatches(scored, "hello", en, 0.75f, 9)
        assertTrue(scored.isEmpty())
    }
}
