package uz.alifbo.keyboard

import android.app.Application
import uz.alifbo.keyboard.data.CrashLog
import uz.alifbo.keyboard.data.PackStore
import uz.alifbo.keyboard.data.RemoteSync
import uz.alifbo.keyboard.work.UpdateWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        // Paketlarni fonda oldindan yuklab qo'yamiz: klaviatura ochilganda kutish bo'lmaydi.
        Thread { PackStore.all(this) }.start()
        // Har 6 soatda alifbo yangilanishlarini tekshiradi (faqat internet bor paytda).
        RemoteSync.schedulePeriodic(this)
        // Har 12 soatda GitHub Releases'da yangi versiya bor-yoʻqligini tekshiradi.
        UpdateWorker.schedule(this)
    }
}
