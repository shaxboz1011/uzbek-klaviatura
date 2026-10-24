package uz.alifbo.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import uz.alifbo.keyboard.TestFiles
import uz.alifbo.keyboard.data.LayoutPack
import uz.alifbo.keyboard.data.Lexicon
import uz.alifbo.keyboard.data.LexiconBuilder
import uz.alifbo.keyboard.data.PackParser
import java.util.Random
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Surib yozish dekoderini sinaydi: lugʻatdagi soʻzlar uchun "ideal" barmoq yoʻli yasab, shovqin qoʻshamiz va
 * dekoder soʻzni qanchalik toʻgʻri topishini (top-1 / top-3) oʻlchaymiz. Parametrlarni taxmin bilan emas, raqam bilan sozlash uchun.
 */
class SwipeBenchmarkTest {

    companion object {
        lateinit var pack: LayoutPack
        lateinit var lex: Lexicon
        val centers = HashMap<Char, FloatArray>()
        var keyW = 0f

        @JvmStatic
        @BeforeClass
        fun setUp() {
            pack = PackParser.parse(TestFiles.repoFile("registry/packs/uz-latn-2026.json").readText())
            val lines = TestFiles.repoFile("registry/dict/uz.txt").readLines()
            lex = LexiconBuilder.build(lines, "uz", LexiconBuilder.letters(pack))
            buildGeometry()
        }

        /** KeyboardView.rebuild() mantiqiga yaqin geometriya: raqamlar qatori + 3 harf qatori + pastki qator, kenglik 1080 px. */
        private fun buildGeometry() {
            val w = 1080f
            val padH = 9f
            val rowH = 156f
            val padTop = 18f
            val letterRows = pack.rows
            var maxSum = 0f
            for (r in letterRows) maxSum = maxOf(maxSum, r.sumOf { it.weight.toDouble() }.toFloat())
            val fullW = w - 2 * padH
            val unit = fullW / maxSum
            keyW = unit
            for ((i, r) in letterRows.withIndex()) {
                val ri = i + 1 // 0-qator — raqamlar
                val sum = r.sumOf { it.weight.toDouble() }.toFloat()
                var x = padH + (fullW - sum * unit) / 2f
                for (k in r) {
                    val kw = k.weight * unit
                    if (!k.isToken && k.label.length == 1 && k.label[0].isLetter()) {
                        centers[k.label[0].lowercaseChar()] = floatArrayOf(x + kw / 2f, padTop + ri * rowH + rowH / 2f)
                    }
                    x += kw
                }
            }
        }

        /** Soʻz uchun barmoq yoʻli: harf markazlarini tutashtiruvchi chiziq + shovqin (jitter * keyW) + yumshatish. */
        fun path(word: String, jitter: Float, rnd: Random): Triple<FloatArray, FloatArray, Int> {
            val pts = ArrayList<FloatArray>()
            var prev: FloatArray? = null
            for (ch in word) {
                val c = centers[ch] ?: return Triple(FloatArray(0), FloatArray(0), 0)
                if (prev != null && prev[0] == c[0] && prev[1] == c[1]) continue
                pts.add(c)
                prev = c
            }
            val xs = ArrayList<Float>()
            val ys = ArrayList<Float>()
            val step = keyW / 6f
            if (pts.size == 1) {
                xs.add(pts[0][0]); ys.add(pts[0][1])
            }
            for (i in 1 until pts.size) {
                val a = pts[i - 1]
                val b = pts[i]
                val d = hypot(b[0] - a[0], b[1] - a[1])
                val n = maxOf(1, (d / step).toInt())
                for (j in 0 until n) {
                    val t = j.toFloat() / n
                    xs.add(a[0] + (b[0] - a[0]) * t)
                    ys.add(a[1] + (b[1] - a[1]) * t)
                }
            }
            xs.add(pts.last()[0]); ys.add(pts.last()[1])
            val n = xs.size
            val nx = FloatArray(n) { xs[it] + (rnd.nextGaussian() * jitter * keyW).toFloat() }
            val ny = FloatArray(n) { ys[it] + (rnd.nextGaussian() * jitter * keyW).toFloat() }
            // yumshatish (harakatlanuvchi oʻrtacha, oyna 5)
            val sx = FloatArray(n)
            val sy = FloatArray(n)
            for (i in 0 until n) {
                var ax = 0f; var ay = 0f; var c = 0
                for (j in maxOf(0, i - 2)..minOf(n - 1, i + 2)) { ax += nx[j]; ay += ny[j]; c++ }
                sx[i] = ax / c; sy[i] = ay / c
            }
            return Triple(sx, sy, n)
        }
    }

    private fun testWords(count: Int): List<String> =
        lex.byFirst.values.flatten()
            .filter { it.word.length in 4..9 && it.word.all { c -> c in centers } }
            .sortedByDescending { it.prior }
            .take(count)
            .map { it.word }

    private fun run(jitter: Float, count: Int = 400): Pair<Double, Double> {
        val rnd = Random(42)
        var top1 = 0
        var top3 = 0
        var total = 0
        for (w in testWords(count)) {
            val (xs, ys, n) = path(w, jitter, rnd)
            if (n < 3) continue
            val res = SwipeDecoder.decode(xs, ys, n, centers, keyW, lex, emptyMap(), 3)
            total++
            if (res.firstOrNull() == w) top1++
            if (w in res) top3++
        }
        return Pair(top1.toDouble() / total, top3.toDouble() / total)
    }

    @Test
    fun geometryIsSane() {
        assertTrue(centers.size >= 28)
        assertTrue(keyW > 50f)
        // Ş (W oʻrnida) Q va E orasida
        val q = centers['q']!!; val s = centers['ş']!!; val e = centers['e']!!
        assertTrue(q[0] < s[0] && s[0] < e[0])
    }

    @Test
    fun lexiconHasCommonWords() {
        for (w in listOf("salom", "kitob", "maktab", "yaxshi")) assertTrue("$w yoʻq", lex.contains(w))
    }

    @Test
    fun benchmark() {
        for (j in listOf(0.10f, 0.20f, 0.30f)) {
            val t0 = System.nanoTime()
            val (t1, t3) = run(j)
            val ms = (System.nanoTime() - t0) / 1e6 / 400
            println("SWIPE jitter=$j top1=%.3f top3=%.3f  (%.1f ms/soʻz)".format(t1, t3, ms))
        }
        val (t1, t3) = run(0.10f)
        assertTrue("top3 juda past: $t3", t3 > 0.30)
        assertTrue(t1 <= t3)
    }

    @Test
    fun decodesSimpleWordExactly() {
        val rnd = Random(1)
        val (xs, ys, n) = path("salom", 0.0f, rnd)
        val res = SwipeDecoder.decode(xs, ys, n, centers, keyW, lex, emptyMap(), 3)
        assertEquals("salom", res.firstOrNull())
    }
}
