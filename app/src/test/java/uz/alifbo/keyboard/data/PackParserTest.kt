package uz.alifbo.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.alifbo.keyboard.TestFiles

class PackParserTest {
    private fun packs() = TestFiles.repoFile("registry/packs").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }

    @Test
    fun everyRegistryPackParses() {
        val files = packs()
        assertTrue("paketlar topilmadi", files.size >= 8)
        for (f in files) {
            val p = PackParser.parse(f.readText())
            assertEquals(f.name, p.id + ".json", f.name)
            assertTrue("${p.id}: harflar yetarli emas", LexiconBuilder.letters(p).size >= 20)
            assertTrue("${p.id}: @space yoʻq", p.bottom.any { it.label == "@space" })
        }
    }

    @Test
    fun newUzbekAlphabetHasItsLetters() {
        val p = PackParser.parse(TestFiles.repoFile("registry/packs/uz-latn-2026.json").readText())
        val letters = LexiconBuilder.letters(p)
        for (c in "şçöğ") assertTrue("$c yoʻq", c in letters)
        assertTrue("tutuq belgisi yoʻq", 'ʼ' in letters)
    }

    @Test
    fun oldUzbekAlphabetHasOkina() {
        val p = PackParser.parse(TestFiles.repoFile("registry/packs/uz-latn-1995.json").readText())
        assertTrue('ʻ' in LexiconBuilder.letters(p))
    }
}
