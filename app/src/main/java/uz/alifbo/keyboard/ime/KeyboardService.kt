package uz.alifbo.keyboard.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import uz.alifbo.keyboard.data.Recents
import uz.alifbo.keyboard.data.Autocorrect
import uz.alifbo.keyboard.data.Bigrams
import uz.alifbo.keyboard.data.ClipStore
import uz.alifbo.keyboard.data.LayoutPack
import uz.alifbo.keyboard.data.LexiconStore
import uz.alifbo.keyboard.data.PackStore
import uz.alifbo.keyboard.data.Predictor
import uz.alifbo.keyboard.data.Prefs
import uz.alifbo.keyboard.data.RemoteSync
import uz.alifbo.keyboard.data.ShortcutLogic
import uz.alifbo.keyboard.data.Shortcuts
import uz.alifbo.keyboard.ui.ScanActivity
import uz.alifbo.keyboard.ui.SettingsActivity
import uz.alifbo.keyboard.ui.VoiceActivity
import uz.alifbo.keyboard.ui.VoiceBridge

class KeyboardService : InputMethodService(),
    KeyboardView.Listener, StripView.Listener, EmojiPanel.Listener, ClipPanel.Listener,
    MorePanel.Listener, TranslatePanel.Listener, ConvertPanel.Listener, EditPanel.Listener {

    private var root: KeyboardRoot? = null
    private lateinit var prefs: Prefs
    private var packs: List<LayoutPack> = emptyList()
    private var loadedGen = -1
    private var loadedSig = ""
    private var builtTheme = ""
    private var secretField = false
    private var swipeWord: String? = null
    private var swipeAllowed = true

    /** Oxirgi avto-tuzatish: orqaga (⌫) bosilsa asl soʻz tiklanadi. */
    private class Fix(val original: String, val fixed: String, val sep: String, val relearn: Boolean = true)
    private var lastFix: Fix? = null

    // Sozlamalar oʻzgarsa, klaviatura yopilmasdan darrov qoʻllanadi (Sozlamalar ichida sinab koʻrish uchun).
    private val liveKeys = setOf(
        "keyRadius", "keyGap", "textScale", "keyShadow", "bottomPad", "hapticMs", "longPressMs", "cursorStep",
        "autoCap", "toolsMask", "theme", "accent", "height", "soundVol", "sound", "haptic", "keyPreview",
        "numberRow", "swipe", "oneHanded", "stylePreset", "mixedLang"
    )
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in liveKeys) applyLive()
    }

    /** Barcha sozlamalarni koʻrinishga qoʻllaydi (mavzu/urgʻu oʻzgargan boʻlsa, koʻrinishni qayta quradi). */
    private fun applyLive() {
        if (root != null && builtTheme != themeSig()) setInputView(onCreateInputView())
        val r = root ?: return
        if (prefs.mixedLang) (packs.firstOrNull { it.id == prefs.currentId } ?: packs.firstOrNull())?.let { LexiconStore.loadExtrasAsync(this, it) {} }
        r.reloadColors()
        r.strip.setToolsMask(prefs.toolsMask)
        r.strip.setBadges(StripView.NEW_MASK and prefs.seenTools.inv())
        val v = r.keyboard
        v.applyPrefs(prefs.haptic, prefs.heightPercent, prefs.keyPreview, prefs.sound)
        v.setSoundVolume(prefs.soundVolume)
        v.applyStyle(prefs)
        v.setNumberRow(prefs.numberRow)
        v.setOneHanded(prefs.oneHanded)
        v.setSwipeEnabled(prefs.swipe && swipeAllowed)
    }

    private var suggestOn = true
    private var learnOn = true
    private var prevWord = ""
    /** Oxirgi tugallangan soʻz (keyingi soʻz bashorati uchun); jumla tugasa yoki maydon almashsa boʻshaydi. */
    private var lastWord = ""

    // Serverdan yangi alifbo kelganda klaviatura ochiq turgan bo'lsa ham darrov almashadi.
    private val storeListener: () -> Unit = { reloadPacks() }

    private var lastClipText: String? = null
    private var lastClipAtMs = 0L

    // Boʻsh maydonda "eng oxirgi" tavsiyalar (nusxalangan matn, skrinshot)
    private var chipActions: List<() -> Unit> = emptyList()
    private var chipClipText: String? = null
    private var chipShot: Recents.Shot? = null
    private var dismissedClip: String? = null
    private var dismissedShot: android.net.Uri? = null
    private var chipGen = 0
    private val bgExec = java.util.concurrent.Executors.newSingleThreadExecutor()

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip(true) }

    /**
     * Joriy nusxani bufer tarixiga qoʻshadi. Listener ishlamay qolgan (jarayon oʻldirilgan, nusxa klaviatura
     * yopiq paytda olingan) holatlar uchun klaviatura ochilganda va bufer paneli ochilganda ham chaqiriladi.
     * @return false — tizim buferga ruxsat bermadi.
     */
    private fun captureClip(force: Boolean): Boolean {
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return true
            if (clip.itemCount == 0) return true
            // Parol menejerlari va boshqalar "maxfiy" deb belgilagan nusxalar saqlanmaydi.
            if (clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return true
            val item = clip.getItemAt(0)
            // Faqat rasm/fayl (matnsiz) nusxalari — masalan, skrinshot — matn tarixiga tushmaydi.
            if (item.text == null && item.uri != null) return true
            val t = item.coerceToText(this).toString()
            if (force || t != lastClipText) {
                ClipStore.add(this, t)
                lastClipAtMs = if (android.os.Build.VERSION.SDK_INT >= 26) clip.description.timestamp else System.currentTimeMillis()
            }
            lastClipText = t
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        prefs.registerListener(prefListener)
        PackStore.addListener(storeListener)
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        prefs.unregisterListener(prefListener)
        PackStore.removeListener(storeListener)
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(clipListener)
        Predictor.save(this)
        Bigrams.save(this)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        builtTheme = themeSig()
        val r = KeyboardRoot(Theme.themed(this, prefs.theme))
        r.keyboard.listener = this
        r.keyboard.learnedWords = { if (learnOn) Predictor.snapshot(this) else emptyMap() }
        r.strip.listener = this
        r.edit.listener = this
        r.emoji.listener = this
        r.clip.listener = this
        r.more.listener = this
        r.translate.listener = this
        r.convert.listener = this
        root = r
        reloadPacks()
        return r
    }

    private fun themeSig() = "${prefs.theme}/${prefs.accent}/${prefs.stylePreset}"

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        root?.reloadColors()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val sig = prefs.enabledIds.joinToString(",")
        if (loadedGen != PackStore.generation || loadedSig != sig) reloadPacks()

        swipeAllowed = !isSecretField(info)
        applyLive()
        val r = root ?: return
        r.showPanel(KeyboardRoot.KEYBOARD)
        captureClip(false)
        val v = r.keyboard

        val secret = isSecretField(info)
        secretField = secret
        suggestOn = prefs.suggestions && !secret
        learnOn = suggestOn && (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0
        prevWord = ""
        lastWord = ""
        swipeWord = null
        r.strip.setSuggestions(emptyList())
        refreshChips()

        when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME -> v.setSymbols(true)
            else -> v.setSymbols(false)
        }
        v.setEnterLabel(enterLabelFor(info))
        updateShift()

        // Ovozli kiritish / skanerdan qaytgan matn
        val voice = VoiceBridge.pending
        if (voice != null) {
            VoiceBridge.pending = null
            currentInputConnection?.commitText(voice, 1)
        }
        RemoteSync.maybeSync(applicationContext)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        Predictor.save(this)
        Bigrams.save(this)
        super.onFinishInputView(finishingInput)
    }

    private fun isSecretField(info: EditorInfo): Boolean {
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return when (cls) {
            InputType.TYPE_CLASS_TEXT ->
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                    variation == InputType.TYPE_TEXT_VARIATION_URI
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun reloadPacks() {
        val all = PackStore.all(this)
        val enabled = prefs.enabledIds
        var list = enabled.mapNotNull { id -> all.firstOrNull { it.id == id } }
        if (list.isEmpty()) list = all.take(1)
        packs = list
        loadedGen = PackStore.generation
        loadedSig = enabled.joinToString(",")
        val cur = prefs.currentId
        val p = list.firstOrNull { it.id == cur } ?: list.firstOrNull() ?: return
        applyPack(p)
    }

    /** Joriy alifbo uchun boshlangʻich lugʻat (yuklangan boʻlsa). */
    private fun currentLexicon() =
        (packs.firstOrNull { it.id == prefs.currentId } ?: packs.firstOrNull())?.let { LexiconStore.get(it) }

    /** Aralash tilda yozish yoqilgan boʻlsa, joriy alifbo bilan birga tavsiya beriladigan boshqa til lugʻatlari. */
    private fun currentExtras(): List<uz.alifbo.keyboard.data.Lexicon> {
        if (!prefs.mixedLang) return emptyList()
        val p = packs.firstOrNull { it.id == prefs.currentId } ?: packs.firstOrNull() ?: return emptyList()
        return LexiconStore.extras(p)
    }

    private fun applyPack(p: LayoutPack) {
        root?.keyboard?.setPack(p)
        LexiconStore.loadAsync(this, p) {}
        if (prefs.mixedLang) LexiconStore.loadExtrasAsync(this, p) {}
    }

    private fun enterLabelFor(info: EditorInfo): String? {
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return null
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "Oʻt"
            EditorInfo.IME_ACTION_SEARCH -> "Qidir"
            EditorInfo.IME_ACTION_SEND -> "Yubor"
            EditorInfo.IME_ACTION_NEXT -> "Keyingi"
            EditorInfo.IME_ACTION_DONE -> "OK"
            else -> null
        }
    }

    private fun updateShift() {
        val ei = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val caps = if (ei.inputType != InputType.TYPE_NULL) ic.getCursorCapsMode(ei.inputType) else 0
        root?.keyboard?.setAutoShift(caps != 0)
    }

    // ---------------------------------------------------------------- so'z tavsiyalari

    private fun trailingWord(s: CharSequence): String {
        var i = s.length
        while (i > 0) {
            val ch = s[i - 1]
            if (ch.isLetter() || ch == 'ʻ' || ch == 'ʼ' || ch == '\'') i-- else break
        }
        return s.substring(i).trimStart('ʻ', 'ʼ', '\'')
    }

    /** Matn o'zgargandan keyin tavsiyalarni yangilaydi; so'z tugagan bo'lsa uni o'rganadi. */
    private fun refreshSuggestions(boundary: Boolean) {
        val r = root ?: return
        clearChips()
        if (!suggestOn) {
            r.strip.setSuggestions(emptyList())
            return
        }
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(40, 0) ?: ""
        val cur = trailingWord(before)
        if (boundary && learnOn && prevWord.length >= 2) {
            Predictor.learn(this, prevWord)
            if (prefs.nextWord) {
                if (lastWord.isNotEmpty()) Bigrams.learn(this, lastWord, prevWord)
                lastWord = prevWord
            }
            // Jumla tugadi: keyingi jumla oldingi soʻzga bogʻlanmasin
            if (before.trimEnd().lastOrNull() in setOf('.', '!', '?')) lastWord = ""
        }
        prevWord = cur
        r.strip.setSuggestions(
            when {
                cur.isNotEmpty() -> {
                    val base = Predictor.suggest(this, cur, 3, currentLexicon(), currentExtras())
                    // Qisqartma yozilgan boʻlsa, uning matni birinchi tavsiya (qisqa boʻlsa; uzun matn boʻsh joy bosilganda chiqadi)
                    val sc = if (prefs.shortcuts) Shortcuts.find(this, cur) else null
                    val ex = sc?.let { ShortcutLogic.applyCase(cur, ShortcutLogic.expandVars(it.text, java.util.Calendar.getInstance())) }
                    if (ex != null && ex.length <= 22 && '\n' !in ex) (listOf(ex) + base.filter { it != ex }).take(3) else base
                }
                prefs.nextWord && lastWord.isNotEmpty() && before.lastOrNull()?.isWhitespace() == true ->
                    Bigrams.next(this, lastWord, 3)
                else -> emptyList()
            }
        )
    }

    // ---------------------------------------------------------------- "eng oxirgi" tavsiyalar

    private fun clearChips() {
        chipGen++
        chipActions = emptyList()
        root?.strip?.setChips(emptyList())
    }

    /** Maydon boʻsh boʻlsa: yaqinda nusxalangan matn va yaqinda olingan skrinshotni chip sifatida koʻrsatadi. */
    private fun refreshChips() {
        clearChips()
        val r = root ?: return
        if (secretField) return
        val ic = currentInputConnection ?: return
        val empty = ic.getTextBeforeCursor(1, 0).isNullOrEmpty() && ic.getTextAfterCursor(1, 0).isNullOrEmpty()
        if (!empty) return
        val gen = chipGen
        val now = System.currentTimeMillis()

        val clip = lastClipText
        chipClipText = if (prefs.recentClipChip && clip != null && clip.isNotBlank() && clip != dismissedClip &&
            now - lastClipAtMs <= Recents.FRESH_MS) clip else null

        chipShot = null
        showChips(null)
        if (!prefs.recentScreenshotChip) return
        bgExec.execute {
            val shot = Recents.latestScreenshot(this)
            if (shot == null || shot.uri == dismissedShot) return@execute
            val thumb = Recents.thumbnail(this, shot.uri, (160 * resources.displayMetrics.density).toInt())
            r.post {
                if (gen != chipGen) return@post
                chipShot = shot
                showChips(thumb)
            }
        }
    }

    private fun showChips(thumb: android.graphics.Bitmap?) {
        val chips = ArrayList<StripView.Chip>()
        val actions = ArrayList<() -> Unit>()
        val shot = chipShot
        if (shot != null) {
            chips.add(StripView.Chip("Skrinshot", thumb))
            actions.add { insertScreenshot(shot) }
        }
        val clip = chipClipText
        if (clip != null) {
            chips.add(StripView.Chip(clip))
            actions.add {
                currentInputConnection?.commitText(clip, 1)
                updateShift()
            }
        }
        chipActions = actions
        root?.strip?.setChips(chips)
    }

    override fun onToolKey(id: Int) = onTool(id)

    // ---------------------------------------------------------------- matn tahrirlash paneli

    override fun onEditKey(keyCode: Int, ctrl: Boolean, shift: Boolean) {
        val ic = currentInputConnection ?: return
        var meta = 0
        if (ctrl) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (shift) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        val t = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    override fun onEditAction(id: Int) {
        val ic = currentInputConnection ?: return
        when (id) {
            EditPanel.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            EditPanel.CUT -> ic.performContextMenuAction(android.R.id.cut)
            EditPanel.COPY -> ic.performContextMenuAction(android.R.id.copy)
            EditPanel.PASTE -> ic.performContextMenuAction(android.R.id.paste)
            EditPanel.UNDO -> onEditKey(KeyEvent.KEYCODE_Z, true, false)
            EditPanel.REDO -> onEditKey(KeyEvent.KEYCODE_Z, true, true)
        }
    }

    override fun onChip(index: Int) {
        val a = chipActions.getOrNull(index) ?: return
        dismissedClip = chipClipText ?: dismissedClip
        dismissedShot = chipShot?.uri ?: dismissedShot
        clearChips()
        a()
        refreshSuggestions(false)
    }

    override fun onChipsDismiss() {
        dismissedClip = chipClipText ?: dismissedClip
        dismissedShot = chipShot?.uri ?: dismissedShot
        clearChips()
    }

    /** Rasmni qabul qiladigan maydonga toʻgʻridan-toʻgʻri qoʻyadi, aks holda buferga nusxalaydi. */
    private fun insertScreenshot(shot: Recents.Shot) {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        val accepts = EditorInfoCompat.getContentMimeTypes(ei).any { ClipDescription.compareMimeTypes(shot.mime, it) }
        if (accepts) {
            val info = InputContentInfoCompat(shot.uri, ClipDescription("skrinshot", arrayOf(shot.mime)), null)
            val flags = if (android.os.Build.VERSION.SDK_INT >= 25) InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION else 0
            if (InputConnectionCompat.commitContent(ic, ei, info, flags, null)) return
        }
        try {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newUri(contentResolver, "skrinshot", shot.uri))
            Toast.makeText(this, "Skrinshot buferga nusxalandi — kerakli joyda «Qoʻyish»ni bosing", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Bu maydon rasmni qabul qilmaydi", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSuggestion(text: String) {
        val ic = currentInputConnection ?: return
        val sw = swipeWord
        if (sw != null) {
            swipeWord = null
            val tail = ic.getTextBeforeCursor(sw.length + 1, 0)?.toString()
            if (tail == "$sw ") {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(sw.length + 1, 0)
                ic.commitText("$text ", 1)
                ic.endBatchEdit()
                if (learnOn) Predictor.learn(this, text)
                root?.strip?.setSuggestions(emptyList())
                updateShift()
                return
            }
        }
        val before = ic.getTextBeforeCursor(40, 0) ?: ""
        val cur = trailingWord(before)
        val out = if (cur.isNotEmpty() && cur[0].isUpperCase()) text.replaceFirstChar { it.uppercase() } else text
        ic.beginBatchEdit()
        ic.deleteSurroundingText(cur.length, 0)
        ic.commitText("$out ", 1)
        ic.endBatchEdit()
        if (learnOn) Predictor.learn(this, out)
        rememberWord(out)
        prevWord = ""
        updateShift()
        refreshSuggestions(false) // keyingi soʻz tavsiyalarini darrov koʻrsatadi
    }

    /** Tavsiya yoki surib yozish bilan kiritilgan soʻzni keyingi soʻz bashorati uchun eslab qoladi. */
    private fun rememberWord(w: String) {
        if (!learnOn || !prefs.nextWord) return
        val k = w.lowercase()
        if (lastWord.isNotEmpty()) Bigrams.learn(this, lastWord, k)
        lastWord = k
    }

    // ---------------------------------------------------------------- asboblar qatori

    override fun onTool(id: Int) {
        val r = root ?: return
        // "Yangi" nuqtasi ochilgach yoʻqoladi
        if (StripView.NEW_MASK and (1 shl id) != 0 && prefs.seenTools and (1 shl id) == 0) {
            prefs.seenTools = prefs.seenTools or (1 shl id)
            r.strip.setBadges(StripView.NEW_MASK and prefs.seenTools.inv())
        }
        when (id) {
            StripView.EMOJI -> {
                r.emoji.show(prefs.emojiRecents)
                r.showPanel(KeyboardRoot.EMOJI)
            }
            StripView.TRANSLATE -> {
                r.showPanel(KeyboardRoot.TRANSLATE)
                r.translate.open(currentLang(), secretField)
            }
            StripView.CLIP -> {
                val access = captureClip(false)
                r.clip.show(ClipStore.list(this), access)
                r.showPanel(KeyboardRoot.CLIP)
            }
            StripView.EDIT -> {
                r.edit.reset()
                r.showPanel(KeyboardRoot.EDIT)
            }
            StripView.VOICE -> startVoice()
            StripView.SCAN -> startActivity(
                Intent(this, ScanActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("pack", prefs.currentId)
            )
            StripView.SETTINGS -> openSettings()
            StripView.MORE -> {
                r.more.show(prefs.oneHanded, prefs.theme, prefs.sound)
                r.showPanel(KeyboardRoot.MORE)
            }
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun currentLang(): String {
        val p = packs.firstOrNull { it.id == prefs.currentId } ?: packs.firstOrNull()
        return p?.locale?.language ?: "uz"
    }

    private fun startVoice() {
        val p = packs.firstOrNull { it.id == prefs.currentId } ?: packs.firstOrNull()
        val lang = p?.locale?.language?.takeIf { it.isNotEmpty() } ?: "uz"
        val tag = if (p != null && p.country.isNotEmpty()) "$lang-${p.country}" else lang
        startActivity(
            Intent(this, VoiceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("lang", tag)
        )
    }

    // ---------------------------------------------------------------- emoji / bufer paneli

    override fun onEmoji(e: String) {
        currentInputConnection?.commitText(e, 1)
        val rec = (listOf(e) + prefs.emojiRecents.filter { it != e }).take(32)
        prefs.emojiRecents = rec
    }

    override fun onBack() {
        root?.showPanel(KeyboardRoot.KEYBOARD)
    }

    override fun onPaste(text: String) {
        currentInputConnection?.commitText(text, 1)
        root?.showPanel(KeyboardRoot.KEYBOARD)
        refreshSuggestions(false)
        updateShift()
    }

    override fun onTogglePin(text: String) {
        ClipStore.togglePin(this, text)
        root?.clip?.show(ClipStore.list(this), true)
    }

    override fun onClearClips() {
        ClipStore.clearUnpinned(this)
        root?.clip?.show(ClipStore.list(this), true)
    }

    // ---------------------------------------------------------------- «…» menyusi

    override fun onMore(id: Int) {
        val r = root ?: return
        val ic = currentInputConnection
        when (id) {
            MorePanel.SELECT_ALL -> ic?.performContextMenuAction(android.R.id.selectAll)
            MorePanel.COPY -> ic?.performContextMenuAction(android.R.id.copy)
            MorePanel.CUT -> ic?.performContextMenuAction(android.R.id.cut)
            MorePanel.PASTE -> ic?.performContextMenuAction(android.R.id.paste)
            MorePanel.CURSOR_LEFT -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            MorePanel.CURSOR_RIGHT -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            MorePanel.CLEAR -> {
                if (ic != null) {
                    ic.beginBatchEdit()
                    ic.performContextMenuAction(android.R.id.selectAll)
                    ic.commitText("", 1)
                    ic.endBatchEdit()
                }
            }
            MorePanel.ONEHAND -> {
                val m = (prefs.oneHanded + 1) % 3
                prefs.oneHanded = m
                r.keyboard.setOneHanded(m)
                r.more.show(m, prefs.theme, prefs.sound)
                return
            }
            MorePanel.THEME -> {
                prefs.theme = (prefs.theme + 1) % 3 // sozlama oʻzgarishi koʻrinishni qayta quradi (applyLive)
                val nr = root ?: return
                nr.more.show(prefs.oneHanded, prefs.theme, prefs.sound)
                nr.showPanel(KeyboardRoot.MORE)
                return
            }
            MorePanel.SOUND -> {
                prefs.sound = !prefs.sound
                r.keyboard.applyPrefs(prefs.haptic, prefs.heightPercent, prefs.keyPreview, prefs.sound)
                r.keyboard.setSoundVolume(prefs.soundVolume)
                if (prefs.sound) r.keyboard.previewClick()
                r.more.show(prefs.oneHanded, prefs.theme, prefs.sound)
                return
            }
            MorePanel.CONVERT -> {
                r.showPanel(KeyboardRoot.CONVERT)
                r.convert.open()
                return
            }
            MorePanel.SETTINGS -> {
                openSettings()
                return
            }
            MorePanel.HIDE -> {
                r.showPanel(KeyboardRoot.KEYBOARD)
                requestHideSelf(0)
                return
            }
        }
        // Tahrirlash amallaridan keyin klaviaturaga qaytamiz.
        if (id == MorePanel.SELECT_ALL || id == MorePanel.COPY || id == MorePanel.CUT ||
            id == MorePanel.PASTE || id == MorePanel.CLEAR
        ) {
            r.showPanel(KeyboardRoot.KEYBOARD)
            refreshSuggestions(false)
            updateShift()
        }
    }

    // ---------------------------------------------------------------- alifbo oʻgiruvchi

    override fun onConvertBack() {
        root?.showPanel(KeyboardRoot.KEYBOARD)
    }

    override fun onConvertSource(): String? {
        val ic = currentInputConnection ?: return null
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) return sel.toString()
        val before = ic.getTextBeforeCursor(2000, 0) ?: ""
        val after = ic.getTextAfterCursor(2000, 0) ?: ""
        return before.toString() + after.toString()
    }

    override fun onConvertApply(target: Int) {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        ic.beginBatchEdit()
        if (!sel.isNullOrEmpty()) {
            ic.commitText(uz.alifbo.keyboard.data.AlphabetTools.convert(sel.toString(), target), 1)
        } else {
            val before = ic.getTextBeforeCursor(2000, 0) ?: ""
            val after = ic.getTextAfterCursor(2000, 0) ?: ""
            val all = before.toString() + after.toString()
            ic.deleteSurroundingText(before.length, after.length)
            ic.commitText(uz.alifbo.keyboard.data.AlphabetTools.convert(all, target), 1)
        }
        ic.endBatchEdit()
        root?.showPanel(KeyboardRoot.KEYBOARD)
        refreshSuggestions(false)
        updateShift()
    }

    // ---------------------------------------------------------------- tezkor tarjima

    override fun onTranslateBack() {
        root?.showPanel(KeyboardRoot.KEYBOARD)
    }

    override fun onTranslateSource(): TranslatePanel.Source? {
        val ic = currentInputConnection ?: return null
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) return TranslatePanel.Source(sel.toString(), true, 0, 0)
        val before = ic.getTextBeforeCursor(1500, 0) ?: ""
        val after = ic.getTextAfterCursor(1500, 0) ?: ""
        val all = before.toString() + after.toString()
        return TranslatePanel.Source(all, false, before.length, after.length)
    }

    override fun onTranslateReplace(result: String, src: TranslatePanel.Source) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (!src.selected) ic.deleteSurroundingText(src.before, src.after)
        ic.commitText(result, 1)
        ic.endBatchEdit()
        root?.showPanel(KeyboardRoot.KEYBOARD)
        refreshSuggestions(false)
        updateShift()
    }

    override fun onTranslateInsert(result: String) {
        currentInputConnection?.commitText(result, 1)
        root?.showPanel(KeyboardRoot.KEYBOARD)
        refreshSuggestions(false)
        updateShift()
    }

    override fun onTranslateCopy(result: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("tarjima", result))
        Toast.makeText(this, "Nusxalandi", Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- KeyboardView.Listener

    override fun onExitOneHanded() {
        prefs.oneHanded = 0
        root?.keyboard?.setOneHanded(0)
    }

    /** Qisqartma ("mnm") yozilib ajratuvchi bosilsa, toʻliq matnga almashtiradi; ⌫ — qisqartmani qaytaradi. true — matn yozildi. */
    private fun expandShortcut(ic: android.view.inputmethod.InputConnection, sep: String): Boolean {
        if (!prefs.shortcuts || secretField) return false
        if (sep.length != 1 || !(sep[0].isWhitespace() || sep[0] in ".,!?;:)")) return false
        val before = ic.getTextBeforeCursor(40, 0) ?: return false
        val cur = trailingWord(before)
        if (cur.isEmpty()) return false
        val sc = Shortcuts.find(this, cur) ?: return false
        val out = ShortcutLogic.applyCase(cur, ShortcutLogic.expandVars(sc.text, java.util.Calendar.getInstance()))
        lastFix = null
        ic.beginBatchEdit()
        ic.deleteSurroundingText(cur.length, 0)
        ic.commitText(out + sep, 1)
        ic.endBatchEdit()
        lastFix = Fix(cur, out, sep, relearn = false)
        return true
    }

    /** Ajratuvchi (boʻsh joy/tinish belgisi) yozilganda, tugagan soʻzni kerak boʻlsa tuzatadi. true — matn yozildi. */
    private fun autocorrect(ic: android.view.inputmethod.InputConnection, sep: String): Boolean {
        lastFix = null
        if (!prefs.autoCorrect || !suggestOn || secretField) return false
        if (sep.length != 1 || !(sep[0].isWhitespace() || sep[0] in ".,!?;:)")) return false
        val before = ic.getTextBeforeCursor(40, 0) ?: return false
        val cur = trailingWord(before)
        if (cur.isEmpty()) return false
        val lex = currentLexicon() ?: return false
        val known = Predictor.snapshot(this)
        val extras = currentExtras()
        val fixed = Autocorrect.correct(cur, lex) { it in known || extras.any { x -> x.contains(it) } } ?: return false
        val out = Autocorrect.matchCase(cur, fixed)
        ic.beginBatchEdit()
        ic.deleteSurroundingText(cur.length, 0)
        ic.commitText(out + sep, 1)
        ic.endBatchEdit()
        lastFix = Fix(cur, out, sep)
        return true
    }

    override fun onSwipe(words: List<String>) {
        val ic = currentInputConnection ?: return
        val best = words.firstOrNull() ?: return
        val b = ic.getTextBeforeCursor(1, 0)
        val lead = if (!b.isNullOrEmpty() && (b[0].isLetterOrDigit() || b[0] in ".,!?:;)")) " " else ""
        ic.commitText("$lead$best ", 1)
        if (learnOn) Predictor.learn(this, best.lowercase())
        rememberWord(best)
        updateShift()
        prevWord = ""
        swipeWord = best
        root?.strip?.setSuggestions(if (words.size > 1 && suggestOn) words else emptyList())
    }

    override fun onText(text: String) {
        swipeWord = null
        if (text != " " && text.length > 0 && text[0].isLetter()) lastFix = null
        val ic = currentInputConnection
        if (text == " " && prefs.doubleSpacePeriod && !secretField && ic != null) {
            // Ikki marta boʻsh joy: "soʻz " + " " -> "soʻz. "
            val b = ic.getTextBeforeCursor(2, 0)
            if (b != null && b.length == 2 && b[1] == ' ' && (b[0].isLetterOrDigit())) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                ic.endBatchEdit()
                refreshSuggestions(true)
                updateShift()
                return
            }
        }
        if (ic != null && (expandShortcut(ic, text) || autocorrect(ic, text))) {
            refreshSuggestions(true)
            updateShift()
            return
        }
        ic?.commitText(text, 1)
        val last = text.lastOrNull()
        val boundary = last != null && !(last.isLetter() || last == 'ʻ' || last == 'ʼ' || last == '\'')
        refreshSuggestions(boundary)
        updateShift()
    }

    override fun onDelete() {
        val fx = lastFix
        if (fx != null) {
            lastFix = null
            val ic = currentInputConnection
            val tail = ic?.getTextBeforeCursor(fx.fixed.length + fx.sep.length, 0)?.toString()
            if (ic != null && tail == fx.fixed + fx.sep) {
                // Tuzatishni bekor qilamiz: asl soʻzni qaytaramiz va uni oʻrganamiz (qayta tuzatilmasligi uchun).
                ic.beginBatchEdit()
                ic.deleteSurroundingText(fx.fixed.length + fx.sep.length, 0)
                ic.commitText(fx.original, 1)
                ic.endBatchEdit()
                if (fx.relearn) Predictor.learn(this, fx.original)
                refreshSuggestions(false)
                updateShift()
                return
            }
        }
        val sw = swipeWord
        if (sw != null) {
            swipeWord = null
            val ic = currentInputConnection
            if (ic != null && ic.getTextBeforeCursor(sw.length + 1, 0)?.toString() == "$sw ") {
                ic.deleteSurroundingText(sw.length + 1, 0)
                root?.strip?.setSuggestions(emptyList())
                updateShift()
                return
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        refreshSuggestions(false)
        updateShift()
    }

    override fun onEnter() {
        swipeWord = null
        if (!sendDefaultEditorAction(true)) sendKeyChar('\n')
        refreshSuggestions(true)
        updateShift()
    }

    override fun onCursor(dir: Int) {
        swipeWord = null
        sendDownUpKeyEvents(if (dir > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
        refreshSuggestions(false)
    }

    override fun onLangNext() {
        if (packs.size <= 1) {
            onLangPicker()
            return
        }
        val i = packs.indexOfFirst { it.id == prefs.currentId }.coerceAtLeast(0)
        val p = packs[(i + 1) % packs.size]
        prefs.currentId = p.id
        applyPack(p)
        updateShift()
    }

    override fun onLangPicker() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }
}
