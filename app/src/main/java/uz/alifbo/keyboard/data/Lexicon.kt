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

    fun letters(pack: LayoutPack): Set<Char> = LexiconBuilder.letters(pack)

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
        val file = if (lang == "uz" && 'ў' in letters) "uz_cyrl" else lang
        val lines = try {
            ctx.assets.open("dict/$file.txt").bufferedReader().use { it.readLines() }
        } catch (e: Exception) {
            emptyList()
        }
        return LexiconBuilder.build(lines, lang, letters)
    }
}
