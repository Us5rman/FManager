package fmanager.ui

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fmanager.model.FileItem
import fmanager.model.FileOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    suspend fun stats(item: FileItem): FileOps.Stats =
        withContext(Dispatchers.IO) { FileOps.stats(File(item.path)) }
}
