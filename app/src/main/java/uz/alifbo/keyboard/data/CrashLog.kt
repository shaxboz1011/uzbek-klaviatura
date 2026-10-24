package uz.alifbo.keyboard.data

import android.content.Context
import android.os.Build
import uz.alifbo.keyboard.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Qulashlarni (kutilmagan xatolarni) telefonda saqlaydi, shunda "Muammo haqida xabar" tugmasi ularni hisobotga qoʻshishi mumkin.
 * Faqat xato izi va qurilma/versiya maʼlumoti yoziladi — siz yozgan matn, bufer yoki oʻrganilgan soʻzlar HECH QACHON yozilmaydi.
 */
object CrashLog {
    private const val MAX_FILE = 24_000
    private const val MAX_TRACE = 3_000

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "crash.txt")

    /** Eski yozuvga yangisini qoʻshadi va umumiy hajmni max belgigacha qisqartiradi (eng eski qismi tashlanadi). Sof funksiya. */
    fun appendTrimmed(existing: String, entry: String, max: Int): String {
        val all = existing + entry
        return if (all.length <= max) all else all.substring(all.length - max)
    }

    fun install(ctx: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                record(ctx, t, e)
            } catch (ignored: Throwable) {
                // yozib boʻlmasa ham tizimning oddiy xato qayta ishlovchisiga topshiramiz
            }
            prev?.uncaughtException(t, e)
        }
    }

    private fun record(ctx: Context, t: Thread, e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val trace = sw.toString().take(MAX_TRACE)
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "=== $ts  v${BuildConfig.VERSION_NAME}  oqim: ${t.name}\n$trace\n"
        val f = file(ctx)
        val old = if (f.exists()) f.readText() else ""
        f.writeText(appendTrimmed(old, entry, MAX_FILE))
    }

    fun last(ctx: Context): String? {
        val f = file(ctx)
        return if (f.exists() && f.length() > 0) f.readText().takeLast(MAX_TRACE + 200) else null
    }

    fun clear(ctx: Context) {
        file(ctx).delete()
    }
}

/** "Muammo haqida xabar" hisoboti: qurilma va sozlamalar haqida shaxsiy boʻlmagan maʼlumot. */
object ReportBuilder {
    fun build(ctx: Context, description: String, includeCrash: Boolean): String {
        val prefs = Prefs(ctx)
        val rt = Runtime.getRuntime()
        val sb = StringBuilder()
        if (description.isNotBlank()) sb.append("### Tavsif\n").append(description.trim()).append("\n\n")
        sb.append("### Qurilma va ilova\n")
        sb.append("- Ilova: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
        sb.append("- Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("- Qurilma: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        sb.append("- Til/mintaqa: ").append(Locale.getDefault().toLanguageTag()).append('\n')
        sb.append("- Xotira: ").append(rt.maxMemory() / (1024 * 1024)).append(" MB maks\n")
        sb.append("- Yoqilgan alifbolar: ").append(prefs.enabledIds.joinToString(", ")).append('\n')
        sb.append("- Joriy alifbo: ").append(prefs.currentId.ifEmpty { "(birinchisi)" }).append('\n')
        sb.append("- Sozlamalar: mavzu=").append(prefs.theme)
            .append(", balandlik=").append(prefs.heightPercent)
            .append(", swipe=").append(prefs.swipe)
            .append(", avto-tuzatish=").append(prefs.autoCorrect)
            .append(", tavsiyalar=").append(prefs.suggestions)
            .append(", raqamlar qatori=").append(prefs.numberRow).append('\n')
        if (includeCrash) {
            val crash = CrashLog.last(ctx)
            if (crash != null) sb.append("\n### Oxirgi xato izi\n```\n").append(crash).append("\n```\n")
        }
        sb.append("\n_Hisobotda siz yozgan matn, bufer va oʻrganilgan soʻzlar yoʻq._\n")
        return sb.toString()
    }
}
