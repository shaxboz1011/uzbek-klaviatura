package uz.alifbo.keyboard.data

import android.os.Handler
import android.os.Looper
import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Tezkor tarjima. Matn tarjima uchun MyMemory (api.mymemory.translated.net) xizmatiga yuboriladi,
 * shuning uchun foydalanuvchi avval rozilik beradi va parol/email/URL maydonlarida ishlamaydi.
 * Bepul limit: kuniga taxminan 5000 belgi (IP boʻyicha), bitta soʻrovda 500 belgigacha.
 */
object Translator {
    data class Lang(val code: String, val name: String)

    val LANGS = listOf(
        Lang("uz", "Oʻzbekcha"), Lang("en", "English"), Lang("ru", "Русский"), Lang("tr", "Türkçe"),
        Lang("kk", "Қазақша"), Lang("ky", "Кыргызча"), Lang("tg", "Тоҷикӣ"), Lang("az", "Azərbaycanca"),
        Lang("ar", "العربية"), Lang("de", "Deutsch"), Lang("fr", "Français"), Lang("es", "Español"),
        Lang("zh", "中文"), Lang("ko", "한국어"), Lang("ja", "日本語")
    )

    const val MAX_CHARS = 1500
    private const val CHUNK = 400
    private const val ENDPOINT = "https://api.mymemory.translated.net/get"

    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newSingleThreadExecutor()
    private var ticket = 0

    fun nameOf(code: String): String = LANGS.firstOrNull { it.code == code }?.name ?: code

    /** Natija asosiy oqimda qaytadi. Yangi soʻrov eskisining natijasini bekor qiladi. */
    fun translate(text: String, from: String, to: String, cb: (result: String?, error: String?) -> Unit) {
        val t = ++ticket
        val src = text.take(MAX_CHARS)
        pool.execute {
            var out: String? = null
            var err: String? = null
            try {
                out = if (from == to) src else translateText(src, from, to)
            } catch (e: Exception) {
                err = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            }
            main.post { if (t == ticket) cb(out, err) }
        }
    }

    private fun translateText(text: String, from: String, to: String): String {
        val lines = text.split("\n")
        val sb = StringBuilder()
        for ((li, line) in lines.withIndex()) {
            if (li > 0) sb.append('\n')
            if (line.isBlank()) continue
            val parts = split(line.trim(), CHUNK)
            sb.append(parts.joinToString(" ") { translateChunk(it, from, to) })
        }
        return sb.toString()
    }

    /** Matnni jumla/soʻz chegarasida CHUNK belgidan oshmaydigan boʻlaklarga boʻladi. */
    private fun split(s: String, max: Int): List<String> {
        val out = ArrayList<String>()
        var rest = s
        while (rest.length > max) {
            var cut = -1
            for (i in max downTo max / 2) {
                val ch = rest[i - 1]
                if (ch == '.' || ch == '!' || ch == '?') {
                    cut = i
                    break
                }
            }
            if (cut < 0) cut = rest.lastIndexOf(' ', max).let { if (it > max / 2) it else max }
            out.add(rest.substring(0, cut).trim())
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) out.add(rest)
        return out
    }

    private fun code(c: String) = if (c == "zh") "zh-CN" else c

    private fun translateChunk(q: String, from: String, to: String): String {
        val pair = URLEncoder.encode("${code(from)}|${code(to)}", "UTF-8")
        val url = URL("$ENDPOINT?q=${URLEncoder.encode(q, "UTF-8")}&langpair=$pair")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", "AlifboKlaviatura/1.0")
            val body = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText().take(64 * 1024) } ?: ""
            val json = JSONObject(body)
            val status = json.opt("responseStatus")?.toString()?.toIntOrNull() ?: conn.responseCode
            val tr = json.optJSONObject("responseData")?.optString("translatedText") ?: ""
            if (status == 429 || tr.contains("MYMEMORY WARNING", ignoreCase = true)) {
                throw Exception("Bugungi bepul tarjima limiti tugadi. Keyinroq urinib koʻring.")
            }
            if (status != 200 || tr.isEmpty()) {
                throw Exception(json.optString("responseDetails").ifEmpty { "Tarjima qilib boʻlmadi ($status)" })
            }
            @Suppress("DEPRECATION")
            return Html.fromHtml(tr, Html.FROM_HTML_MODE_LEGACY).toString()
        } catch (e: java.io.IOException) {
            throw Exception("Internetga ulanib boʻlmadi")
        } finally {
            conn.disconnect()
        }
    }
}
