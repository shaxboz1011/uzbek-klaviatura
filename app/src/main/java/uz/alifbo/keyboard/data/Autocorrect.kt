package uz.alifbo.keyboard.data

import kotlin.math.abs

/**
 * Avto-tuzatish: lugʻatda ham, oʻrganilgan soʻzlar orasida ham yoʻq soʻz bitta xato (almashgan/ortiqcha/yetishmagan harf yoki
 * oʻrni almashgan qoʻshni harflar) bilan lugʻatdagi yagona va yetarlicha ishlatiladigan soʻzga oʻxshasa, shuni qaytaradi.
 * Shubhali holatlarda (bir nechta mos nomzod, kam ishlatiladigan soʻz, bosh harfli soʻz) hech narsa qilmaydi.
 */
object Autocorrect {
    private const val MIN_PRIOR = 0.35f
    private const val MARGIN = 0.08f

    fun correct(word: String, lex: Lexicon, known: (String) -> Boolean): String? {
        if (word.length < 3 || word.length > 20) return null
        if (word[0].isUpperCase() || word.any { it.isDigit() }) return null
        val w = word.lowercase()
        if (lex.contains(w) || known(w)) return null
        val list = lex.byFirst[w[0]] ?: return null
        var best: String? = null
        var bestPrior = 0f
        var second = 0f
        for (e in list) {
            val c = e.word
            if (abs(c.length - w.length) > 1 || !withinOneEdit(w, c)) continue
            if (e.prior > bestPrior) {
                second = bestPrior
                bestPrior = e.prior
                best = c
            } else if (e.prior > second) {
                second = e.prior
            }
        }
        if (best == null || bestPrior < MIN_PRIOR) return null
        if (second > 0f && bestPrior - second < MARGIN) return null
        return best
    }

    /** a va b orasidagi Damerau-Levenshtein masofasi 1 dan oshmaydimi (a != b). */
    fun withinOneEdit(a: String, b: String): Boolean {
        if (a == b) return false
        val la = a.length
        val lb = b.length
        if (abs(la - lb) > 1) return false
        var i = 0
        while (i < la && i < lb && a[i] == b[i]) i++
        if (la == lb) {
            if (a.substring(i + 1) == b.substring(i + 1)) return true // almashgan harf
            return i + 1 < la && a[i] == b[i + 1] && a[i + 1] == b[i] && a.substring(i + 2) == b.substring(i + 2) // oʻrni almashgan
        }
        return if (la < lb) a.substring(i) == b.substring(i + 1) else a.substring(i + 1) == b.substring(i)
    }

    /** Asl soʻzning bosh/katta harf shaklini tuzatilgan soʻzga koʻchiradi. */
    fun matchCase(original: String, fixed: String): String =
        if (original.length > 1 && original.all { it.isUpperCase() }) fixed.uppercase()
        else fixed
}
