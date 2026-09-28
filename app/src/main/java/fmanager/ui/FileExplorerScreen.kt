package fmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fmanager.model.FileItem
import fmanager.model.FileOps
import java.text.DateFormat
import java.util.Date

fun formatSize(b: Long): String {
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = -1
    do { v /= 1024; i++ } while (v >= 1024 && i < units.lastIndex)
    return "%.1f %s".format(v, units[i])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(viewModel: FileManagerViewModel) {
    val currentPath by viewModel.currentPath.collectAsState()
    val fileList by viewModel.fileList.collectAsState()
    val clip by viewModel.clip.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var propsTarget by remember { mutableStateOf<FileItem?>(null) }

    BackHandler(enabled = currentPath != viewModel.rootPath) {
        viewModel.navigateUp()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentPath.replace("/storage/emulated/0", "Internal Storage"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            clip?.let { c ->
                ExtendedFloatingActionButton(onClick = { viewModel.paste() }) {
                    Text(if (c.mode == ClipMode.COPY) "Paste here" else "Move here")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(
                fileList,
                key = { it.path },
                contentType = { if (it.isDirectory) 0 else 1 }
            ) { item ->
                FileRowItem(
                    item = item,
                    onClick = {
                        if (item.isDirectory) viewModel.loadDirectory(item.path)
                    },
                    onRename = { renameTarget = item },
                    onCopy = { viewModel.setClip(item, ClipMode.COPY) },
                    onMove = { viewModel.setClip(item, ClipMode.MOVE) },
                    onProps = { propsTarget = item }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }

    renameTarget?.let { t ->
        var text by remember(t) { mutableStateOf(t.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(t, text)
                    renameTarget = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    propsTarget?.let { t ->
        var stats by remember(t) { mutableStateOf<FileOps.Stats?>(null) }
        LaunchedEffect(t) { stats = viewModel.stats(t) }
        AlertDialog(
            onDismissRequest = { propsTarget = null },
            title = { Text("Properties") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Name: ${t.name}")
                    Text("Path: ${t.path}")
                    Text("Size: ${stats?.let { formatSize(it.size) } ?: "Calculating..."}")
                    if (t.isDirectory) {
                        stats?.let { Text("Contains: ${it.files} files, ${it.folders} folders") }
                    }
                    Text("Modified: ${DateFormat.getDateTimeInstance().format(Date(t.lastModified))}")
                }
            },
            confirmButton = {
                TextButton(onClick = { propsTarget = null }) { Text("Close") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRowItem(
    item: FileItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onProps: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, tint) = when {
                item.isDirectory -> Icons.Default.Folder to MaterialTheme.colorScheme.primary
                item.isArchive -> Icons.Default.FolderZip to Color(0xFFFF9800)
                else -> Icons.Default.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.isDirectory) {
                    Text(
                        text = formatSize(item.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
            DropdownMenuItem(text = { Text("Copy") }, onClick = { menuOpen = false; onCopy() })
            DropdownMenuItem(text = { Text("Move") }, onClick = { menuOpen = false; onMove() })
            DropdownMenuItem(text = { Text("Properties") }, onClick = { menuOpen = false; onProps() })
        }
    }
}
