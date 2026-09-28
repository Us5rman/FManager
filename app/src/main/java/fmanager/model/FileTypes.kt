package fmanager.model

enum class OpenKind(val title: String, val mime: String) {
    TEXT("Open / edit as text", "text/plain"),
    VIDEO("Open as video", "video/*"),
    AUDIO("Open as sound", "audio/*"),
    IMAGE("Open as image", "image/*"),
    ANY("Open with other app", "*/*")
}

object FileTypes {
    private val video = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "m4v", "mpg", "mpeg")
    private val audio = setOf("mp3", "wav", "flac", "ogg", "m4a", "aac", "opus", "wma", "amr", "mid")
    private val image = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg")
    private val text = setOf(
        "txt", "log", "md", "json", "xml", "csv", "ini", "conf", "cfg", "properties",
        "java", "kt", "kts", "gradle", "py", "js", "ts", "html", "htm", "css", "yml", "yaml",
        "toml", "sh", "bat", "c", "cpp", "h", "hpp", "cs", "smali", "sql", "lua", "php",
        "rs", "go", "rb", "swift", "pro", "gitignore", "env", "desktop", "reg", "cmd"
    )

    fun kindOf(item: FileItem): OpenKind {
        val ext = item.name.substringAfterLast('.', "").lowercase()
        return when {
            ext in video -> OpenKind.VIDEO
            ext in audio -> OpenKind.AUDIO
            ext in image -> OpenKind.IMAGE
            ext in text -> OpenKind.TEXT
            else -> OpenKind.ANY
        }
    }
}
