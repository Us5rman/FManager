package fmanager.model

import java.io.File

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val isArchive: Boolean = false
) {
    companion object {
        fun fromFile(file: File): FileItem {
            val archiveExtensions = setOf("zip", "7z", "rar", "tar", "gz", "xz", "iso", "cab")
            val ext = file.extension.lowercase()
            
            return FileItem(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isDirectory) 0L else file.length(),
                lastModified = file.lastModified(),
                isArchive = !file.isDirectory && archiveExtensions.contains(ext)
            )
        }
    }
}
