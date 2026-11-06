package uz.alifbo.keyboard.data

import java.util.Locale

/** Matnni tezkor o'zgartirish: KATTA, kichik, Bosh Harf, Gap boshi, bo'shliqlarni tuzatish. Android'ga bog'liq emas (testlanadi). */
object TextTransforms {
    const val UPPER = 0
    const val LOWER = 1
    const val TITLE = 2
    const val SENTENCE = 3
    const val TIDY = 4

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == 'ʻ' || c == 'ʼ' || c == '\''

    fun apply(kind: Int, s: String, loc: Locale): String = when (kind) {
        UPPER -> s.uppercase(loc)
        LOWER -> s.lowercase(loc)
        TITLE -> title(s, loc)
        SENTENCE -> sentence(s, loc)
        TIDY -> tidy(s)
        else -> s
    }

    /** Har bir so'zning birinchi harfi katta, qolgani kichik ("oʻzbek tili" -> "Oʻzbek Tili"). */
    fun title(s: String, loc: Locale): String {
        val sb = StringBuilder(s.length)
        var start = true
        for (c in s) {
            if (isWordChar(c)) {
                sb.append(if (start) c.toString().uppercase(loc) else c.toString().lowercase(loc))
                start = false
            } else {
                sb.append(c)
                start = true
            }
        }
        return sb.toString()
    }

    /** Hammasi kichik, faqat gap boshi katta ("SALOM. QALAYSIZ" -> "Salom. Qalaysiz"). */
    fun sentence(s: String, loc: Locale): String {
        val sb = StringBuilder(s.length)
        var cap = true
        for (c in s.lowercase(loc)) {
            if (cap && c.isLetter()) {
                sb.append(c.toString().uppercase(loc))
                cap = false
            } else {
                sb.append(c)
                if (c == '.' || c == '!' || c == '?' || c == '\n') cap = true
                else if (!c.isWhitespace() && c != '"' && c != '«' && c != '(') cap = false
            }
        }
        return sb.toString()
    }

    /** Ortiqcha bo'sh joylar, tinish belgisi oldidagi bo'sh joy, ketma-ket bo'sh qatorlar. */
    fun tidy(s: String): String {
        var t = s.replace('\t', ' ')
        t = t.replace(Regex(" {2,}"), " ")
        t = t.replace(Regex(" +\n"), "\n")
        t = t.replace(Regex("\n{3,}"), "\n\n")
        t = t.replace(Regex(" +([,.;:!?])"), "$1")
        return t.trim()
    }
}
