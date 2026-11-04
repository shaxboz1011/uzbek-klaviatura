package uz.alifbo.keyboard.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size

/** Boʻsh maydonda taklif qilinadigan "eng oxirgi" narsalar: skrinshot (rasmlarga ruxsat bilan) va nusxalangan matn. */
object Recents {
    /** Tavsiya shu vaqtdan keyin yoʻqoladi. */
    const val FRESH_MS = 3 * 60 * 1000L

    class Shot(val uri: Uri, val mime: String, val takenAtMs: Long)

    fun hasImageAccess(ctx: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        return ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    }

    /** Oxirgi FRESH_MS ichida olingan skrinshot (papka yoki fayl nomida "screenshot"/"skrinshot"/"ekran" boʻlsa), yoʻqsa null. */
    fun latestScreenshot(ctx: Context, nowMs: Long = System.currentTimeMillis()): Shot? {
        if (!hasImageAccess(ctx)) return null
        try {
            val cols = mutableListOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATE_ADDED
            )
            if (Build.VERSION.SDK_INT >= 29) cols.add(MediaStore.Images.Media.RELATIVE_PATH)
            else @Suppress("DEPRECATION") cols.add(MediaStore.Images.Media.DATA)
            val since = (nowMs - FRESH_MS) / 1000
            ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                cols.toTypedArray(),
                "${MediaStore.Images.Media.DATE_ADDED} >= ?",
                arrayOf(since.toString()),
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n++ < 40) {
                    val name = c.getString(1) ?: ""
                    val where = c.getString(4) ?: ""
                    if (!looksLikeScreenshot(name, where)) continue
                    val id = c.getLong(0)
                    return Shot(
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                        c.getString(2) ?: "image/png",
                        c.getLong(3) * 1000
                    )
                }
            }
        } catch (e: Exception) {
            // ruxsat qaytarib olingan yoki MediaStore javob bermadi — tavsiya yoʻq
        }
        return null
    }

    fun looksLikeScreenshot(name: String, path: String): Boolean {
        val s = (name + " " + path).lowercase()
        return "screenshot" in s || "skrinshot" in s || "screen_shot" in s || "ekran" in s || "снимок экрана" in s || "скриншот" in s
    }

    fun thumbnail(ctx: Context, uri: Uri, px: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) {
            ctx.contentResolver.loadThumbnail(uri, Size(px, px), null)
        } else {
            val o = BitmapFactory.Options().apply { inSampleSize = 8 }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        }
    } catch (e: Exception) {
        null
    }
}
