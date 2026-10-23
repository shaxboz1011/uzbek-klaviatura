package uz.alifbo.keyboard.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Paketlar ombori.
 *  1) ilova ichidagi boshlang'ich paketlar (assets/packs/*.json)
 *  2) serverdan yuklangan yangilari (filesDir/packs/*.json)
 * Bir xil id bo'lsa — versiyasi yuqorisi ustun turadi.
 */
object PackStore {
    private const val TAG = "PackStore"

    @Volatile
    private var cache: List<LayoutPack>? = null

    /** Paketlar o'zgarganda oshib boradi; klaviatura shuni solishtirib qayta yuklaydi. */
    @Volatile
    var generation: Int = 0
        private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    fun all(ctx: Context): List<LayoutPack> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val map = LinkedHashMap<String, LayoutPack>()

            val am = ctx.applicationContext.assets
            for (name in (am.list("packs") ?: emptyArray()).sorted()) {
                if (!name.endsWith(".json")) continue
                try {
                    val text = am.open("packs/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val p = PackParser.parse(text)
                    map[p.id] = p
                } catch (e: Exception) {
                    Log.w(TAG, "asset paket o'qilmadi: $name", e)
                }
            }

            val dir = File(ctx.applicationContext.filesDir, "packs")
            val files = dir.listFiles { f -> f.name.endsWith(".json") }
            files?.sortedBy { it.name }?.forEach { f ->
                try {
                    val p = PackParser.parse(f.readText(Charsets.UTF_8))
                    val old = map[p.id]
                    if (old == null || p.version >= old.version) map[p.id] = p
                } catch (e: Exception) {
                    Log.w(TAG, "yuklangan paket o'qilmadi: ${f.name}", e)
                }
            }

            val list = map.values.toList()
            cache = list
            return list
        }
    }

    fun versionOf(ctx: Context, id: String): Int =
        all(ctx).firstOrNull { it.id == id }?.version ?: 0

    /** Yuklangan paketni tekshirib, atomik tarzda saqlaydi. */
    @Throws(IOException::class)
    fun install(ctx: Context, expectedId: String, json: String): LayoutPack {
        val p = PackParser.parse(json)
        require(p.id == expectedId) { "id mos kelmadi: ${p.id} != $expectedId" }
        val dir = File(ctx.applicationContext.filesDir, "packs").apply { mkdirs() }
        val tmp = File(dir, "$expectedId.tmp")
        tmp.writeText(json, Charsets.UTF_8)
        val dst = File(dir, "$expectedId.json")
        if (!tmp.renameTo(dst)) {
            dst.delete()
            if (!tmp.renameTo(dst)) throw IOException("fayl almashtirilmadi")
        }
        return p
    }

    fun invalidate() {
        cache = null
        generation++
        main.post { listeners.forEach { it() } }
    }
}
