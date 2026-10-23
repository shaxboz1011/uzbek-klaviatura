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

    @Synchronized
    fun suggest(ctx: Context, prefix: String, max: Int): List<String> {
        ensure(ctx)
        val p = prefix.lowercase()
        if (p.isEmpty()) return emptyList()
        return words.entries
            .filter { it.key.startsWith(p) && it.key != p }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length })
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
