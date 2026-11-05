package uz.alifbo.keyboard.data

/**
 * Keyingi soʻz bashorati: "oldingi soʻz -> keyingi soʻz" juftliklari sanogi. Faqat foydalanuvchi yozganidan oʻrganadi
 * (shaxsiy xotirada, internetga chiqmaydi). Android'ga bogʻliq emas (testlanadi).
 */
class BigramModel(private val maxPrev: Int = 4000, private val maxNext: Int = 24) {
    private val m = HashMap<String, HashMap<String, Int>>()

    @Synchronized
    fun learn(prev: String, next: String) {
        val a = prev.lowercase()
        val b = next.lowercase()
        if (a.length < 2 || b.length < 2 || a == b || a.length > 30 || b.length > 30) return
        val row = m.getOrPut(a) { HashMap() }
        row[b] = (row[b] ?: 0) + 1
        if (row.size > maxNext) {
            val keep = row.entries.sortedByDescending { it.value }.take(maxNext).map { it.key }.toSet()
            row.keys.retainAll(keep)
        }
        if (m.size > maxPrev + 200) prune()
    }

    private fun prune() {
        m.entries.removeIf { e -> e.value.values.sum() <= 1 }
        if (m.size > maxPrev) {
            val keep = m.entries.sortedByDescending { it.value.values.sum() }.take(maxPrev).map { it.key }.toSet()
            m.keys.retainAll(keep)
        }
    }

    /** prev dan keyin eng koʻp kelgan soʻzlar (koʻp -> kam, teng boʻlsa alifbo tartibida). */
    @Synchronized
    fun next(prev: String, n: Int): List<String> {
        val row = m[prev.lowercase()] ?: return emptyList()
        return row.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(n)
            .map { it.key }
    }

    @Synchronized
    fun clear() = m.clear()

    @Synchronized
    fun serialize(): String =
        m.entries.joinToString("\n") { (a, row) -> row.entries.joinToString("\n") { "$a\t${it.key}\t${it.value}" } }

    @Synchronized
    fun load(text: String) {
        m.clear()
        for (line in text.lineSequence()) {
            val p = line.split('\t')
            if (p.size != 3) continue
            val c = p[2].toIntOrNull() ?: continue
            m.getOrPut(p[0]) { HashMap() }[p[1]] = c
        }
    }
}
