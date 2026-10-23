package uz.alifbo.keyboard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bufer (clipboard) tarixi. Faqat shu ilovaning shaxsiy xotirasida saqlanadi, internetga chiqmaydi.
 * Maxfiy deb belgilangan nusxalar (parol menejerlari) saqlanmaydi.
 */
object ClipStore {
    data class Item(val text: String, val pinned: Boolean)

    private const val MAX_UNPINNED = 30
    private const val MAX_LEN = 2000

    private var items: MutableList<Item>? = null

    @Synchronized
    private fun load(ctx: Context): MutableList<Item> {
        items?.let { return it }
        val out = ArrayList<Item>()
        val s = ctx.applicationContext.getSharedPreferences("clips", Context.MODE_PRIVATE).getString("items", null)
        if (s != null) {
            try {
                val a = JSONArray(s)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    out.add(Item(o.getString("t"), o.optBoolean("p", false)))
                }
            } catch (e: Exception) {
                out.clear()
            }
        }
        items = out
        return out
    }

    private fun save(ctx: Context) {
        val a = JSONArray()
        val cur = items ?: return
        for (x in cur) a.put(JSONObject().put("t", x.text).put("p", x.pinned))
        ctx.applicationContext.getSharedPreferences("clips", Context.MODE_PRIVATE)
            .edit().putString("items", a.toString()).apply()
    }

    /** Pinlanganlar tepada, keyin eng yangilari. */
    @Synchronized
    fun list(ctx: Context): List<Item> {
        val l = load(ctx)
        return l.filter { it.pinned } + l.filter { !it.pinned }
    }

    @Synchronized
    fun add(ctx: Context, text: String) {
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_LEN) return
        val l = load(ctx)
        val old = l.firstOrNull { it.text == t }
        l.removeAll { it.text == t }
        l.add(0, Item(t, old?.pinned ?: false))
        var unpinned = 0
        val iter = l.iterator()
        while (iter.hasNext()) {
            val x = iter.next()
            if (!x.pinned && ++unpinned > MAX_UNPINNED) iter.remove()
        }
        save(ctx)
    }

    @Synchronized
    fun togglePin(ctx: Context, text: String) {
        val l = load(ctx)
        val i = l.indexOfFirst { it.text == text }
        if (i >= 0) {
            l[i] = l[i].copy(pinned = !l[i].pinned)
            save(ctx)
        }
    }

    /** Pinlanmaganlarni o'chiradi. */
    @Synchronized
    fun clearUnpinned(ctx: Context) {
        load(ctx).removeAll { !it.pinned }
        save(ctx)
    }

    @Synchronized
    fun clearAll(ctx: Context) {
        load(ctx).clear()
        save(ctx)
    }
}
