package fmanager.ui

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fmanager.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileManagerViewModel : ViewModel() {
    val rootPath: String = Environment.getExternalStorageDirectory().absolutePath

    private val _currentPath = MutableStateFlow(rootPath)
    val currentPath: StateFlow<String> = _currentPath

    private val _fileList = MutableStateFlow<List<FileItem>>(emptyList())
    val fileList: StateFlow<List<FileItem>> = _fileList

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

    fun navigateUp() {
        if (_currentPath.value == rootPath) return
        File(_currentPath.value).parent?.let { loadDirectory(it) }
    }
}
