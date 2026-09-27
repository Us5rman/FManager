package fmanager.ui

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fmanager.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class FileManagerViewModel : ViewModel() {

    private val rootPath: String = Environment.getExternalStorageDirectory().absolutePath

    private val _currentPath = MutableStateFlow(rootPath)
    val currentPath: StateFlow<String> = _currentPath

    private val _fileList = MutableStateFlow<List<FileItem>>(emptyList())
    val fileList: StateFlow<List<FileItem>> = _fileList

    init {
        loadDirectory(rootPath)
    }

    fun loadDirectory(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val folder = File(path)
            if (folder.exists() && folder.isDirectory) {
                val files = folder.listFiles()
                    ?.map { FileItem.fromFile(it) }
                    ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    ?: emptyList()

                _currentPath.value = path
                _fileList.value = files
            }
        }
    }

    fun navigateUp(): Boolean {
        val current = File(_currentPath.value)
        val parent = current.parentFile
        
        return if (parent != null && current.absolutePath != rootPath) {
            loadDirectory(parent.absolutePath)
            true
        } else {
            false
        }
    }
}
