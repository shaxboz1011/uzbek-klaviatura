package uz.alifbo.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutocorrectTest {
    private fun lex(vararg words: Pair<String, Float>): Lexicon {
        val by = HashMap<Char, ArrayList<Lexicon.Entry>>()
        for ((w, p) in words) by.getOrPut(w[0]) { ArrayList() }.add(Lexicon.Entry(w, p))
        for (l in by.values) l.sortByDescending { it.prior }
        return Lexicon(by, words.map { it.first }.toSet())
    }

    private val L = lex("kitob" to 0.9f, "kitoblar" to 0.7f, "maktab" to 0.9f, "bola" to 0.8f, "bolta" to 0.2f, "salom" to 0.95f)

    @Test
    fun withinOneEdit() {
        assertTrue(Autocorrect.withinOneEdit("kitpb", "kitob")) // almashgan
        assertTrue(Autocorrect.withinOneEdit("kitb", "kitob")) // yetishmagan
        assertTrue(Autocorrect.withinOneEdit("kitoob", "kitob")) // ortiqcha
        assertTrue(Autocorrect.withinOneEdit("kitbo", "kitob")) // oʻrni almashgan
        assertFalse(Autocorrect.withinOneEdit("kitob", "kitob"))
        assertFalse(Autocorrect.withinOneEdit("abc", "abcde"))
        assertFalse(Autocorrect.withinOneEdit("abcd", "badc"))
    }

    @Test
    fun correctsClearTypo() {
        assertEquals("kitob", Autocorrect.correct("kitbo", L) { false })
        assertEquals("maktab", Autocorrect.correct("maktav", L) { false })
        assertEquals("salom", Autocorrect.correct("salim", L) { false })
    }

    @Test
    fun leavesKnownAndUnknownAlone() {
        assertNull(Autocorrect.correct("kitob", L) { false })       // lugʻatda bor
        assertNull(Autocorrect.correct("xyzxyz", L) { false })      // yaqin nomzod yoʻq
        assertNull(Autocorrect.correct("kitbo", L) { it == "kitbo" }) // foydalanuvchi oʻrgangan soʻz
        assertNull(Autocorrect.correct("Kitbo", L) { false })       // bosh harfli (ism boʻlishi mumkin)
        assertNull(Autocorrect.correct("ab", L) { false })          // juda qisqa
        assertNull(Autocorrect.correct("kit2b", L) { false })       // raqamli
    }

    @Test
    fun skipsAmbiguousAndRare() {
        val amb = lex("bola" to 0.80f, "bolt" to 0.78f) // "bolx": ikki teng nomzod
        assertNull(Autocorrect.correct("bolx", amb) { false })
        val rare = lex("bolta" to 0.1f)
        assertNull(Autocorrect.correct("boltq", rare) { false }) // kam ishlatiladigan soʻz
    }
}
