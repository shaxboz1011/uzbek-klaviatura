package uz.alifbo.keyboard.ime

import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import uz.alifbo.keyboard.data.ClipStore
import uz.alifbo.keyboard.data.LayoutPack
import uz.alifbo.keyboard.data.PackStore
import uz.alifbo.keyboard.data.Predictor
import uz.alifbo.keyboard.data.Prefs
import uz.alifbo.keyboard.data.RemoteSync
import uz.alifbo.keyboard.ui.SettingsActivity
import uz.alifbo.keyboard.ui.VoiceActivity
import uz.alifbo.keyboard.ui.VoiceBridge

class KeyboardService : InputMethodService(),
    KeyboardView.Listener, StripView.Listener, EmojiPanel.Listener, ClipPanel.Listener {

    private var root: KeyboardRoot? = null
    private lateinit var prefs: Prefs
    private var packs: List<LayoutPack> = emptyList()
    private var loadedGen = -1
    private var loadedSig = ""

    private var suggestOn = true
    private var learnOn = true
    private var prevWord = ""

    // Serverdan yangi alifbo kelganda klaviatura ochiq turgan bo'lsa ham darrov almashadi.
    private val storeListener: () -> Unit = { reloadPacks() }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return@OnPrimaryClipChangedListener
            if (clip.itemCount == 0) return@OnPrimaryClipChangedListener
            // Parol menejerlari va boshqalar "maxfiy" deb belgilagan nusxalar saqlanmaydi.
            if (clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) {
                return@OnPrimaryClipChangedListener
            }
            val t = clip.getItemAt(0).coerceToText(this).toString()
            ClipStore.add(this, t)
        } catch (e: Exception) {
            // buferni o'qib bo'lmasa — jim o'tamiz
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        PackStore.addListener(storeListener)
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        PackStore.removeListener(storeListener)
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(clipListener)
        Predictor.save(this)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val r = KeyboardRoot(this)
        r.keyboard.listener = this
        r.strip.listener = this
        r.emoji.listener = this
        r.clip.listener = this
        root = r
        reloadPacks()
        return r
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        root?.reloadColors()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val sig = prefs.enabledIds.joinToString(",")
        if (loadedGen != PackStore.generation || loadedSig != sig) reloadPacks()

        val r = root ?: return
        r.showPanel(KeyboardRoot.KEYBOARD)
        val v = r.keyboard
        v.applyPrefs(prefs.haptic, prefs.heightPercent)
        v.setNumberRow(prefs.numberRow)
        v.setOneHanded(prefs.oneHanded)
        r.strip.setOneHandMode(prefs.oneHanded)

        val secret = isSecretField(info)
        suggestOn = prefs.suggestions && !secret
        learnOn = suggestOn && (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0
        prevWord = ""
        r.strip.setSuggestions(emptyList())

        when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME -> v.setSymbols(true)
            else -> v.setSymbols(false)
        }
        v.setEnterLabel(enterLabelFor(info))
        updateShift()

        // Ovozli kiritishdan qaytgan matn
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
        root?.keyboard?.setPack(p)
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
        r.strip.setSuggestions(if (cur.isEmpty()) emptyList() else Predictor.suggest(this, cur, 3))
    }

    override fun onSuggestion(text: String) {
        val ic = currentInputConnection ?: return
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
            StripView.CLIP -> {
                r.clip.show(ClipStore.list(this))
                r.showPanel(KeyboardRoot.CLIP)
            }
            StripView.VOICE -> startVoice()
            StripView.ONEHAND -> {
                val m = (prefs.oneHanded + 1) % 3
                prefs.oneHanded = m
                r.keyboard.setOneHanded(m)
                r.strip.setOneHandMode(m)
            }
            StripView.SETTINGS -> {
                startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            StripView.HIDE -> requestHideSelf(0)
        }
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
        root?.clip?.show(ClipStore.list(this))
    }

    override fun onClearClips() {
        ClipStore.clearUnpinned(this)
        root?.clip?.show(ClipStore.list(this))
    }

    // ---------------------------------------------------------------- KeyboardView.Listener

    override fun onText(text: String) {
        currentInputConnection?.commitText(text, 1)
        val last = text.lastOrNull()
        val boundary = last != null && !(last.isLetter() || last == 'ʻ' || last == 'ʼ' || last == '\'')
        refreshSuggestions(boundary)
        updateShift()
    }

    override fun onDelete() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        refreshSuggestions(false)
        updateShift()
    }

    override fun onEnter() {
        if (!sendDefaultEditorAction(true)) sendKeyChar('\n')
        refreshSuggestions(true)
        updateShift()
    }

    override fun onCursor(dir: Int) {
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
        root?.keyboard?.setPack(p)
        updateShift()
    }

    override fun onLangPicker() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }
}
