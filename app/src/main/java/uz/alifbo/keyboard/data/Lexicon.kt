package uz.alifbo.keyboard.data

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Surib yozish uchun lugʻat: boshlangʻich harf boʻyicha guruhlangan soʻzlar.
 * Fayllar: assets/dict/<til>.txt  (soʻz<TAB>chastota; chastota boʻlmasa — tartib raqami).
 * Oʻzbekcha lugʻat «kanonik» (1995 lotin: oʻ, gʻ, sh, ch) shaklda saqlanadi va paket alifbosiga moslab oʻgiriladi.
 */
class Lexicon(val byFirst: Map<Char, List<Entry>>, private val index: Set<String> = emptySet()) {
    class Entry(val word: String, val prior: Float)

    fun contains(w: String) = w in index
}

object LexiconStore {
    private val cache = ConcurrentHashMap<String, Lexicon>()
    private val order = ArrayList<String>() // yuklanish tartibi: xotirani tejash uchun eng koʻpi bilan 2 ta lugʻat saqlanadi
    private val loading = HashSet<String>()

    fun get(pack: LayoutPack): Lexicon? = cache[key(pack)]

    private fun key(p: LayoutPack) = "${p.id}#${p.version}"

    /** Paketdagi harf tugmalari (kichik harf). Surib yozishda faqat shu harflardan iborat soʻzlar ishlatiladi. */
    fun letters(pack: LayoutPack): Set<Char> {
        val set = HashSet<Char>()
        for (k in pack.rows.flatten() + pack.bottom) {
            if (!k.isToken && k.label.length == 1 && k.label[0].isLetter()) set.add(k.label[0].lowercaseChar())
        }
        return set
    }

    /** Fonda yuklaydi; tayyor boʻlsa onReady asosiy oqimda emas, fon oqimida chaqiriladi. */
    fun loadAsync(ctx: Context, pack: LayoutPack, onReady: () -> Unit) {
        val k = key(pack)
        synchronized(loading) {
            if (cache.containsKey(k) || !loading.add(k)) return
        }
        val app = ctx.applicationContext
        Thread {
            try {
                cache[k] = build(app, pack)
            } catch (e: Exception) {
                cache[k] = Lexicon(emptyMap())
            } finally {
                synchronized(order) {
                    order.remove(k)
                    order.add(k)
                    while (order.size > 2) cache.remove(order.removeAt(0))
                }
                synchronized(loading) { loading.remove(k) }
            }
            onReady()
        }.start()
    }

    private fun build(ctx: Context, pack: LayoutPack): Lexicon {
        val letters = letters(pack)
        val lang = pack.locale.language
        val modern = 'ş' in letters
        val words = HashMap<String, Float>()
        val lines = try {
            ctx.assets.open("dict/${if (lang == "uz" && 'ў' in letters) "uz_cyrl" else lang}.txt").bufferedReader().use { it.readLines() }
        } catch (e: Exception) {
            emptyList()
        }
        var maxLog = 1f
        val parsed = ArrayList<Pair<String, Float>>(lines.size)
        for ((i, line) in lines.withIndex()) {
            val p = line.split('\t')
            var w = p[0].trim().lowercase().replace('\'', 'ʻ')
            if (w.isEmpty()) continue
            if (lang == "uz" && modern) {
                w = w.replace("oʻ", "ö").replace("gʻ", "ğ").replace("sh", "ş").replace("ch", "ç")
            }
            val c = p.getOrNull(1)?.toFloatOrNull() ?: (1_000_000f / (i + 5))
            val lg = Math.log(1.0 + c).toFloat()
            if (lg > maxLog) maxLog = lg
            parsed.add(w to lg)
        }
        for ((w, lg) in parsed) {
            if (w.length < 2 || !w.all { it in letters }) continue
            val prior = lg / maxLog
            val old = words[w]
            if (old == null || prior > old) words[w] = prior
        }
        val by = HashMap<Char, ArrayList<Lexicon.Entry>>()
        for ((w, pr) in words) by.getOrPut(w[0]) { ArrayList() }.add(Lexicon.Entry(w, pr))
        // Har bir guruh chastota boʻyicha kamayish tartibida: tavsiya berishda birinchi mos kelganlari eng yaxshisi.
        for (l in by.values) l.sortByDescending { it.prior }
        return Lexicon(by, HashSet(words.keys))
    }
}
