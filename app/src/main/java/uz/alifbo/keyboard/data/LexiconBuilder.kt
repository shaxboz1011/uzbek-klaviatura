package uz.alifbo.keyboard.data

/** Lugʻat fayli (soʻz<TAB>chastota yoki faqat soʻz) dan, paket harflariga moslab, [Lexicon] quradi. Android'ga bogʻliq emas (testlanadi). */
object LexiconBuilder {

    /** Paketdagi harf tugmalari (kichik harf). Faqat shu harflardan iborat soʻzlar ishlatiladi. */
    fun letters(pack: LayoutPack): Set<Char> {
        val set = HashSet<Char>()
        for (k in pack.rows.flatten() + pack.bottom) {
            if (!k.isToken && k.label.length == 1 && k.label[0].isLetter()) set.add(k.label[0].lowercaseChar())
        }
        return set
    }

    fun build(lines: List<String>, lang: String, letters: Set<Char>): Lexicon {
        val modern = 'ş' in letters
        val words = HashMap<String, Float>()
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
