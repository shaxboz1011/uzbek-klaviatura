package uz.alifbo.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolPagesTest {
    private val all: Set<String> = DefaultLayers.symbolPages.flatten().flatten().map { it.label }.toSet()

    @Test
    fun everyRowFillsTheWidth() {
        for ((p, page) in DefaultLayers.symbolPages.withIndex()) {
            for ((r, row) in page.withIndex()) {
                assertEquals("sahifa ${p + 1}, qator ${r + 1}", 10f, row.sumOf { it.weight.toDouble() }.toFloat(), 0.01f)
            }
        }
    }

    @Test
    fun commonSymbolsAreReachable() {
        val need = "@#\$%&*-+()/!\"':;?,.~`|•√π÷×¶∆£¢€¥^°=<>[]{}\\_©®™✓≈≠±«»“”".map { it.toString() }
        val missing = need.filter { it !in all }
        assertTrue("yetishmayotgan belgilar: $missing", missing.isEmpty())
    }
}
