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

    /** Tugma bosilganda tovush. */
    var sound: Boolean
        get() = sp.getBoolean("sound", false)
        set(v) {
            sp.edit().putBoolean("sound", v).apply()
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

    companion object {
        val DEFAULT_ENABLED = listOf("uz-latn-2026", "uz-latn-1995", "uz-cyrl", "ru", "en")
    }
}
