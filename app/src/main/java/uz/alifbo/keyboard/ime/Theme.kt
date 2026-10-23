package uz.alifbo.keyboard.ime

import android.content.Context
import android.content.res.Configuration
import uz.alifbo.keyboard.R
import uz.alifbo.keyboard.data.Prefs

/** Klaviatura mavzusi: och/toʻq/tizim va urgʻu rangi. */
object Theme {
    /** Urgʻu ranglari (ARGB). 0 — standart (kb_accent). */
    val ACCENTS = intArrayOf(0, 0xFF1E9E5A.toInt(), 0xFF7B4DD6.toInt(), 0xFFE8710A.toInt(), 0xFFD6336C.toInt(), 0xFF0E8A8A.toInt())

    fun accent(ctx: Context): Int {
        val a = Prefs(ctx).accent
        return if (a != 0) a else ctx.getColor(R.color.kb_accent)
    }

    /** mode: 0 - tizim, 1 - och, 2 - toʻq. Resurslar (values / values-night) shu rejimga qarab tanlanadi. */
    fun themed(base: Context, mode: Int): Context {
        if (mode == 0) return base
        val cfg = Configuration(base.resources.configuration)
        val night = if (mode == 2) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return base.createConfigurationContext(cfg)
    }
}
