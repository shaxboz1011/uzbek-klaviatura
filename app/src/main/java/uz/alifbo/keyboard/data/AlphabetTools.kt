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

    /** Oʻzbek kirill -> lotin (1995 imlosi: oʻ, gʻ, sh, ch). Rus kirillidagi harflar (ц, щ, ы...) ham yaqin lotin shakliga oʻgiriladi. */
    fun cyrToLat(s: String): String {
        val sb = StringBuilder()
        val vowels = "аеёиоуўэюяАЕЁИОУЎЭЮЯ"
        for ((i, ch) in s.withIndex()) {
            val prev = if (i > 0) s[i - 1] else ' '
            val startOrVowel = !prev.isLetter() || prev in vowels || prev == 'ъ' || prev == 'Ъ'
            val up = ch.isUpperCase()
            fun cap(x: String) = if (up) x.replaceFirstChar { it.uppercase() } else x
            when (ch.lowercaseChar()) {
                'а' -> sb.append(cap("a")); 'б' -> sb.append(cap("b")); 'в' -> sb.append(cap("v")); 'г' -> sb.append(cap("g"))
                'д' -> sb.append(cap("d")); 'е' -> sb.append(if (startOrVowel) cap("ye") else cap("e"))
                'ё' -> sb.append(cap("yo")); 'ж' -> sb.append(cap("j")); 'з' -> sb.append(cap("z")); 'и' -> sb.append(cap("i"))
                'й' -> sb.append(cap("y")); 'к' -> sb.append(cap("k")); 'л' -> sb.append(cap("l")); 'м' -> sb.append(cap("m"))
                'н' -> sb.append(cap("n")); 'о' -> sb.append(cap("o")); 'п' -> sb.append(cap("p")); 'р' -> sb.append(cap("r"))
                'с' -> sb.append(cap("s")); 'т' -> sb.append(cap("t")); 'у' -> sb.append(cap("u")); 'ф' -> sb.append(cap("f"))
                'х' -> sb.append(cap("x")); 'ц' -> sb.append(cap("ts")); 'ч' -> sb.append(cap("ch")); 'ш' -> sb.append(cap("sh"))
                'щ' -> sb.append(cap("shch")); 'ъ' -> sb.append(TUTUQ); 'ы' -> sb.append(cap("i")); 'ь' -> {}
                'э' -> sb.append(cap("e")); 'ю' -> sb.append(cap("yu")); 'я' -> sb.append(cap("ya"))
                'ў' -> sb.append(cap("o") + OKINA); 'қ' -> sb.append(cap("q")); 'ғ' -> sb.append(cap("g") + OKINA)
                'ҳ' -> sb.append(cap("h"))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

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
