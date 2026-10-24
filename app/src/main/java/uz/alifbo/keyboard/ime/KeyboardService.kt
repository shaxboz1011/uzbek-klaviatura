package uz.alifbo.keyboard.ime

import android.content.ClipData
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
import uz.alifbo.keyboard.data.Autocorrect
import uz.alifbo.keyboard.data.ClipStore
import uz.alifbo.keyboard.data.LayoutPack
import uz.alifbo.keyboard.data.LexiconStore
import uz.alifbo.keyboard.data.PackStore
import uz.alifbo.keyboard.data.Predictor
import uz.alifbo.keyboard.data.Prefs
import uz.alifbo.keyboard.data.RemoteSync
import uz.alifbo.keyboard.ui.ScanActivity
import uz.alifbo.keyboard.ui.SettingsActivity
import uz.alifbo.keyboard.ui.VoiceActivity
import uz.alifbo.keyboard.ui.VoiceBridge

class KeyboardService : InputMethodService(),
    KeyboardView.Listener, StripView.Listener, EmojiPanel.Listener, ClipPanel.Listener,
    MorePanel.Listener, TranslatePanel.Listener, ConvertPanel.Listener {

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
    private class Fix(val original: String, val fixed: String, val sep: String)
    private var lastFix: Fix? = null

    // Sozlamalar oʻzgarsa, klaviatura yopilmasdan darrov qoʻllanadi (Sozlamalar ichida sinab koʻrish uchun).
    private val liveKeys = setOf(
        "keyRadius", "keyGap", "textScale", "keyShadow", "bottomPad", "hapticMs", "longPressMs", "cursorStep",
        "autoCap", "toolsMask", "theme", "accent", "height", "soundVol", "sound", "haptic", "keyPreview",
        "numberRow", "swipe", "oneHanded"
    )
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in liveKeys) applyLive()
    }

    /** Barcha sozlamalarni koʻrinishga qoʻllaydi (mavzu/urgʻu oʻzgargan boʻlsa, koʻrinishni qayta quradi). */
    private fun applyLive() {
        if (root != null && builtTheme != themeSig()) setInputView(onCreateInputView())
        val r = root ?: return
        r.reloadColors()
        r.strip.setToolsMask(prefs.toolsMask)
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

    // Serverdan yangi alifbo kelganda klaviatura ochiq turgan bo'lsa ham darrov almashadi.
    private val storeListener: () -> Unit = { reloadPacks() }

    private var lastClipText: String? = null

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
            val t = clip.getItemAt(0).coerceToText(this).toString()
            if (force || t != lastClipText) ClipStore.add(this, t)
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
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        builtTheme = themeSig()
        val r = KeyboardRoot(Theme.themed(this, prefs.theme))
        r.keyboard.listener = this
        r.keyboard.learnedWords = { if (learnOn) Predictor.snapshot(this) else emptyMap() }
        r.strip.listener = this
        r.emoji.listener = this
        r.clip.listener = this
        r.more.listener = this
        r.translate.listener = this
        r.convert.listener = this
        root = r
        reloadPacks()
        return r
    }

    private fun themeSig() = "${prefs.theme}/${prefs.accent}"

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
        swipeWord = null
        r.strip.setSuggestions(emptyList())

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

    private fun applyPack(p: LayoutPack) {
        root?.keyboard?.setPack(p)
        LexiconStore.loadAsync(this, p) {}
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
        if (!suggestOn) {
            r.strip.setSuggestions(emptyList())
            return
        }
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(40, 0) ?: ""
        val cur = trailingWord(before)
        if (boundary && learnOn && prevWord.length >= 2) Predictor.learn(this, prevWord)
        prevWord = cur
        r.strip.setSuggestions(if (cur.isEmpty()) emptyList() else Predictor.suggest(this, cur, 3, currentLexicon()))
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
        prevWord = ""
        root?.strip?.setSuggestions(emptyList())
        updateShift()
    }

    // ---------------------------------------------------------------- asboblar qatori

    override fun onTool(id: Int) {
        val r = root ?: return
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
        val fixed = Autocorrect.correct(cur, lex) { it in known } ?: return false
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
        if (ic != null && autocorrect(ic, text)) {
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
                Predictor.learn(this, fx.original)
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
