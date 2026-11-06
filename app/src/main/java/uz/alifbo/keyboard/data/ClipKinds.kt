package uz.alifbo.keyboard.data

/** Bufer yozuvlarini turiga ko'ra ajratish (Havola, Raqam, Matn). Android'ga bog'liq emas (testlanadi). */
object ClipKinds {
    const val ALL = 0
    const val LINK = 1
    const val NUMBER = 2
    const val PINNED = 3

    private val URL = Regex("^(https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun isLink(t: String): Boolean {
        val s = t.trim()
        return URL.matches(s) || EMAIL.matches(s)
    }

    /** Telefon, karta, hisob raqami kabi: asosan raqamlardan iborat (kamida 6 ta raqam; +, -, (), bo'sh joy ruxsat). */
    fun isNumber(t: String): Boolean {
        val s = t.trim()
        if (s.isEmpty()) return false
        val digits = s.count { it.isDigit() }
        return digits >= 6 && s.all { it.isDigit() || it in "+-() .," }
    }

    fun matches(kind: Int, item: ClipStore.Item): Boolean = when (kind) {
        LINK -> isLink(item.text)
        NUMBER -> isNumber(item.text)
        PINNED -> item.pinned
        else -> true
    }
}
