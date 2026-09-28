package fmanager.ui

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fmanager.model.ArchiveOps
import fmanager.model.FileItem
import fmanager.model.FileOps
import fmanager.model.OpenKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

enum class ClipMode { COPY, MOVE }
data class Clip(val path: String, val mode: ClipMode)

class FileManagerViewModel : ViewModel() {
    val rootPath: String = Environment.getExternalStorageDirectory().absolutePath

    private val _currentPath = MutableStateFlow(rootPath)
    val currentPath: StateFlow<String> = _currentPath

    private val _fileList = MutableStateFlow<List<FileItem>>(emptyList())
    val fileList: StateFlow<List<FileItem>> = _fileList

    private val _clip = MutableStateFlow<Clip?>(null)
    val clip: StateFlow<Clip?> = _clip

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun messageShown() { _message.value = null }

    private val _progress = MutableStateFlow<OpProgress?>(null)
    val progress: StateFlow<OpProgress?> = _progress
    private var opJob: Job? = null

    private val _editing = MutableStateFlow<FileItem?>(null)
    val editing: StateFlow<FileItem?> = _editing
    fun openEditor(item: FileItem) { _editing.value = item }
    fun closeEditor() { _editing.value = null; refresh() }

    private val _viewer = MutableStateFlow<ViewerTarget?>(null)
    val viewer: StateFlow<ViewerTarget?> = _viewer
    fun openViewer(item: FileItem, kind: OpenKind) { _viewer.value = ViewerTarget(item, kind) }
    fun closeViewer() { _viewer.value = null }

    fun loadDirectory(path: String) {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                File(path).listFiles()
                    ?.map { FileItem.fromFile(it) }
                    ?.sortedWith(
                        compareByDescending<FileItem> { it.isDirectory }
                            .thenBy { it.name.lowercase() }
                    ) ?: emptyList()
            }
            _currentPath.value = path
            _fileList.value = items
        }
    }

    fun refresh() = loadDirectory(_currentPath.value)

    fun navigateUp() {
        if (_currentPath.value == rootPath) return
        File(_currentPath.value).parent?.let { loadDirectory(it) }
    }

    fun setClip(item: FileItem, mode: ClipMode) {
        _clip.value = Clip(item.path, mode)
        _message.value = if (mode == ClipMode.COPY) "Copied. Open a folder and tap Paste" else "Open a folder and tap Move here"
    }

    fun paste() {
        val c = _clip.value ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val src = File(c.path)
                val dest = File(_currentPath.value)
                if (c.mode == ClipMode.COPY) FileOps.copy(src, dest) else FileOps.move(src, dest)
            }
            _message.value = if (ok) "Done" else "Failed"
            if (ok && c.mode == ClipMode.MOVE) _clip.value = null
            refresh()
        }
    }

    fun cancelClip() { _clip.value = null }

    fun rename(item: FileItem, newName: String) {
        val name = newName.trim()
        if (name.isEmpty() || name.contains('/')) {
            _message.value = "Invalid name"
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val src = File(item.path)
                val target = File(src.parentFile, name)
                !target.exists() && src.renameTo(target)
            }
            _message.value = if (ok) "Renamed" else "Rename failed"
            refresh()
        }
    }

    fun createFolder(name: String) {
        val n = name.trim()
        if (n.isEmpty() || n.contains('/')) {
            _message.value = "Invalid name"
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val dir = File(_currentPath.value, n)
                !dir.exists() && dir.mkdirs()
            }
            _message.value = if (ok) "Folder created" else "Could not create folder"
            refresh()
        }
    }

    /**
     * Runs a long operation with a progress indicator. [block] gets a progress
     * reporter (done, total) and a cancel check that throws when cancelled.
     * It returns a success message, or throws to report failure.
     */
    private fun runOperation(
        label: String,
        failMsg: String,
        block: suspend (report: (Long, Long) -> Unit, check: () -> Unit) -> String
    ) {
        if (opJob?.isActive == true) {
            _message.value = "Another operation is running"
            return
        }
        opJob = viewModelScope.launch {
            _progress.value = OpProgress(label, -1f)
            try {
                val msg = withContext(Dispatchers.IO) {
                    val ctx = coroutineContext
                    block(
                        { done, total ->
                            val f = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
                            _progress.value = OpProgress(label, f)
                        },
                        { ctx.ensureActive() }
                    )
                }
                _message.value = msg
            } catch (e: CancellationException) {
                _message.value = "Cancelled"
                throw e
            } catch (e: Exception) {
                _message.value = e.message?.takeIf { it.isNotBlank() } ?: failMsg
            } finally {
                _progress.value = null
                refresh()
            }
        }
    }

    fun cancelOperation() { opJob?.cancel() }

    fun compress(item: FileItem, format: ArchiveOps.Format, level: ArchiveOps.Level, name: String) {
        val n = name.trim()
        if (n.isEmpty() || n.contains('/')) {
            _message.value = "Invalid name"
            return
        }
        runOperation("Compressing", "Compression failed") { report, check ->
            val src = File(item.path)
            val dir = src.parentFile ?: File(_currentPath.value)
            val out = FileOps.uniqueTarget(dir, "$n.${ArchiveOps.extensionFor(format, level)}")
            try {
                ArchiveOps.compress(listOf(src), out, format, level, report, check)
            } catch (e: Throwable) {
                out.delete()
                throw e
            }
            "Created ${out.name}"
        }
    }

    fun extract(item: FileItem, toFolder: Boolean) {
        runOperation("Extracting", "Extraction failed") { report, check ->
            val archive = File(item.path)
            val parent = archive.parentFile ?: File(_currentPath.value)
            val dest = if (toFolder) {
                FileOps.uniqueTarget(parent, ArchiveOps.baseName(archive.name))
            } else {
                parent
            }
            val count = ArchiveOps.extract(archive, dest, report, check)
            "Extracted $count file${if (count == 1) "" else "s"}"
        }
    }

    fun repair(item: FileItem) {
        runOperation("Repairing", "Repair failed") { report, check ->
            val archive = File(item.path)
            val dir = archive.parentFile ?: File(_currentPath.value)
            val out = FileOps.uniqueTarget(dir, "${ArchiveOps.baseName(archive.name)}_repaired.zip")
            val r = try {
                ArchiveOps.repair(archive, out, report, check)
            } catch (e: Throwable) {
                out.delete()
                throw e
            }
            if (r.complete) "Repaired: ${r.recovered} files saved to ${out.name}"
            else "Partly recovered: ${r.recovered} files saved to ${out.name}"
        }
    }

    suspend fun stats(item: FileItem): FileOps.Stats =
        withContext(Dispatchers.IO) { FileOps.stats(File(item.path)) }

    // Returns null if the file is too big (>1 MB) or looks binary
    suspend fun readText(item: FileItem): String? = withContext(Dispatchers.IO) {
        runCatching {
            val f = File(item.path)
            if (f.length() > 1_048_576L) return@runCatching null
            val bytes = f.readBytes()
            if (bytes.any { it == 0.toByte() }) null else String(bytes, Charsets.UTF_8)
        }.getOrNull()
    }

    suspend fun writeText(item: FileItem, text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { File(item.path).writeText(text); true }.getOrDefault(false)
    }
}
