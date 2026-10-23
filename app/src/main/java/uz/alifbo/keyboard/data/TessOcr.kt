package uz.alifbo.keyboard.data

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kirill yozuvini oʻqish (Tesseract, telefonning oʻzida). Til paketi (`uzb_cyrl`, `rus`) birinchi marta ishlatilganda
 * GitHub'dagi rasmiy tessdata_fast omboridan yuklanadi (1.5–4 MB) va telefonda saqlanadi.
 */
object TessOcr {
    const val UZ_CYRL = "uzb_cyrl"
    const val RUS = "rus"

    private const val BASE = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/"
    private const val MIN_BYTES = 500_000L

    private fun root(ctx: Context) = File(ctx.applicationContext.filesDir, "tess")
    private fun file(ctx: Context, lang: String) = File(File(root(ctx), "tessdata"), "$lang.traineddata")

    fun hasLang(ctx: Context, lang: String) = file(ctx, lang).let { it.exists() && it.length() > MIN_BYTES }

    /** Fon oqimida chaqiring. Xato boʻlsa — xabar, muvaffaqiyatda null. */
    fun download(ctx: Context, lang: String, onProgress: (Int) -> Unit): String? {
        val dst = file(ctx, lang)
        dst.parentFile?.mkdirs()
        val tmp = File(dst.parentFile, "$lang.part")
        try {
            val c = URL(BASE + "$lang.traineddata").openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 10000
                c.readTimeout = 20000
                c.instanceFollowRedirects = true
                if (c.responseCode !in 200..299) return "Til paketini yuklab boʻlmadi (HTTP ${c.responseCode})"
                val total = c.contentLengthLong
                c.inputStream.use { ins ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(32 * 1024)
                        var read = 0L
                        var last = -1
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val p = (read * 100 / total).toInt()
                                if (p != last) {
                                    last = p
                                    onProgress(p)
                                }
                            }
                        }
                    }
                }
            } finally {
                c.disconnect()
            }
            if (tmp.length() < MIN_BYTES) {
                tmp.delete()
                return "Til paketi nuqsonli yuklandi"
            }
            if (!tmp.renameTo(dst)) return "Til paketini saqlab boʻlmadi"
            return null
        } catch (e: Exception) {
            tmp.delete()
            return "Internetga ulanib boʻlmadi"
        }
    }

    /** Fon oqimida chaqiring. */
    fun recognize(ctx: Context, bmp: Bitmap, lang: String): String {
        val api = TessBaseAPI()
        try {
            if (!api.init(root(ctx).absolutePath, lang)) return ""
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            api.setImage(bmp)
            return api.getUTF8Text() ?: ""
        } finally {
            api.recycle()
        }
    }
}
