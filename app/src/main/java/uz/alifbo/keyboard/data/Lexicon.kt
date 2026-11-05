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

    // ---- aralash tilda tavsiyalar: boshqa tilning lugʻati (paket harflari bilan cheklanmaydi) ----
    private val extraCache = ConcurrentHashMap<String, Lexicon>()
    private val extraLoading = HashSet<String>()

    /** Joriy alifbo bilan birga tavsiya beriladigan boshqa til lugʻatlari (xuddi shu yozuvda: lotin->inglizcha, kirill->ruscha va hokazo). */
    fun extraFiles(pack: LayoutPack): List<String> {
        val lang = pack.locale.language
        return when {
            lang == "uz" && 'ў' in letters(pack) -> listOf("ru")
            lang == "uz" -> listOf("en")
            lang == "ru" -> listOf("uz_cyrl")
            lang == "en" -> listOf("uz")
            else -> emptyList()
        }
    }

    fun extras(pack: LayoutPack): List<Lexicon> = extraFiles(pack).mapNotNull { extraCache[it] }

    fun loadExtrasAsync(ctx: Context, pack: LayoutPack, onReady: () -> Unit) {
        val app = ctx.applicationContext
        for (f in extraFiles(pack)) {
            synchronized(extraLoading) {
                if (extraCache.containsKey(f) || !extraLoading.add(f)) return@synchronized
                Thread {
                    try {
                        val lines = app.assets.open("dict/$f.txt").bufferedReader().use { it.readLines() }
                        extraCache[f] = LexiconBuilder.build(lines, if (f.startsWith("uz")) "uz" else f, null)
                    } catch (e: Exception) {
                        extraCache[f] = Lexicon(emptyMap())
                    } finally {
                        synchronized(extraLoading) { extraLoading.remove(f) }
                    }
                    onReady()
                }.start()
            }
        }
    }

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
