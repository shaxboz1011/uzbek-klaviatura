package uz.alifbo.keyboard.ime

import uz.alifbo.keyboard.data.Lexicon
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Surib yozishni soʻzga aylantiradi: barmoq yoʻli har bir nomzod soʻzning "ideal" yoʻli
 * (harf tugmalari markazlarini tutashtiruvchi chiziq) bilan solishtiriladi.
 */
object SwipeDecoder {
    private const val N = 32

    /** Dekoder parametrlari (testlarda solishtirib sozlanadi; ilovada standart qiymatlar ishlatiladi). */
    class Tuning(
        var priorWeight: Float = 0.45f,   // soʻz chastotasining vazni
        var missWeight: Float = 0.6f,     // yoʻl harf yonidan oʻtmasa jarima
        var missFree: Float = 1.1f,       // shu masofagacha (tugma kengligida) jarima yoʻq
        var lenMin: Float = 0.4f,         // ideal yoʻl uzunligi / foydalanuvchi yoʻli uzunligi: eng kichik
        var lenMax: Float = 2.3f,         // ... eng katta
        var endRadius: Float = 1.6f       // birinchi/oxirgi harf boshlanish/tugash nuqtasiga eng koʻp qancha yaqin boʻlishi
    )

    @Volatile
    var tuning = Tuning()

    /** x, y — yoʻl nuqtalari (n ta); centers — harf -> (x, y) tugma markazi; keyW — tugma kengligi. */
    fun decode(
        xs: FloatArray, ys: FloatArray, n: Int,
        centers: Map<Char, FloatArray>, keyW: Float,
        base: Lexicon?, learned: Map<String, Int>, max: Int
    ): List<String> {
        if (n < 3 || keyW <= 0f || centers.isEmpty()) return emptyList()
        var userLen = 0f
        for (i in 1 until n) userLen += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        if (userLen < keyW * 1.5f) return emptyList()

        val ux = FloatArray(N)
        val uy = FloatArray(N)
        resample(xs, ys, n, ux, uy)

        val sx = xs[0]
        val sy = ys[0]
        val ex = xs[n - 1]
        val ey = ys[n - 1]
        val t = tuning
        val near = keyW * t.endRadius

        val starts = ArrayList<Char>()
        for ((c, p) in centers) if (hypot(p[0] - sx, p[1] - sy) <= near) starts.add(c)
        if (starts.isEmpty()) return emptyList()

        val cand = HashMap<String, Float>()
        val ix = FloatArray(32)
        val iy = FloatArray(32)
        val rx = FloatArray(N)
        val ry = FloatArray(N)

        fun consider(w: String, prior: Float) {
            if (w.length > 31) return
            val last = centers[w[w.length - 1]] ?: return
            if (hypot(last[0] - ex, last[1] - ey) > near) return
            // ideal yoʻl (ketma-ket takror harflar bitta nuqta)
            var m = 0
            for (ch in w) {
                val p = centers[ch] ?: return
                if (m > 0 && ix[m - 1] == p[0] && iy[m - 1] == p[1]) continue
                ix[m] = p[0]
                iy[m] = p[1]
                m++
            }
            if (m < 2) return
            var idLen = 0f
            for (i in 1 until m) idLen += hypot(ix[i] - ix[i - 1], iy[i] - iy[i - 1])
            if (idLen < userLen * t.lenMin || idLen > userLen * t.lenMax) return
            resample(ix, iy, m, rx, ry)
            var d = 0f
            for (i in 0 until N) d += hypot(rx[i] - ux[i], ry[i] - uy[i])
            d /= N * keyW
            // yoʻl har bir harf yonidan oʻtganmi
            var miss = 0f
            for (i in 0 until m) {
                var best = Float.MAX_VALUE
                for (j in 0 until n) {
                    val dd = hypot(xs[j] - ix[i], ys[j] - iy[i])
                    if (dd < best) best = dd
                }
                val r = best / keyW
                if (r > t.missFree) miss += (r - t.missFree)
            }
            val score = d + miss * t.missWeight - t.priorWeight * prior
            val old = cand[w]
            if (old == null || score < old) cand[w] = score
        }

        if (base != null) {
            for (c in starts) base.byFirst[c]?.forEach { consider(it.word, it.prior) }
        }
        // oʻrganilgan soʻzlar — biroz ustunlik bilan
        for ((w, cnt) in learned) {
            if (w.length >= 2 && w[0] in starts) consider(w, min(1f, 0.55f + ln(1f + cnt) / 8f))
        }
        if (cand.isEmpty()) return emptyList()
        return cand.entries.sortedBy { it.value }.take(max).map { it.key }
    }

    /** Siniq chiziqni yoy uzunligi boʻyicha bir tekis N nuqtaga keltiradi. */
    private fun resample(x: FloatArray, y: FloatArray, n: Int, ox: FloatArray, oy: FloatArray) {
        var total = 0f
        for (i in 1 until n) total += hypot(x[i] - x[i - 1], y[i] - y[i - 1])
        if (total <= 0f) {
            for (i in 0 until N) {
                ox[i] = x[0]
                oy[i] = y[0]
            }
            return
        }
        val step = total / (N - 1)
        var seg = 1
        var segStart = 0f
        for (k in 0 until N) {
            val target = min(total, k * step)
            while (seg < n - 1 && segStart + hypot(x[seg] - x[seg - 1], y[seg] - y[seg - 1]) < target) {
                segStart += hypot(x[seg] - x[seg - 1], y[seg] - y[seg - 1])
                seg++
            }
            val len = hypot(x[seg] - x[seg - 1], y[seg] - y[seg - 1])
            val t = if (len > 0f) max(0f, min(1f, (target - segStart) / len)) else 0f
            ox[k] = x[seg - 1] + (x[seg] - x[seg - 1]) * t
            oy[k] = y[seg - 1] + (y[seg] - y[seg - 1]) * t
        }
    }
}
