package uz.alifbo.keyboard.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import android.app.Notification
import uz.alifbo.keyboard.data.Prefs
import uz.alifbo.keyboard.ui.SettingsActivity
import uz.alifbo.keyboard.update.Updater
import java.util.concurrent.TimeUnit

/** Kuniga ikki marta GitHub'da yangi versiya borligini tekshiradi va bildirishnoma chiqaradi. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.autoUpdateCheck) return Result.success()
        try {
            val rel = Updater.checkNewer(applicationContext)
            if (rel != null) {
                prefs.updateVersion = rel.version
                if (prefs.notifiedVersion != rel.version) {
                    notifyNew(applicationContext, rel.version)
                    prefs.notifiedVersion = rel.version
                }
            } else {
                prefs.updateVersion = ""
            }
        } catch (e: Exception) {
            return if (runAttemptCount >= 2) Result.success() else Result.retry()
        }
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "updates"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx.applicationContext)
                .enqueueUniquePeriodicWork("alifbo-update-check", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        private fun notifyNew(ctx: Context, version: String) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Yangilanishlar", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val b = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(ctx, CHANNEL)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(ctx)
            }
            b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Alifbo: yangi versiya $version")
                .setContentText("Yangilash uchun bosing")
                .setContentIntent(open)
                .setAutoCancel(true)
            try {
                nm.notify(1001, b.build())
            } catch (e: SecurityException) {
                // bildirishnomaga ruxsat berilmagan
            }
        }
    }
}
