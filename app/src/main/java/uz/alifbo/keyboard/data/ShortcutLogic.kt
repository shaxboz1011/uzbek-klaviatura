package uz.alifbo.keyboard.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Matn qisqartmalari: "mnm" yozib boʻsh joy bosilsa, "Menga ham kerak" chiqadi. Android'ga bogʻliq emas (testlanadi). */
object ShortcutLogic {
    class Shortcut(val abbr: String, val text: String)

    const val MAX_COUNT = 200
    const val MAX_TEXT = 500
    const val MAX_ABBR = 16

    private val WEEKDAYS = arrayOf("yakshanba", "dushanba", "seshanba", "chorshanba", "payshanba", "juma", "shanba")

    /** Qisqartma: 1..16 ta harf (yoki ʻ ʼ '), boʻsh joysiz, kichik harfda. Yaroqsiz boʻlsa null. */
    fun normalizeAbbr(s: String): String? {
        val t = s.trim().lowercase()
        if (t.isEmpty() || t.length > MAX_ABBR) return null
        if (!t.all { it.isLetter() || it == 'ʻ' || it == 'ʼ' || it == '\'' }) return null
        return t
    }

    fun find(list: List<Shortcut>, typed: String): Shortcut? {
        val k = typed.lowercase()
        return list.firstOrNull { it.abbr == k }
    }

    /** Bosh harf bilan yozilgan qisqartma — matn ham bosh harf bilan boshlanadi. */
    fun applyCase(typed: String, text: String): String =
        if (typed.isNotEmpty() && typed[0].isUpperCase() && text.isNotEmpty() && text[0].isLowerCase()) text.replaceFirstChar { it.uppercase() } else text

    /** {sana} -> 05.11.2026, {vaqt} -> 14:30, {hafta} -> payshanba. */
    fun expandVars(text: String, cal: Calendar): String {
        if ('{' !in text) return text
        fun two(n: Int) = n.toString().padStart(2, '0')
        return text
            .replace("{sana}", "${two(cal.get(Calendar.DAY_OF_MONTH))}.${two(cal.get(Calendar.MONTH) + 1)}.${cal.get(Calendar.YEAR)}")
            .replace("{vaqt}", "${two(cal.get(Calendar.HOUR_OF_DAY))}:${two(cal.get(Calendar.MINUTE))}")
            .replace("{hafta}", WEEKDAYS[cal.get(Calendar.DAY_OF_WEEK) - 1])
    }

    fun toJson(list: List<Shortcut>): String {
        val a = JSONArray()
        for (s in list) a.put(JSONObject().put("a", s.abbr).put("t", s.text))
        return a.toString()
    }

    fun parse(json: String?): List<Shortcut> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val a = JSONArray(json)
            val out = ArrayList<Shortcut>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val abbr = normalizeAbbr(o.getString("a")) ?: continue
                val t = o.getString("t")
                if (t.isNotEmpty() && t.length <= MAX_TEXT) out.add(Shortcut(abbr, t))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Boshlovchilar uchun namunalar. */
    val SAMPLES = listOf(
        Shortcut("ass", "Assalomu alaykum"),
        Shortcut("rah", "Katta rahmat!"),
        Shortcut("xk", "Xayrli kun!"),
        Shortcut("sn", "{sana}"),
        Shortcut("vq", "{vaqt}")
    )
}
