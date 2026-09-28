package fmanager.model

import java.io.File

object FileOps {
    data class Stats(val size: Long, val files: Int, val folders: Int)

    fun uniqueTarget(dir: File, name: String): File {
        var t = File(dir, name)
        if (!t.exists()) return t
        val base = t.nameWithoutExtension
        val ext = t.extension.let { if (it.isEmpty()) "" else ".$it" }
        var i = 1
        while (true) {
            t = File(dir, "$base ($i)$ext")
            if (!t.exists()) return t
            i++
        }
    }

    private fun isInside(src: File, dest: File) =
        src.isDirectory && dest.canonicalPath.startsWith(src.canonicalPath)

    fun copy(src: File, destDir: File): Boolean = runCatching {
        if (isInside(src, destDir)) return false
        src.copyRecursively(uniqueTarget(destDir, src.name))
    }.getOrDefault(false)

    fun move(src: File, destDir: File): Boolean = runCatching {
        if (src.parentFile?.canonicalPath == destDir.canonicalPath) return false
        if (isInside(src, destDir)) return false
        val target = uniqueTarget(destDir, src.name)
        if (src.renameTo(target)) return true
        src.copyRecursively(target) && src.deleteRecursively()
    }.getOrDefault(false)

    fun stats(f: File): Stats {
        if (f.isFile) return Stats(f.length(), 1, 0)
        var size = 0L; var files = 0; var folders = 0
        f.walkTopDown().forEach {
            if (it == f) return@forEach
            if (it.isDirectory) folders++ else { files++; size += it.length() }
        }
        return Stats(size, files, folders)
    }
}
