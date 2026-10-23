package uz.alifbo.keyboard.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import org.json.JSONObject
import uz.alifbo.keyboard.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Releases orqali yangilanish: yangi versiyani tekshiradi, APK ni yuklab oladi va
 * tizim oʻrnatuvchisiga (PackageInstaller) topshiradi. Oʻrnatishni foydalanuvchi bir marta tasdiqlaydi.
 * Yangi APK shu ilovaning oʻsha imzosi bilan imzolangan boʻlishi shart (aks holda Android rad etadi).
 */
object Updater {
    class Release(val version: String, val notes: String, val apkUrl: String, val size: Long)

    const val ACTION_RESULT = "uz.alifbo.keyboard.INSTALL_RESULT"
    private const val MAX_APK = 60L * 1024 * 1024

    fun installedVersion(ctx: Context): String =
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").split(".", "-").mapNotNull { it.toIntOrNull() }

    /** a > b ? */
    fun isNewer(a: String, b: String): Boolean {
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size, 3)) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    /** Tarmoqqa chiqadi (fon oqimida chaqiring). Xatoda istisno tashlaydi. */
    fun fetchLatest(): Release? {
        val c = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "AlifboKlaviatura")
            if (c.responseCode == 404) return null
            if (c.responseCode !in 200..299) throw IllegalStateException("GitHub: HTTP ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText().take(512 * 1024) })
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
            val tag = o.optString("tag_name").removePrefix("v")
            val assets = o.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    val url = a.optString("browser_download_url")
                    if (!url.startsWith("https://")) continue
                    return Release(tag, o.optString("body").trim(), url, a.optLong("size", -1L))
                }
            }
            return null
        } finally {
            c.disconnect()
        }
    }

    /** Yangi versiya boʻlsa qaytaradi, boʻlmasa null. */
    fun checkNewer(ctx: Context): Release? {
        val r = fetchLatest() ?: return null
        return if (isNewer(r.version, installedVersion(ctx))) r else null
    }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /**
     * APK ni yuklab, oʻrnatish sessiyasiga yozadi va tasdiqlash oynasini chiqaradi.
     * Fon oqimida chaqiring. Xato boʻlsa — xabar, muvaffaqiyatda null.
     */
    fun downloadAndInstall(ctx: Context, rel: Release, onProgress: (Int) -> Unit): String? {
        val app = ctx.applicationContext
        val pi = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(app.packageName)
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        val session = pi.openSession(id)
        var done = false
        try {
            val c = URL(rel.apkUrl).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 10000
                c.readTimeout = 20000
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "AlifboKlaviatura")
                if (c.responseCode !in 200..299) return "Yuklab boʻlmadi (HTTP ${c.responseCode})"
                val total = if (rel.size > 0) rel.size else c.contentLengthLong
                if (total > MAX_APK) return "APK juda katta"
                session.openWrite("alifbo.apk", 0, total).use { out ->
                    c.inputStream.use { ins ->
                        val buf = ByteArray(16 * 1024)
                        var read = 0L
                        var last = -1
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (read > MAX_APK) return "APK juda katta"
                            if (total > 0) {
                                val pct = (read * 100 / total).toInt()
                                if (pct != last) {
                                    last = pct
                                    onProgress(pct)
                                }
                            }
                        }
                        session.fsync(out)
                    }
                }
            } finally {
                c.disconnect()
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
            val intent = Intent(app, InstallReceiver::class.java).setAction(ACTION_RESULT)
            val pending = PendingIntent.getBroadcast(app, id, intent, flags)
            session.commit(pending.intentSender)
            done = true
            return null
        } catch (e: Exception) {
            return e.message ?: e.javaClass.simpleName
        } finally {
            if (!done) {
                try {
                    session.abandon()
                } catch (e: Exception) {
                    // sessiya allaqachon yopilgan
                }
            }
            session.close()
        }
    }
}
