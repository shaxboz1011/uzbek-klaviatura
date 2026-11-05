package uz.alifbo.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BigramModelTest {
    @Test
    fun suggestsMostFrequentNextWords() {
        val m = BigramModel()
        repeat(3) { m.learn("assalomu", "alaykum") }
        m.learn("assalomu", "aleykum")
        repeat(2) { m.learn("rahmat", "sizga") }
        assertEquals(listOf("alaykum", "aleykum"), m.next("assalomu", 3))
        assertEquals(listOf("sizga"), m.next("RAHMAT", 3))
        assertTrue(m.next("nomalum", 3).isEmpty())
    }

    @Test
    fun ignoresShortOrRepeatedWords() {
        val m = BigramModel()
        m.learn("a", "bola")
        m.learn("bola", "bola")
        assertTrue(m.next("a", 3).isEmpty() && m.next("bola", 3).isEmpty())
    }

    @Test
    fun roundTripsThroughText() {
        val m = BigramModel()
        repeat(2) { m.learn("salom", "dunyo") }
        m.learn("salom", "do'st")
        val m2 = BigramModel()
        m2.load(m.serialize())
        assertEquals(m.next("salom", 5), m2.next("salom", 5))
    }

    @Test
    fun keepsMemoryBounded() {
        val m = BigramModel(maxPrev = 50, maxNext = 5)
        for (i in 0 until 400) m.learn("so'z$i", "keyin")
        repeat(5) { m.learn("mashhur", "juft") }
        for (i in 0 until 60) m.learn("mashhur", "boshqa$i")
        assertTrue(m.serialize().lines().size < 400)
        assertEquals("juft", m.next("mashhur", 1).first())
    }
}
