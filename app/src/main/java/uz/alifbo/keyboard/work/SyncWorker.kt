package uz.alifbo.keyboard.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import uz.alifbo.keyboard.data.RemoteSync

class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val r = RemoteSync.syncBlocking(applicationContext)
        // Tarmoq xatosida 3 martagacha qayta urinadi, undan keyin keyingi davrni kutadi.
        return if (r.ok || runAttemptCount >= 2) Result.success() else Result.retry()
    }
}
