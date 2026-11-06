package uz.alifbo.keyboard.data

import android.content.Context

/** Matn qisqartmalari ombori (SharedPreferences, shu telefonda; internetga chiqmaydi). */
object Shortcuts {
    private var cache: List<ShortcutLogic.Shortcut>? = null

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("shortcuts", Context.MODE_PRIVATE)

    @Synchronized
    fun list(ctx: Context): List<ShortcutLogic.Shortcut> =
        cache ?: ShortcutLogic.parse(sp(ctx).getString("items", null)).also { cache = it }

    /** Qoʻshadi yoki shu qisqartmani almashtiradi. false — qisqartma/matn yaroqsiz yoki joy toʻlgan. */
    @Synchronized
    fun put(ctx: Context, abbr: String, text: String): Boolean {
        val a = ShortcutLogic.normalizeAbbr(abbr) ?: return false
        val t = text.trim()
        if (t.isEmpty() || t.length > ShortcutLogic.MAX_TEXT) return false
        val cur = list(ctx)
        if (cur.size >= ShortcutLogic.MAX_COUNT && cur.none { it.abbr == a }) return false
        save(ctx, cur.filter { it.abbr != a } + ShortcutLogic.Shortcut(a, t))
        return true
    }

    @Synchronized
    fun remove(ctx: Context, abbr: String) = save(ctx, list(ctx).filter { it.abbr != abbr })

    private fun save(ctx: Context, l: List<ShortcutLogic.Shortcut>) {
        cache = l
        sp(ctx).edit().putString("items", ShortcutLogic.toJson(l)).apply()
    }

    fun find(ctx: Context, typed: String) = ShortcutLogic.find(list(ctx), typed)
}
