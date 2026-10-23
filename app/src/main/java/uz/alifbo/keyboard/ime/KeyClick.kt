package uz.alifbo.keyboard.ime

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Tugma bosilganda "klik" tovushi. Tizimning "tugma tovushi" sozlamasiga va "Tizim" ovozi balandligiga
 * bogʻliq emas: qisqa tovush oʻzimiz yaratamiz va media ovozida chalamiz.
 */
class KeyClick {
    companion object {
        const val STANDARD = 0
        const val DELETE = 1
        const val SPACE = 2
        private const val RATE = 44100
    }

    private val tracks = arrayOfNulls<AudioTrack>(3)
    private var volume = 0.4f

    fun setVolume(percent: Int) {
        volume = (percent.coerceIn(0, 100) / 100f)
        for (t in tracks) t?.setVolume(volume)
    }

    fun play(kind: Int) {
        try {
            val old = tracks[kind]
            val t = old ?: build(kind).also { tracks[kind] = it }
            if (old != null) {
                // Qayta chalish: toʻxtatib, statik maʼlumotni boshiga qaytaramiz.
                t.stop()
                t.reloadStaticData()
            }
            t.setVolume(volume)
            t.play()
        } catch (e: Exception) {
            // tovush chalib boʻlmasa — jim oʻtamiz
        }
    }

    fun release() {
        for (i in tracks.indices) {
            try {
                tracks[i]?.release()
            } catch (e: Exception) {
                // e'tiborsiz
            }
            tracks[i] = null
        }
    }

    private fun build(kind: Int): AudioTrack {
        val (freq, ms, tau) = when (kind) {
            DELETE -> Triple(1250.0, 28, 0.006)
            SPACE -> Triple(950.0, 32, 0.008)
            else -> Triple(1750.0, 22, 0.004)
        }
        val n = RATE * ms / 1000
        val pcm = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val env = exp(-t / tau)
            val tone = sin(2 * PI * freq * t) + 0.35 * sin(2 * PI * freq * 2.1 * t)
            // boshida kichik shovqin — "tik" ttuyg'usi
            val click = if (i < 40) (((i * 7919) % 200) - 100) / 100.0 * (1 - i / 40.0) * 0.6 else 0.0
            pcm[i] = ((tone * env + click) * 0.55 * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(fmt)
            .setBufferSizeInBytes(n * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, n)
        track.setVolume(volume)
        return track
    }
}
