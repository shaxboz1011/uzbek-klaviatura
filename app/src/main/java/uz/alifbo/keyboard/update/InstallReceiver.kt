package uz.alifbo.keyboard.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/** PackageInstaller natijasi: tasdiqlash oynasini ochadi yoki xatoni koʻrsatadi. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        ctx.startActivity(confirm)
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Oʻrnatish oynasini ochib boʻlmadi", Toast.LENGTH_LONG).show()
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // Ilova yangilanadi va qayta ishga tushadi.
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Nomaʼlum xato"
                Toast.makeText(ctx, "Yangilanmadi: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
