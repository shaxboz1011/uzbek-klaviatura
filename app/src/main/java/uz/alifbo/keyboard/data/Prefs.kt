package uz.alifbo.keyboard.data

import android.content.Context
import uz.alifbo.keyboard.BuildConfig

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("alifbo", Context.MODE_PRIVATE)

    /** Yoqilgan alifbolar (paket id lari), tartib bilan. */
    var enabledIds: List<String>
        get() = sp.getString("enabled", null)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: DEFAULT_ENABLED
        set(v) {
            sp.edit().putString("enabled", v.joinToString(",")).apply()
        }

    var currentId: String
        get() = sp.getString("current", "") ?: ""
        set(v) {
            sp.edit().putString("current", v).apply()
        }

    var registryUrl: String
        get() = sp.getString("registry", null) ?: BuildConfig.REGISTRY_URL
        set(v) {
            sp.edit().putString("registry", v.trim()).remove("etag").apply()
        }

    var etag: String?
        get() = sp.getString("etag", null)
        set(v) {
            sp.edit().putString("etag", v).apply()
        }

    var lastSyncMs: Long
        get() = sp.getLong("lastSync", 0L)
        set(v) {
            sp.edit().putLong("lastSync", v).apply()
        }

    var lastSyncInfo: String
        get() = sp.getString("lastSyncInfo", "") ?: ""
        set(v) {
            sp.edit().putString("lastSyncInfo", v).apply()
        }

    var haptic: Boolean
        get() = sp.getBoolean("haptic", true)
        set(v) {
            sp.edit().putBoolean("haptic", v).apply()
        }

    /** Tugma bosilganda tepasida harf chiqishi (Samsung kabi). */
    var keyPreview: Boolean
        get() = sp.getBoolean("keyPreview", true)
        set(v) {
            sp.edit().putBoolean("keyPreview", v).apply()
        }

    /** GitHub'da yangi versiya borligini avtomatik tekshirish. */
    var autoUpdateCheck: Boolean
        get() = sp.getBoolean("autoUpdate", true)
        set(v) {
            sp.edit().putBoolean("autoUpdate", v).apply()
        }

    /** Topilgan yangi versiya (boʻsh — yoʻq). */
    var updateVersion: String
        get() = sp.getString("updVer", "") ?: ""
        set(v) {
            sp.edit().putString("updVer", v).apply()
        }

    /** Bildirishnoma yuborilgan oxirgi versiya. */
    var notifiedVersion: String
        get() = sp.getString("updNotified", "") ?: ""
        set(v) {
            sp.edit().putString("updNotified", v).apply()
        }

    /** Avto-tuzatish (sinov): soʻz tugaganda xato yozilgan soʻzni lugʻatdagi yaqin soʻzga almashtiradi. */
    var autoCorrect: Boolean
        get() = sp.getBoolean("autoCorrect", false)
        set(v) {
            sp.edit().putBoolean("autoCorrect", v).apply()
        }

    /** Surib yozish (swipe). */
    var swipe: Boolean
        get() = sp.getBoolean("swipe", true)
        set(v) {
            sp.edit().putBoolean("swipe", v).apply()
        }

    /** Tugma bosilganda tovush. */
    var sound: Boolean
        get() = sp.getBoolean("sound", false)
        set(v) {
            sp.edit().putBoolean("sound", v).apply()
        }

    /** Tugma tovushi balandligi, foizda. */
    var soundVolume: Int
        get() = sp.getInt("soundVol", 40).coerceIn(5, 100)
        set(v) {
            sp.edit().putInt("soundVol", v).apply()
        }

    /** Ikki marta boʻsh joy — nuqta va boʻsh joy. */
    var doubleSpacePeriod: Boolean
        get() = sp.getBoolean("dblSpace", true)
        set(v) {
            sp.edit().putBoolean("dblSpace", v).apply()
        }

    /** 0 - tizimga ergashadi, 1 - och, 2 - toʻq. */
    var theme: Int
        get() = sp.getInt("theme", 0).coerceIn(0, 2)
        set(v) {
            sp.edit().putInt("theme", v).apply()
        }

    /** Urgʻu rangi (ARGB); 0 — standart. */
    var accent: Int
        get() = sp.getInt("accent", 0)
        set(v) {
            sp.edit().putInt("accent", v).apply()
        }

    /** Tezkor tarjima: qaysi tilga (til kodi). */
    var translateTo: String
        get() = sp.getString("trTo", "en") ?: "en"
        set(v) {
            sp.edit().putString("trTo", v).apply()
        }

    /** Tarjima uchun matn tashqi xizmatga yuborilishiga rozilik. */
    var translateConsent: Boolean
        get() = sp.getBoolean("trConsent", false)
        set(v) {
            sp.edit().putBoolean("trConsent", v).apply()
        }

    /** Klaviatura balandligi, foizda (80..130). */
    var heightPercent: Int
        get() = sp.getInt("height", 100).coerceIn(80, 130)
        set(v) {
            sp.edit().putInt("height", v).apply()
        }

    /** Doimiy raqamlar qatori (Samsung klaviaturasi kabi). */
    var numberRow: Boolean
        get() = sp.getBoolean("numberRow", true)
        set(v) {
            sp.edit().putBoolean("numberRow", v).apply()
        }

    /** 0 - oddiy, 1 - chap qo'l, 2 - o'ng qo'l. */
    var oneHanded: Int
        get() = sp.getInt("oneHanded", 0).coerceIn(0, 2)
        set(v) {
            sp.edit().putInt("oneHanded", v).apply()
        }

    var suggestions: Boolean
        get() = sp.getBoolean("suggestions", true)
        set(v) {
            sp.edit().putBoolean("suggestions", v).apply()
        }

    var emojiRecents: List<String>
        get() = sp.getString("emojiRecents", null)?.split(" ")?.filter { it.isNotEmpty() } ?: emptyList()
        set(v) {
            sp.edit().putString("emojiRecents", v.joinToString(" ")).apply()
        }

    // ---- koʻrinish va xatti-harakatni sozlash ----

    /** Tugma burchaklari radiusi, dp (0..18). */
    var keyRadius: Int
        get() = sp.getInt("keyRadius", 9).coerceIn(0, 18)
        set(v) { sp.edit().putInt("keyRadius", v).apply() }

    /** Tugmalar orasidagi masofa (har tomondan), dp x2 (1..6). */
    var keyGap: Int
        get() = sp.getInt("keyGap", 3).coerceIn(1, 6)
        set(v) { sp.edit().putInt("keyGap", v).apply() }

    /** Harf oʻlchami, foizda (80..130). */
    var textScale: Int
        get() = sp.getInt("textScale", 100).coerceIn(80, 130)
        set(v) { sp.edit().putInt("textScale", v).apply() }

    /** Tugma ostidagi soya. */
    var keyShadow: Boolean
        get() = sp.getBoolean("keyShadow", true)
        set(v) { sp.edit().putBoolean("keyShadow", v).apply() }

    /** Pastdan qoʻshimcha boʻsh joy, dp (0..32) — katta telefonda bosh barmoq uchun qulay. */
    var bottomPad: Int
        get() = sp.getInt("bottomPad", 0).coerceIn(0, 32)
        set(v) { sp.edit().putInt("bottomPad", v).apply() }

    /** Tebranish kuchi, ms (0 — tizim standarti, 5..40). */
    var hapticMs: Int
        get() = sp.getInt("hapticMs", 0).coerceIn(0, 40)
        set(v) { sp.edit().putInt("hapticMs", v).apply() }

    /** Uzoq bosish kechikishi, ms (150..600). */
    var longPressMs: Int
        get() = sp.getInt("longPressMs", 380).coerceIn(150, 600)
        set(v) { sp.edit().putInt("longPressMs", v).apply() }

    /** Boʻsh joy ustida surib kursorni siljitish qadami, dp (8..32): kichik — sezgir. */
    var cursorStep: Int
        get() = sp.getInt("cursorStep", 16).coerceIn(8, 32)
        set(v) { sp.edit().putInt("cursorStep", v).apply() }

    /** Gap boshida avtomatik bosh harf. */
    var autoCap: Boolean
        get() = sp.getBoolean("autoCap", true)
        set(v) { sp.edit().putBoolean("autoCap", v).apply() }

    /** Asboblar qatorida koʻrinadigan tugmalar (bit: 0 emoji, 1 tarjima, 2 bufer, 3 ovoz, 4 sozlamalar, 5 «…», 6 skaner). */
    var toolsMask: Int
        get() = (sp.getInt("toolsMask", 63) and 63) or (if (sp.getBoolean("scanTool", true)) 64 else 0)
        set(v) {
            // 6-bit (skaner) alohida saqlanadi: eski saqlangan qiymatlar skanerni yashirib qoʻymasligi uchun.
            sp.edit().putInt("toolsMask", (v and 63) or 32).putBoolean("scanTool", (v and 64) != 0).apply()
        }

    /** Barcha koʻrinish/xatti-harakat sozlamalarini standartga qaytaradi (tillar va oʻrganilgan soʻzlarga tegmaydi). */
    fun resetStyle() {
        sp.edit()
            .remove("keyRadius").remove("keyGap").remove("textScale").remove("keyShadow").remove("bottomPad")
            .remove("scanTool").remove("hapticMs").remove("longPressMs").remove("cursorStep").remove("autoCap").remove("toolsMask")
            .remove("theme").remove("accent").remove("height").remove("soundVol")
            .apply()
    }

    fun registerListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        val DEFAULT_ENABLED = listOf("uz-latn-2026", "uz-latn-1995", "uz-cyrl", "ru", "en")
    }
}
