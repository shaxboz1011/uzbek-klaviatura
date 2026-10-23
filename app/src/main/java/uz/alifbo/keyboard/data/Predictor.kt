package uz.alifbo.keyboard.data

import android.content.Context
import java.io.File

/**
 * Foydalanuvchi yozgan so'zlarni o'rganib, tavsiya beradi (faqat shu telefonda, internetga chiqmaydi).
 * Boshlang'ich lug'at yo'q: tavsiyalar siz yozgan sari to'lib boradi.
 */
object Predictor {
    private const val MAX_WORDS = 5000
    private val words = HashMap<String, Int>()
    private var loaded = false
    private var dirty = 0

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "learned.txt")

    @Synchronized
    private fun ensure(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = file(ctx)
        if (!f.exists()) return
        try {
            f.forEachLine { line ->
                val p = line.split('\t')
                if (p.size == 2) p[1].toIntOrNull()?.let { words[p[0]] = it }
            }
        } catch (e: Exception) {
            words.clear()
        }
    }

    @Synchronized
    fun learn(ctx: Context, word: String) {
        ensure(ctx)
        val k = word.lowercase()
        if (k.length < 2 || k.length > 30) return
        words[k] = (words[k] ?: 0) + 1
        dirty++
        if (words.size > MAX_WORDS + 500) {
            words.entries.removeAll { it.value <= 1 }
            if (words.size > MAX_WORDS) {
                val keep = words.entries.sortedByDescending { it.value }.take(MAX_WORDS).map { it.key }.toSet()
                words.keys.retainAll(keep)
            }
        }
        if (dirty >= 25) save(ctx)
    }

    /**
     * Tavsiyalar: siz yozgan (oʻrganilgan) soʻzlar va boshlangʻich lugʻat (lex) birlashtiriladi.
     * Oʻrganilgan soʻzlar ustun (qancha koʻp ishlatilsa, shuncha yuqori).
     */
    @Synchronized
    fun suggest(ctx: Context, prefix: String, max: Int, lex: Lexicon? = null): List<String> {
        ensure(ctx)
        val p = prefix.lowercase()
        if (p.isEmpty()) return emptyList()
        val scored = HashMap<String, Float>()
        for (e in words.entries) {
            if (e.key.startsWith(p) && e.key != p) scored[e.key] = 0.6f + minOf(e.value, 20) / 50f
        }
        val list = lex?.byFirst?.get(p[0])
        if (list != null) {
            var found = 0
            for (en in list) { // chastota boʻyicha tartiblangan
                if (en.word.length > p.length && en.word.startsWith(p)) {
                    val s = en.prior * 0.9f
                    if (s > (scored[en.word] ?: 0f)) scored[en.word] = s
                    if (++found >= max * 3) break
                }
            }
        }
        return scored.entries
            .sortedWith(compareByDescending<Map.Entry<String, Float>> { it.value }.thenBy { it.key.length })
            .take(max)
            .map { it.key }
    }

    /** Surib yozish uchun: oʻrganilgan soʻzlar nusxasi. */
    @Synchronized
    fun snapshot(ctx: Context): Map<String, Int> {
        ensure(ctx)
        return HashMap(words)
    }

    @Synchronized
    fun save(ctx: Context) {
        if (!loaded || dirty == 0) return
        dirty = 0
        try {
            file(ctx).writeText(words.entries.joinToString("\n") { "${it.key}\t${it.value}" })
        } catch (e: Exception) {
            // saqlanmasa — keyingi safar urinamiz
        }
    }

    @Synchronized
    fun clear(ctx: Context) {
        words.clear()
        loaded = true
        dirty = 0
        file(ctx).delete()
    }
}
