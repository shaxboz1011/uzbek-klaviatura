package uz.alifbo.keyboard.data

import android.content.Context
import java.io.File

/** [BigramModel] ning fayldagi nusxasi (bigrams.txt, shu ilovaning shaxsiy xotirasida). */
object Bigrams {
    private val model = BigramModel()
    private var loaded = false
    private var dirty = 0

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "bigrams.txt")

    @Synchronized
    private fun ensure(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = file(ctx)
            if (f.exists()) model.load(f.readText())
        } catch (e: Exception) {
            model.clear()
        }
    }

    @Synchronized
    fun learn(ctx: Context, prev: String, next: String) {
        ensure(ctx)
        model.learn(prev, next)
        if (++dirty >= 25) save(ctx)
    }

    @Synchronized
    fun next(ctx: Context, prev: String, n: Int): List<String> {
        ensure(ctx)
        return model.next(prev, n)
    }

    @Synchronized
    fun save(ctx: Context) {
        if (!loaded || dirty == 0) return
        dirty = 0
        try {
            file(ctx).writeText(model.serialize())
        } catch (e: Exception) {
            // saqlanmasa — keyingi safar urinamiz
        }
    }

    @Synchronized
    fun clear(ctx: Context) {
        model.clear()
        loaded = true
        dirty = 0
        file(ctx).delete()
    }
}
