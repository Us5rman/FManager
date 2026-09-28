package fmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fmanager.model.FileItem
import fmanager.model.FileOps
import fmanager.model.FileTypes
import fmanager.model.OpenKind
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

@Composable
fun FileExplorerScreen(viewModel: FileManagerViewModel) {
    val editing by viewModel.editing.collectAsState()
    val e = editing
    if (e != null) TextEditorScreen(e, viewModel) else BrowserScreen(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserScreen(viewModel: FileManagerViewModel) {
    val context = LocalContext.current
    val currentPath by viewModel.currentPath.collectAsState()
    val fileList by viewModel.fileList.collectAsState()
    val clip by viewModel.clip.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var actionTarget by remember { mutableStateOf<FileItem?>(null) }
    var openAsTarget by remember { mutableStateOf<FileItem?>(null) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var propsTarget by remember { mutableStateOf<FileItem?>(null) }

    // Stable callbacks: rows don't recompose while scrolling
    val onItemClick = remember<(FileItem) -> Unit> {
        { item ->
            if (item.isDirectory) viewModel.loadDirectory(item.path)
            else openFile(context, viewModel, item, FileTypes.kindOf(item))
        }
    }
    val onItemLongClick = remember<(FileItem) -> Unit> { { item -> actionTarget = item } }

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
                FileRowItem(item, onItemClick, onItemLongClick)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }

    // One shared bottom sheet for the long-press menu
    actionTarget?.let { t ->
        ModalBottomSheet(onDismissRequest = { actionTarget = null }) {
            Text(
                t.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (!t.isDirectory) {
                SheetAction(FileTypes.kindOf(t).title) {
                    actionTarget = null
                    openFile(context, viewModel, t, FileTypes.kindOf(t))
                }
                SheetAction("Open as...") { actionTarget = null; openAsTarget = t }
            }
            SheetAction("Rename") { actionTarget = null; renameTarget = t }
            SheetAction("Copy") { actionTarget = null; viewModel.setClip(t, ClipMode.COPY) }
            SheetAction("Move") { actionTarget = null; viewModel.setClip(t, ClipMode.MOVE) }
            SheetAction("Properties") { actionTarget = null; propsTarget = t }
            Spacer(Modifier.height(24.dp))
        }
    }

    openAsTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { openAsTarget = null },
            title = { Text("Open as") },
            text = {
                Column {
                    OpenKind.values().forEach { kind ->
                        TextButton(onClick = {
                            openAsTarget = null
                            openFile(context, viewModel, t, kind)
                        }) { Text(kind.title.replace("Open as ", "").replace("Open / edit as ", "").replaceFirstChar { it.uppercase() }) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { openAsTarget = null }) { Text("Cancel") } }
        )
    }

    renameTarget?.let { t ->
        var text by remember(t) { mutableStateOf(t.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(t, text)
                    renameTarget = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
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
            confirmButton = { TextButton(onClick = { propsTarget = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRowItem(
    item: FileItem,
    onClick: (FileItem) -> Unit,
    onLongClick: (FileItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick(item) },
                onLongClick = { onLongClick(item) }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val primary = MaterialTheme.colorScheme.primary
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val (icon, tint) = when {
            item.isDirectory -> Icons.Default.Folder to primary
            item.isArchive -> Icons.Default.FolderZip to Color(0xFFFF9800)
            else -> Icons.Default.InsertDriveFile to muted
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
                val sizeText = remember(item.sizeBytes) { formatSize(item.sizeBytes) }
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
            }
        }
    }
}
