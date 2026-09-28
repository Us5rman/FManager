package fmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import fmanager.model.OpProgress
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fmanager.model.ArchiveOps
import fmanager.model.FileItem
import fmanager.model.FileOps
import fmanager.model.FileTypes
import fmanager.model.OpProgress
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
    val viewer by viewModel.viewer.collectAsState()
    var settingsOpen by remember { mutableStateOf(false) }
    val e = editing
    val v = viewer
    when {
        e != null -> TextEditorScreen(e, viewModel)
        v != null -> when (v.kind) {
            OpenKind.IMAGE -> ImageViewerScreen(v.item) { viewModel.closeViewer() }
            else -> MediaPlayerScreen(v.item, v.kind == OpenKind.VIDEO) { viewModel.closeViewer() }
        }
        settingsOpen -> SettingsScreen { settingsOpen = false }
        else -> BrowserScreen(viewModel) { settingsOpen = true }
    }
}

private fun buildSegments(current: String, root: String): List<Pair<String, String>> {
    val list = mutableListOf("Internal Storage" to root)
    if (current.length > root.length && current.startsWith(root)) {
        var acc = root
        current.removePrefix(root).split('/').filter { it.isNotEmpty() }.forEach { part ->
            acc = "$acc/$part"
            list += part to acc
        }
    }
    return list
}

@Composable
private fun PathCrumbs(segments: List<Pair<String, String>>, onNavigate: (String) -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(segments) {
        withFrameNanos { }
        scroll.animateScrollTo(scroll.maxValue)
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { i, (name, path) ->
            val last = i == segments.lastIndex
            if (i > 0) {
                Text("›", style = MaterialTheme.typography.labelMedium, color = muted)
            }
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = if (last) MaterialTheme.colorScheme.primary else muted,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !last) { onNavigate(path) }
                    .padding(horizontal = 4.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun ProgressChip(p: OpProgress, onClick: () -> Unit) {
    val anim by animateFloatAsState(targetValue = p.fraction.coerceAtLeast(0f), label = "progress")
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            if (p.fraction < 0f) "${p.label}…" else "${p.label} ${(p.fraction * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(4.dp))
        if (p.fraction < 0f) {
            LinearProgressIndicator(Modifier.width(96.dp))
        } else {
            LinearProgressIndicator(progress = anim, modifier = Modifier.width(96.dp))
        }
    }
}

@Composable
private fun ExplorerTopBar(
    currentPath: String,
    rootPath: String,
    progress: OpProgress?,
    onNavigate: (String) -> Unit,
    onProgressClick: () -> Unit,
    onNewFolder: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    val segments = remember(currentPath, rootPath) { buildSegments(currentPath, rootPath) }
    var menuOpen by remember { mutableStateOf(false) }

    // Keep the last progress around so the chip can animate out
    val lastProgress = remember { arrayOfNulls<OpProgress>(1) }
    if (progress != null) lastProgress[0] = progress
    val shown = progress ?: lastProgress[0]

    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = segments.last().first,
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 3 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 3 })
                },
                label = "title"
            ) { name ->
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = segments.size > 1) {
                            onNavigate(segments[segments.size - 2].second)
                        }
                )
            }
            PathCrumbs(segments, onNavigate)
        }

        AnimatedVisibility(
            visible = progress != null,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            shown?.let { ProgressChip(it, onProgressClick) }
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("New folder") },
                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                    onClick = { menuOpen = false; onNewFolder() }
                )
                DropdownMenuItem(
                    text = { Text("Refresh") },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    onClick = { menuOpen = false; onRefresh() }
                )
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = { menuOpen = false; onSettings() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserScreen(viewModel: FileManagerViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val currentPath by viewModel.currentPath.collectAsState()
    val fileList by viewModel.fileList.collectAsState()
    val clip by viewModel.clip.collectAsState()
    val message by viewModel.message.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var actionTarget by remember { mutableStateOf<FileItem?>(null) }
    var openAsTarget by remember { mutableStateOf<FileItem?>(null) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var propsTarget by remember { mutableStateOf<FileItem?>(null) }
    var compressTarget by remember { mutableStateOf<FileItem?>(null) }
    var extractTarget by remember { mutableStateOf<FileItem?>(null) }
    var newFolderDialog by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }

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
            ExplorerTopBar(
                currentPath = currentPath,
                rootPath = viewModel.rootPath,
                progress = progress,
                onNavigate = { viewModel.loadDirectory(it) },
                onProgressClick = { showProgress = true },
                onNewFolder = { newFolderDialog = true },
                onRefresh = { viewModel.refresh() },
                onSettings = onOpenSettings
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
            }
        }
    }

    // One shared bottom sheet for the long-press menu
    actionTarget?.let { t ->
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            sheetState = sheetState
        ) {
            Text(
                t.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (!t.isDirectory) {
                SheetAction("Open as...") { actionTarget = null; openAsTarget = t }
            }
            SheetAction("Compress...") { actionTarget = null; compressTarget = t }
            if (!t.isDirectory && ArchiveOps.canExtract(t.name)) {
                SheetAction("Extract...") { actionTarget = null; extractTarget = t }
                SheetAction("Repair") { actionTarget = null; viewModel.repair(t) }
            }
            SheetAction("Rename") { actionTarget = null; renameTarget = t }
            SheetAction("Copy") { actionTarget = null; viewModel.setClip(t, ClipMode.COPY) }
            SheetAction("Move") { actionTarget = null; viewModel.setClip(t, ClipMode.MOVE) }
            SheetAction("Properties") { actionTarget = null; propsTarget = t }
            Spacer(Modifier.height(24.dp))
        }
    }

    openAsTarget?.let { t ->
        val recommended = FileTypes.kindOf(t)
        AlertDialog(
            onDismissRequest = { openAsTarget = null },
            title = { Text("Open as") },
            text = {
                Column {
                    OpenKind.values().forEach { kind ->
                        val label = kind.title
                            .replace("Open as ", "")
                            .replace("Open / edit as ", "")
                            .replaceFirstChar { it.uppercase() }
                        TextButton(onClick = {
                            openAsTarget = null
                            openFile(context, viewModel, t, kind)
                        }) {
                            Text(if (kind == recommended) "$label (recommended)" else label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { openAsTarget = null }) { Text("Cancel") } }
        )
    }

    compressTarget?.let { t ->
        var format by remember(t) { mutableStateOf(ArchiveOps.Format.ZIP) }
        var level by remember(t) { mutableStateOf(ArchiveOps.Level.NORMAL) }
        var name by remember(t) {
            mutableStateOf(if (t.isDirectory) t.name else t.name.substringBeforeLast('.', t.name))
        }
        AlertDialog(
            onDismissRequest = { compressTarget = null },
            title = { Text("Compress") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Archive name") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Format", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArchiveOps.Format.values().forEach { f ->
                            FilterChip(
                                selected = format == f,
                                onClick = { format = f },
                                label = { Text(f.title) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Level", style = MaterialTheme.typography.titleSmall)
                    ArchiveOps.Level.values().forEach { l ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { level = l },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = level == l, onClick = { level = l })
                            Text("${l.title}  (${l.hint})", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.compress(t, format, level, name)
                    compressTarget = null
                }) { Text("Compress") }
            },
            dismissButton = { TextButton(onClick = { compressTarget = null }) { Text("Cancel") } }
        )
    }

    extractTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { extractTarget = null },
            title = { Text("Extract") },
            text = { Text(t.name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.extract(t, toFolder = true)
                    extractTarget = null
                }) { Text("To new folder") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.extract(t, toFolder = false)
                    extractTarget = null
                }) { Text("Here") }
            }
        )
    }

    if (newFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolderDialog = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createFolder(folderName)
                    newFolderDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { newFolderDialog = false }) { Text("Cancel") } }
        )
    }

    val p = progress
    if (showProgress && p != null) {
        AlertDialog(
            onDismissRequest = { showProgress = false },
            title = { Text(p.label) },
            text = {
                if (p.fraction < 0f) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = p.fraction, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { showProgress = false }) { Text("Hide") } },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.cancelOperation()
                    showProgress = false
                }) { Text("Cancel operation") }
            }
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
    val divider = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val kind = remember(item.name, item.isDirectory) {
        if (item.isDirectory) null else FileTypes.kindOf(item)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .combinedClickable(
                onClick = { onClick(item) },
                onLongClick = { onLongClick(item) }
            )
            .drawBehind {
                drawLine(
                    color = divider,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            kind == OpenKind.IMAGE || kind == OpenKind.VIDEO -> FileThumb(item, kind!!, 44.dp)
            kind == OpenKind.AUDIO -> Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = primary)
            }
            else -> Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
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
            }
        }
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
