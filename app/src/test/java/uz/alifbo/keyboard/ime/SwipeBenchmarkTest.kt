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

        /**
         * Soʻz uchun realistik barmoq yoʻli: harf markazlari boʻylab, lekin burchaklar kesiladi (Chaykin yumshatish),
         * sekin "siljish" (past chastotali xato), boshlanish/tugash nuqtasida xato va ozgina mayda shovqin bor.
         * jitter — xatolik darajasi (tugma kengligi ulushida): 0.1 — puxta, 0.3 — shoshilgan.
         */
        fun path(word: String, jitter: Float, rnd: Random): Triple<FloatArray, FloatArray, Int> {
            var pts = ArrayList<FloatArray>()
            var prev: FloatArray? = null
            for (ch in word) {
                val c = centers[ch] ?: return Triple(FloatArray(0), FloatArray(0), 0)
                if (prev != null && prev[0] == c[0] && prev[1] == c[1]) continue
                pts.add(floatArrayOf(c[0], c[1]))
                prev = c
            }
            // boshlanish va tugash xatosi
            for (idx in listOf(0, pts.size - 1)) {
                pts[idx][0] += (rnd.nextGaussian() * jitter * keyW * 0.7).toFloat()
                pts[idx][1] += (rnd.nextGaussian() * jitter * keyW * 0.7).toFloat()
            }
            // burchaklarni kesish (Chaykin, 2 marta); birinchi va oxirgi nuqta saqlanadi
            repeat(2) {
                if (pts.size >= 3) {
                    val q = ArrayList<FloatArray>()
                    q.add(pts.first())
                    for (i in 0 until pts.size - 1) {
                        val a = pts[i]; val b = pts[i + 1]
                        q.add(floatArrayOf(0.75f * a[0] + 0.25f * b[0], 0.75f * a[1] + 0.25f * b[1]))
                        q.add(floatArrayOf(0.25f * a[0] + 0.75f * b[0], 0.25f * a[1] + 0.75f * b[1]))
                    }
                    q.add(pts.last())
                    pts = q
                }
            }
            val xs = ArrayList<Float>()
            val ys = ArrayList<Float>()
            val step = keyW / 6f
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
            // past chastotali siljish: ikki sinus (1..3 davr), amplituda ~ jitter*keyW
            val ax = jitter * keyW * 1.2f
            val f1 = 1f + rnd.nextFloat() * 2f; val f2 = 1f + rnd.nextFloat() * 2f
            val p1 = rnd.nextFloat() * 6.28f; val p2 = rnd.nextFloat() * 6.28f
            val p3 = rnd.nextFloat() * 6.28f; val p4 = rnd.nextFloat() * 6.28f
            val sx = FloatArray(n)
            val sy = FloatArray(n)
            for (i in 0 until n) {
                val t = i.toFloat() / maxOf(1, n - 1)
                val w = Math.sin(Math.PI * t).toFloat() // chetlarda kichikroq, oʻrtada kattaroq
                sx[i] = xs[i] + ax * w * (Math.sin(2 * Math.PI * f1 * t + p1).toFloat() + 0.5f * Math.sin(2 * Math.PI * 2 * f2 * t + p2).toFloat()) +
                    (rnd.nextGaussian() * jitter * keyW * 0.1).toFloat()
                sy[i] = ys[i] + ax * w * (Math.sin(2 * Math.PI * f2 * t + p3).toFloat() + 0.5f * Math.sin(2 * Math.PI * 2 * f1 * t + p4).toFloat()) +
                    (rnd.nextGaussian() * jitter * keyW * 0.1).toFloat()
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
        for (w in listOf("salom", "kitob", "maktab", "yaxşi")) assertTrue("$w yoʻq", lex.contains(w)) // yangi alifbo: sh -> ş
    }

    @Test
    fun benchmark() {
        for (j in listOf(0.10f, 0.20f, 0.30f, 0.40f)) {
            val t0 = System.nanoTime()
            val (t1, t3) = run(j)
            val ms = (System.nanoTime() - t0) / 1e6 / 400
            println("SWIPE jitter=$j top1=%.3f top3=%.3f  (%.1f ms/soʻz)".format(t1, t3, ms))
        }
        val (t1, t3) = run(0.10f)
        assertTrue("top3 juda past: $t3", t3 > 0.30)
        assertTrue(t1 <= t3)
    }

    /** Parametrlarni solishtirish: bir nechta variant uchun top-1/top-3 (jitter 0.2 va 0.3). */
    @Test
    fun tuningSweep() {
        val configs = listOf(
            "asos" to SwipeDecoder.Tuning(),
            "prior0.8" to SwipeDecoder.Tuning(priorWeight = 0.8f),
            "prior1.2" to SwipeDecoder.Tuning(priorWeight = 1.2f),
            "prior1.8" to SwipeDecoder.Tuning(priorWeight = 1.8f),
            "len0.25-3.5" to SwipeDecoder.Tuning(lenMin = 0.25f, lenMax = 3.5f),
            "miss1.5" to SwipeDecoder.Tuning(missFree = 1.5f),
            "miss0.3" to SwipeDecoder.Tuning(missWeight = 0.3f),
            "miss0.0" to SwipeDecoder.Tuning(missWeight = 0.0f),
            "end2.0" to SwipeDecoder.Tuning(endRadius = 2.0f),
            "kombi1" to SwipeDecoder.Tuning(priorWeight = 1.2f, lenMin = 0.25f, lenMax = 3.5f, missFree = 1.5f),
            "kombi2" to SwipeDecoder.Tuning(priorWeight = 1.2f, lenMin = 0.25f, lenMax = 3.5f, missFree = 1.5f, missWeight = 0.3f, endRadius = 2.0f),
            "kombi3" to SwipeDecoder.Tuning(priorWeight = 1.8f, lenMin = 0.25f, lenMax = 3.5f, missFree = 1.5f, missWeight = 0.3f, endRadius = 2.0f)
        )
        val keep = SwipeDecoder.tuning
        try {
            for ((name, cfg) in configs) {
                SwipeDecoder.tuning = cfg
                val a = run(0.2f, 300)
                val b = run(0.3f, 300)
                println("TUNE %-12s j0.2 top1=%.3f top3=%.3f | j0.3 top1=%.3f top3=%.3f".format(name, a.first, a.second, b.first, b.second))
            }
        } finally {
            SwipeDecoder.tuning = keep
        }
    }

    /** Tashxis: dekoder xato qilgan soʻzlar (birinchi 40 tasi) — sozlash uchun. */
    @Test
    fun printFailures() {
        val rnd = Random(7)
        var shown = 0
        var fails = 0
        var total = 0
        for (w in testWords(400)) {
            val (xs, ys, n) = path(w, 0.2f, rnd)
            if (n < 3) continue
            total++
            val res = SwipeDecoder.decode(xs, ys, n, centers, keyW, lex, emptyMap(), 3)
            if (res.firstOrNull() != w) {
                fails++
                if (shown++ < 40) println("FAIL $w -> $res")
            }
        }
        println("FAIL jami: $fails / $total")
    }

    @Test
    fun decodesSimpleWordExactly() {
        val rnd = Random(1)
        val (xs, ys, n) = path("salom", 0.0f, rnd)
        val res = SwipeDecoder.decode(xs, ys, n, centers, keyW, lex, emptyMap(), 3)
        assertEquals("salom", res.firstOrNull())
    }
}
