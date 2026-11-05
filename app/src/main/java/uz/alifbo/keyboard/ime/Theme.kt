package uz.alifbo.keyboard.ime

import android.content.Context
import android.content.res.Configuration
import uz.alifbo.keyboard.R
import uz.alifbo.keyboard.data.Prefs

/** Klaviatura mavzusi: och/toʻq/tizim, urgʻu rangi va tayyor uslublar (Samsung / iOS / Redmi ga oʻxshash). */
object Theme {
    /** Urgʻu ranglari (ARGB). 0 — standart (uslubning yoki kb_accent). */
    val ACCENTS = intArrayOf(0, 0xFF1E9E5A.toInt(), 0xFF7B4DD6.toInt(), 0xFFE8710A.toInt(), 0xFFD6336C.toInt(), 0xFF0E8A8A.toInt())

    /**
     * Tayyor uslub: tugma shakli + ranglar. Ranglar tartibi: fon, tugma, maxsus tugma, bosilgan, matn, hint, popup, soya.
     * Uslub 0 (Samsung) ranglarni res/values dan oladi (light/dark null).
     */
    class Style(
        val id: Int,
        val name: String,
        val radius: Int,
        val gap: Int,
        val textScale: Int,
        val shadow: Boolean,
        /** Tugma bosilganda yuqorida "tomchi" (tugmaga ulangan) koʻrinadi. */
        val balloon: Boolean,
        val accent: Int,
        /** Klaviatura balandligi (%), qatorlar orasidagi vertikal masofa koʻpaytmasi va iOS ga oʻxshash pastki qator (123, 😀, boʻsh joy, return + 🌐/🎤). */
        val heightPct: Int,
        val vGapMul: Float,
        val ios: Boolean,
        val light: IntArray?,
        val dark: IntArray?
    )

    val STYLES: List<Style> = listOf(
        Style(0, "Samsung", 7, 3, 108, true, false, 0, 86, 2f, false, null, null),
        // Yumaloq, yirik harfli, tugma ostida soya va tomchi popup (iOS ga oʻxshash; ikonka va shriftlar nusxalanmaydi)
        Style(
            1, "Yumaloq (iOS ga oʻxshash)", 4, 3, 112, true, true, 0xFF0A84FF.toInt(), 92, 1.5f, true,
            intArrayOf(0xFFD3D5DB.toInt(), 0xFFFFFFFF.toInt(), 0xFFADB1BB.toInt(), 0xFFC2C5CD.toInt(), 0xFF000000.toInt(), 0xFF6B6F76.toInt(), 0xFFFFFFFF.toInt(), 0xFF8A8C90.toInt()),
            intArrayOf(0xFF212224.toInt(), 0xFF66676A.toInt(), 0xFF3F4043.toInt(), 0xFF808185.toInt(), 0xFFFFFFFF.toInt(), 0xFFA0A0A5.toInt(), 0xFF7A7B7F.toInt(), 0xFF0B0B0C.toInt())
        ),
        // Keng oraliq, yumshoq burchak, soyasiz, toʻq sariq urgʻu (Redmi/MIUI ga oʻxshash)
        Style(
            2, "Yumshoq (Redmi ga oʻxshash)", 8, 4, 100, false, false, 0xFFFF6900.toInt(), 100, 1f, false,
            intArrayOf(0xFFEEF0F4.toInt(), 0xFFFFFFFF.toInt(), 0xFFDDE1E8.toInt(), 0xFFCBD1DB.toInt(), 0xFF202124.toInt(), 0xFF7A7F87.toInt(), 0xFFFFFFFF.toInt(), 0x00000000),
            intArrayOf(0xFF17181A.toInt(), 0xFF2C2E31.toInt(), 0xFF232528.toInt(), 0xFF3A3D41.toInt(), 0xFFF1F3F4.toInt(), 0xFF8A8F96.toInt(), 0xFF3A3D41.toInt(), 0x00000000)
        )
    )

    fun isDark(ctx: Context) = isNight(ctx)

    fun style(ctx: Context): Style = STYLES.getOrNull(Prefs(ctx).stylePreset) ?: STYLES[0]

    private fun isNight(ctx: Context) =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** kb_* rang resursi: Samsung uslubida res/values dan, boshqa uslublarda uslub palitrasidan. */
    fun col(ctx: Context, res: Int): Int {
        val st = style(ctx)
        val pal = (if (isNight(ctx)) st.dark else st.light) ?: return ctx.getColor(res)
        val i = when (res) {
            R.color.kb_bg -> 0
            R.color.kb_key -> 1
            R.color.kb_key_special, R.color.kb_chip -> 2
            R.color.kb_key_pressed -> 3
            R.color.kb_text -> 4
            R.color.kb_hint -> 5
            R.color.kb_popup -> 6
            R.color.kb_key_shadow -> 7
            else -> return ctx.getColor(res)
        }
        return pal[i]
    }

    fun accent(ctx: Context): Int {
        val p = Prefs(ctx)
        if (p.accent != 0) return p.accent
        val st = style(ctx)
        return if (st.accent != 0) st.accent else ctx.getColor(R.color.kb_accent)
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
