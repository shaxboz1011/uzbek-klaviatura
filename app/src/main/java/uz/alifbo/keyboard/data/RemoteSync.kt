package uz.alifbo.keyboard.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONObject
import uz.alifbo.keyboard.work.SyncWorker
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Serverdagi registry.json dan paket versiyalarini solishtirib, yangilarini yuklaydi.
 *
 * registry.json:
 *   { "schema":1, "packs":[ {"id":"uz-latn-2026","version":2,"url":"packs/uz-latn-2026.json","sha256":"..."} ] }
 * url — registry.json ga nisbatan yoki to'liq https manzil.
 */
object RemoteSync {
    private const val MAX_BYTES = 256 * 1024
    private const val OPEN_INTERVAL_MS = 30 * 60 * 1000L

    data class SyncResult(val ok: Boolean, val updated: List<String>, val message: String)

    private fun net() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(net())
            .build()
        WorkManager.getInstance(ctx.applicationContext)
            .enqueueUniquePeriodicWork("alifbo-sync-periodic", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Klaviatura ochilganda chaqiriladi; 30 daqiqadan tez-tez tekshirmaydi. */
    fun maybeSync(ctx: Context) {
        val prefs = Prefs(ctx)
        if (System.currentTimeMillis() - prefs.lastSyncMs < OPEN_INTERVAL_MS) return
        val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(net()).build()
        WorkManager.getInstance(ctx.applicationContext)
            .enqueueUniqueWork("alifbo-sync-now", ExistingWorkPolicy.KEEP, req)
    }

    @Synchronized
    fun syncBlocking(ctx: Context): SyncResult {
        val prefs = Prefs(ctx)
        val res = try {
            doSync(ctx, prefs)
        } catch (e: Exception) {
            SyncResult(false, emptyList(), "Xato: ${e.javaClass.simpleName} ${e.message ?: ""}".trim())
        }
        prefs.lastSyncMs = System.currentTimeMillis()
        prefs.lastSyncInfo = res.message
        return res
    }

    private fun doSync(ctx: Context, prefs: Prefs): SyncResult {
        val base = prefs.registryUrl
        if (base.contains("OWNER/REPO")) {
            return SyncResult(true, emptyList(), "Registry manzili hali sozlanmagan (Sozlamalar → Yangilanishlar)")
        }
        if (!base.startsWith("https://")) {
            return SyncResult(true, emptyList(), "Registry manzili https:// bilan boshlanishi kerak")
        }

        val reg = get(base, prefs.etag)
        if (reg.code == 304) return SyncResult(true, emptyList(), "Yangilanish yoʻq")
        if (reg.code !in 200..299 || reg.body == null) {
            return SyncResult(false, emptyList(), "Registry javobi: HTTP ${reg.code}")
        }

        val root = JSONObject(String(reg.body, Charsets.UTF_8))
        if (root.optInt("schema", 1) > 1) {
            return SyncResult(true, emptyList(), "Registry yangi formatda — ilovani yangilang")
        }
        val arr = root.getJSONArray("packs")
        val updated = ArrayList<String>()
        var failed = 0

        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val id = e.getString("id")
            val ver = e.getInt("version")
            if (ver <= PackStore.versionOf(ctx, id)) continue

            val abs = URL(URL(base), e.getString("url")).toString()
            if (!abs.startsWith("https://")) {
                failed++
                continue
            }
            val r = get(abs, null)
            if (r.code !in 200..299 || r.body == null) {
                failed++
                continue
            }
            val want = e.optString("sha256", "")
            if (want.isNotEmpty() && !sha256(r.body).equals(want, ignoreCase = true)) {
                failed++
                continue
            }
            try {
                PackStore.install(ctx, id, String(r.body, Charsets.UTF_8))
                updated.add(id)
            } catch (ex: Exception) {
                failed++
            }
        }

        if (updated.isNotEmpty()) PackStore.invalidate()
        // ETag ni faqat hammasi muvaffaqiyatli qo'llangan bo'lsa eslab qolamiz,
        // aks holda keyingi safar yana urinib ko'riladi.
        if (failed == 0) prefs.etag = reg.etag

        val msg = buildString {
            if (updated.isEmpty()) append("Yangilanish yoʻq") else append("Yangilandi: ${updated.joinToString(", ")}")
            if (failed > 0) append(" ($failed ta paket yuklanmadi)")
        }
        return SyncResult(failed == 0, updated, msg)
    }

    private class Resp(val code: Int, val body: ByteArray?, val etag: String?)

    private fun get(url: String, etag: String?): Resp {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.instanceFollowRedirects = true
            c.setRequestProperty("Accept", "application/json")
            if (etag != null) c.setRequestProperty("If-None-Match", etag)
            val code = c.responseCode
            if (code == 304) return Resp(304, null, etag)
            if (code !in 200..299) return Resp(code, null, null)
            val bytes = c.inputStream.use { readLimited(it) }
            return Resp(code, bytes, c.getHeaderField("ETag"))
        } finally {
            c.disconnect()
        }
    }

    private fun readLimited(ins: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BYTES) throw IllegalStateException("javob juda katta")
        }
        return out.toByteArray()
    }

    private fun sha256(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
