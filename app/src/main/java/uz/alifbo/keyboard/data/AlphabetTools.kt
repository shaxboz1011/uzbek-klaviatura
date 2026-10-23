package uz.alifbo.keyboard.data

/** Oʻzbek lotin yozuvlari orasida oʻgirish va OCR (rasmdan oʻqish) natijasini tuzatish. */
object AlphabetTools {
    private const val OKINA = "ʻ" // ʻ  (oʻ, gʻ)
    private const val TUTUQ = "ʼ" // ʼ  (maʼno)

    private val OKINA_RE = Regex("([OoGg])['‘’ʼʻ`´]")
    private val TUTUQ_RE = Regex("(?<=[AaEeIiUu])['‘’`´](?=\\p{L})")

    /** Oʻ/Gʻ va tutuq belgilarini toʻgʻri Unicode belgilariga keltiradi (1995 lotin imlosi). */
    fun normalize(s: String): String =
        s.replace(OKINA_RE) { it.groupValues[1] + OKINA }.replace(TUTUQ_RE, TUTUQ)

    /** 1995 lotin (oʻ, gʻ, sh, ch) -> yangi alifbo (ö, ğ, ş, ç). */
    fun toNew(s: String): String {
        var t = normalize(s)
        t = t.replace("o$OKINA", "ö").replace("O$OKINA", "Ö")
            .replace("g$OKINA", "ğ").replace("G$OKINA", "Ğ")
        t = t.replace("sh", "ş").replace("Sh", "Ş").replace("SH", "Ş").replace("sH", "ş")
        t = t.replace("ch", "ç").replace("Ch", "Ç").replace("CH", "Ç").replace("cH", "ç")
        return t
    }

    /** Yangi alifbo -> 1995 lotin. */
    fun toOld(s: String): String =
        s.replace("ö", "o$OKINA").replace("Ö", "O$OKINA")
            .replace("ğ", "g$OKINA").replace("Ğ", "G$OKINA")
            .replace("ş", "sh").replace("Ş", "Sh")
            .replace("ç", "ch").replace("Ç", "Ch")

    /** Qatorlarni abzatsga birlashtiradi: qator oxiridagi defis boʻgʻin koʻchirishi boʻlsa, soʻzni yopishtiradi. */
    fun reflow(lines: List<String>): String {
        val sb = StringBuilder()
        for ((i, raw) in lines.withIndex()) {
            val l = raw.trim()
            if (l.isEmpty()) continue
            if (i > 0 && sb.isNotEmpty()) {
                if (sb.endsWith("-") && l.first().isLowerCase()) sb.setLength(sb.length - 1) else sb.append(' ')
            }
            sb.append(l)
        }
        return sb.toString()
    }
}
