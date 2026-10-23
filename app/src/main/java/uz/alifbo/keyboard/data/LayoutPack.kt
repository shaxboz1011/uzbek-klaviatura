package uz.alifbo.keyboard.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Bitta tugma.
 * label "@..." bilan boshlansa — maxsus tugma (shift, del, ...), aks holda yoziladigan harf (kichik shaklda).
 */
data class KeySpec(
    val label: String,
    val weight: Float,
    val alternates: List<String>,
    val hint: String?
) {
    // "@" oʻzi (bitta belgi) — oddiy belgi; "@shift", "@del" ... — maxsus tugmalar.
    val isToken: Boolean get() = label.length > 1 && label.startsWith("@")
}

/** Bitta alifbo/til paketi (JSON dan o'qiladi). */
class LayoutPack(
    val id: String,
    val name: String,
    val short: String,
    val country: String,
    val localeTag: String,
    val version: Int,
    val effective: String?,
    val note: String?,
    val rows: List<List<KeySpec>>,
    val bottom: List<KeySpec>
) {
    val locale: Locale = Locale.forLanguageTag(localeTag)
}

object DefaultLayers {
    private fun k(label: String, w: Float = 1f, vararg alt: String) =
        KeySpec(label, w, alt.toList(), null)

    /** Barcha tillar uchun umumiy raqam/belgilar qatlami. */
    val symbols: List<List<KeySpec>> = listOf(
        "1234567890".map { k(it.toString()) },
        listOf(
            k("@"), k("#"), k("\$", 1f, "€", "£", "¥", "₽", "₸"), k("%", 1f, "‰"), k("&"),
            k("*"), k("-", 1f, "_", "–", "—"), k("+", 1f, "±"), k("("), k(")")
        ),
        listOf(
            k("!", 1f, "¡"), k("\"", 1f, "«", "»", "“", "”"), k("'", 1f, "ʼ", "ʻ", "‘", "’"),
            k(":"), k(";"), k("/", 1f, "\\"), k("?", 1f, "¿"), k("_"), k("=", 1f, "≠", "≈"),
            k("@del", 1f)
        ),
        listOf(k("@abc", 1.5f), k(","), k("@space", 5f), k("."), k("@enter", 1.5f))
    )
}

object PackParser {
    const val SUPPORTED_SCHEMA = 1

    private val ID_RE = Regex("^[a-z0-9][a-z0-9-]{1,31}$")
    private val TOKENS = setOf("@shift", "@del", "@sym", "@abc", "@lang", "@space", "@enter")
    private val DEFAULT_WEIGHTS = mapOf(
        "@shift" to 1.5f, "@del" to 1.5f, "@sym" to 1.3f, "@abc" to 1.3f,
        "@lang" to 1.1f, "@space" to 4f, "@enter" to 1.5f
    )

    /** Noto'g'ri paket bo'lsa IllegalArgumentException / JSONException tashlaydi. */
    fun parse(text: String): LayoutPack {
        val o = JSONObject(text)
        require(o.optInt("schema", 1) <= SUPPORTED_SCHEMA) { "schema qo'llab-quvvatlanmaydi" }
        val id = o.getString("id")
        require(ID_RE.matches(id)) { "id noto'g'ri: $id" }
        val version = o.getInt("version")
        require(version >= 1) { "version >= 1 bo'lishi kerak" }
        val name = o.getString("name")
        val short = o.optString("short", name.take(6))
        val alt = alternates(o.optJSONObject("alternates"))

        val rowsJson = o.getJSONArray("rows")
        require(rowsJson.length() in 1..5) { "rows soni 1..5 bo'lishi kerak" }
        val rows = ArrayList<List<KeySpec>>()
        for (i in 0 until rowsJson.length()) rows.add(row(rowsJson.getJSONArray(i), alt))

        if (o.optBoolean("numberHints", true)) {
            val idx = o.optInt("numberHintsRow", 0)
            if (idx in rows.indices) rows[idx] = withNumbers(rows[idx])
        }

        val bottom = o.optJSONArray("bottom")?.let { row(it, alt) } ?: defaultBottom()
        require(bottom.any { it.label == "@space" }) { "bottom qatorida @space bo'lishi kerak" }

        return LayoutPack(
            id = id,
            name = name,
            short = short,
            country = o.optString("country", ""),
            localeTag = o.optString("locale", "en"),
            version = version,
            effective = o.optString("effective", "").ifEmpty { null },
            note = o.optString("note", "").ifEmpty { null },
            rows = rows,
            bottom = bottom
        )
    }

    private fun defaultBottom(): List<KeySpec> = listOf(
        KeySpec("@sym", 1.3f, emptyList(), null),
        KeySpec("@lang", 1.1f, emptyList(), null),
        KeySpec(",", 1f, emptyList(), null),
        KeySpec("@space", 4.1f, emptyList(), null),
        KeySpec(".", 1f, emptyList(), null),
        KeySpec("@enter", 1.5f, emptyList(), null)
    )

    private fun alternates(o: JSONObject?): Map<String, List<String>> {
        if (o == null) return emptyMap()
        val m = HashMap<String, List<String>>()
        val it = o.keys()
        while (it.hasNext()) {
            val key = it.next()
            val arr = o.getJSONArray(key)
            val list = ArrayList<String>()
            for (i in 0 until arr.length()) list.add(arr.getString(i))
            m[key] = list
        }
        return m
    }

    private fun row(a: JSONArray, alt: Map<String, List<String>>): List<KeySpec> {
        require(a.length() in 1..14) { "qatordagi tugmalar soni 1..14 bo'lishi kerak" }
        val out = ArrayList<KeySpec>()
        for (i in 0 until a.length()) {
            val e = a.get(i)
            val label: String
            var w: Float? = null
            val extra = ArrayList<String>()
            if (e is JSONObject) {
                label = e.getString("k")
                if (e.has("w")) w = e.getDouble("w").toFloat()
                val ea = e.optJSONArray("alt")
                if (ea != null) for (j in 0 until ea.length()) extra.add(ea.getString(j))
            } else {
                label = e.toString()
            }
            val token = label.length > 1 && label.startsWith("@")
            if (token) {
                require(label in TOKENS) { "noma'lum maxsus tugma: $label" }
            } else {
                require(label.isNotEmpty() && label.length <= 4) { "tugma yorlig'i noto'g'ri: '$label'" }
            }
            val weight = w ?: DEFAULT_WEIGHTS[label] ?: 1f
            require(weight in 0.5f..8f) { "tugma kengligi 0.5..8 bo'lishi kerak" }

            val alts = LinkedHashSet<String>()
            alts.addAll(extra)
            if (!token) alt[label]?.let { alts.addAll(it) }
            alts.remove(label)
            out.add(KeySpec(label, weight, alts.toList(), null))
        }
        return out
    }

    /** Birinchi 10 ta harf tugmasiga 1..0 raqamlarini bosib turganda chiqadigan qilib biriktiradi. */
    private fun withNumbers(r: List<KeySpec>): List<KeySpec> {
        val digits = "1234567890"
        var n = 0
        return r.map { k ->
            if (k.isToken || n >= 10) {
                k
            } else {
                val d = digits[n].toString()
                n++
                k.copy(hint = d, alternates = listOf(d) + k.alternates.filter { it != d })
            }
        }
    }
}
