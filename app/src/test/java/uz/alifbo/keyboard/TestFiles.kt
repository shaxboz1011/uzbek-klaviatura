package uz.alifbo.keyboard

import java.io.File

/** Testlar uchun repo fayllarini topish (Gradle testlarni app/ papkasida ishga tushiradi). */
object TestFiles {
    fun repoFile(path: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, path)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        throw IllegalStateException("Fayl topilmadi: $path (ish papkasi: ${File("").absolutePath})")
    }
}
