package uz.alifbo.keyboard.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

/** Ovozli kiritish: tizimning nutq aniqlash oynasini ochadi va natijani klaviaturaga qaytaradi. */
object VoiceBridge {
    @Volatile
    var pending: String? = null
}

class VoiceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tag = intent.getStringExtra("lang") ?: "uz-UZ"
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
        try {
            startActivityForResult(i, 1)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Ovozli kiritish xizmati topilmadi (Google ilovasi kerak)", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            val t = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (t != null) VoiceBridge.pending = t
        }
        finish()
    }
}
