package fmanager.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fmanager.model.ArchiveOps
import fmanager.model.FileItem
import fmanager.model.FileOps
import fmanager.model.OpenKind

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileExplorerScreen(viewModel: FileManagerViewModel) {
    val context = LocalContext.current

    // ViewModel States
    val currentPath by viewModel.currentPath.collectAsState()
    val fileList by viewModel.fileList.collectAsState()
    val clip by viewModel.clip.collectAsState()
    val message by viewModel.message.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val editing by viewModel.editing.collectAsState()
    val viewer by viewModel.viewer.collectAsState()

    // Settings States
    val compactSpacing by viewModel.compactSpacing.collectAsState()
    val viewModeRaw by viewModel.viewMode.collectAsState()
    // Compared loosely so this keeps working no matter how the setting is cased/stored.
    val isGrid = viewModeRaw.trim().equals("grid", ignoreCase = true)

    // Full-screen Navigation States
    var settingsOpen by remember { mutableStateOf(false) }
    var informationOpen by remember { mutableStateOf(false) }

    // Dialog States
    var selectedItemForMenu by remember { mutableStateOf<FileItem?>(null) }
    var renameItem by remember { mutableStateOf<FileItem?>(null) }
    var propertiesItem by remember { mutableStateOf<FileItem?>(null) }
    var deleteItem by remember { mutableStateOf<FileItem?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var compressItem by remember { mutableStateOf<FileItem?>(null) }

    // FAB expand/collapse + search reveal
    var fabExpanded by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val e = editing
    val v = viewer

    when {
        e != null -> TextEditorScreen(e, viewModel)
        v != null -> when (v.kind) {
            OpenKind.IMAGE -> ImageViewerScreen(v.item) { viewModel.closeViewer() }
            else -> MediaPlayerScreen(v.item, v.kind == OpenKind.VIDEO) { viewModel.closeViewer() }
        }
        settingsOpen -> SettingsScreen(onClose = { settingsOpen = false })
        informationOpen -> InformationScreen(onClose = { informationOpen = false })
        else -> {
            var topMenuExpanded by remember { mutableStateOf(false) }

            BackHandler(enabled = currentPath != viewModel.rootPath) {
                viewModel.navigateUp()
            }

            val displayedList = remember(fileList, searchQuery) {
                if (searchQuery.isBlank()) fileList
                else fileList.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }

            // Breadcrumb segments built from the current path, each one tappable.
            val segments = remember(currentPath, viewModel.rootPath) {
                val root = viewModel.rootPath.trimEnd('/')
                val rel = currentPath.removePrefix(root).trim('/')
                val parts = if (rel.isBlank()) emptyList() else rel.split('/')
                var acc = root
                val list = mutableListOf("Home" to root)
                for (p in parts) {
                    acc = "$acc/$p"
                    list += p to acc
                }
                list
            }

            Scaffold(
                topBar = {
                    Column {
                        TopAppBar(
                            title = { Text("File Manager", style = MaterialTheme.typography.titleMedium) },
                            navigationIcon = {
                                if (currentPath != viewModel.rootPath) {
                                    IconButton(onClick = { viewModel.navigateUp() }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Up")
                                    }
                                }
                            },
                            actions = {
                                IconButton(onClick = { topMenuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                                }
                                DropdownMenu(
                                    expanded = topMenuExpanded,
                                    onDismissRequest = { topMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        onClick = {
                                            topMenuExpanded = false
                                            settingsOpen = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Information") },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                        onClick = {
                                            topMenuExpanded = false
                                            informationOpen = true
                                        }
                                    )
                                }
                            }
                        )

                        // Clickable path breadcrumb: tap any segment to jump there.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            segments.forEachIndexed { index, (label, path) ->
                                if (index > 0) {
                                    Text(
                                        " / ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val isLast = index == segments.lastIndex
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isLast) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = if (isLast) Modifier else Modifier.clickable {
                                        viewModel.loadDirectory(path)
                                    }
                                )
                            }
                        }

                        // Search field that grows in from the top, driven by the FAB's "Search" action.
                        AnimatedVisibility(
                            visible = searchOpen,
                            enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                            exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                singleLine = true,
                                placeholder = { Text("Search this folder") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        searchOpen = false
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close search")
                                    }
                                }
                            )
                        }
                    }
                },
                bottomBar = {
                    clip?.let { c ->
                        BottomAppBar {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (c.mode == ClipMode.COPY) "Item copied" else "Item ready to move",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row {
                                    TextButton(onClick = { viewModel.cancelClip() }) { Text("Cancel") }
                                    Button(onClick = { viewModel.paste() }) { Text("Paste Here") }
                                }
                            }
                        }
                    }
                },
                floatingActionButton = {
                    ExpandableFab(
                        expanded = fabExpanded,
                        onToggle = { fabExpanded = !fabExpanded },
                        onNewFolder = {
                            fabExpanded = false
                            showCreateFolderDialog = true
                        },
                        onNewFile = {
                            fabExpanded = false
                            showCreateFileDialog = true
                        },
                        onSearch = {
                            fabExpanded = false
                            searchOpen = !searchOpen
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (displayedList.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (searchQuery.isBlank()) "Folder is empty" else "No matches",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (isGrid) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 96.dp),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(displayedList, key = { it.path }) { item ->
                                FileGridItem(
                                    item = item,
                                    onClick = { handleItemClick(item, viewModel) },
                                    onLongClick = { selectedItemForMenu = item }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(displayedList, key = { it.path }) { item ->
                                FileListItem(
                                    item = item,
                                    compact = compactSpacing,
                                    onClick = { handleItemClick(item, viewModel) },
                                    onLongClick = { selectedItemForMenu = item }
                                )
                            }
                        }
                    }

                    progress?.let { pr ->
                        Card(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth(0.85f),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(pr.label, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(12.dp))
                                if (pr.fraction >= 0f) {
                                    LinearProgressIndicator(
                                        progress = { pr.fraction },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                Spacer(Modifier.height(12.dp))
                                TextButton(onClick = { viewModel.cancelOperation() }) { Text("Cancel") }
                            }
                        }
                    }
                }
            }

            // --- Context menu for long-pressed item, in its own file ---
            selectedItemForMenu?.let { item ->
                FileContextMenuSheet(
                    item = item,
                    onDismiss = { selectedItemForMenu = null },
                    onCopy = {
                        viewModel.setClip(item, ClipMode.COPY)
                        selectedItemForMenu = null
                    },
                    onMove = {
                        viewModel.setClip(item, ClipMode.MOVE)
                        selectedItemForMenu = null
                    },
                    onRename = {
                        renameItem = item
                        selectedItemForMenu = null
                    },
                    onCompress = {
                        compressItem = item
                        selectedItemForMenu = null
                    },
                    onExtract = {
                        viewModel.extract(item, toFolder = true)
                        selectedItemForMenu = null
                    },
                    onProperties = {
                        propertiesItem = item
                        selectedItemForMenu = null
                    },
                    onDelete = {
                        deleteItem = item
                        selectedItemForMenu = null
                    }
                )
            }

            // --- Delete confirmation ---
            deleteItem?.let { item ->
                AlertDialog(
                    onDismissRequest = { deleteItem = null },
                    title = { Text("Delete") },
                    text = { Text("Delete \"${item.name}\"? This cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.delete(item)
                            deleteItem = null
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteItem = null }) { Text("Cancel") }
                    }
                )
            }

            // --- Rename Dialog ---
            renameItem?.let { item ->
                var text by remember { mutableStateOf(item.name) }
                AlertDialog(
                    onDismissRequest = { renameItem = null },
                    title = { Text("Rename") },
                    text = {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            label = { Text("New Name") }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (text.isNotBlank()) viewModel.rename(item, text.trim())
                            renameItem = null
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { renameItem = null }) { Text("Cancel") }
                    }
                )
            }

            // --- New Folder Dialog ---
            if (showCreateFolderDialog) {
                var folderName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showCreateFolderDialog = false },
                    title = { Text("Create Folder") },
                    text = {
                        OutlinedTextField(
                            value = folderName,
                            onValueChange = { folderName = it },
                            singleLine = true,
                            label = { Text("Folder Name") }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (folderName.isNotBlank()) viewModel.createFolder(folderName.trim())
                            showCreateFolderDialog = false
                        }) { Text("Create") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
                    }
                )
            }

            // --- New File Dialog ---
            if (showCreateFileDialog) {
                var fileName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showCreateFileDialog = false },
                    title = { Text("Create File") },
                    text = {
                        OutlinedTextField(
                            value = fileName,
                            onValueChange = { fileName = it },
                            singleLine = true,
                            label = { Text("File Name") }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (fileName.isNotBlank()) viewModel.createFile(fileName.trim())
                            showCreateFileDialog = false
                        }) { Text("Create") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateFileDialog = false }) { Text("Cancel") }
                    }
                )
            }

            // --- Properties Dialog ---
            propertiesItem?.let { item ->
                var stats by remember { mutableStateOf<FileOps.Stats?>(null) }
                LaunchedEffect(item) { stats = viewModel.stats(item) }

                AlertDialog(
                    onDismissRequest = { propertiesItem = null },
                    title = { Text("Properties") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Name: ${item.name}")
                            Text("Path: ${item.path}")
                            stats?.let { s -> Text("Details: ${s.toString()}") } ?: Text("Calculating size...")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { propertiesItem = null }) { Text("Close") }
                    }
                )
            }

            // --- Compress Dialog ---
            compressItem?.let { item ->
                var archiveName by remember { mutableStateOf(item.name) }
                AlertDialog(
                    onDismissRequest = { compressItem = null },
                    title = { Text("Compress Item") },
                    text = {
                        OutlinedTextField(
                            value = archiveName,
                            onValueChange = { archiveName = it },
                            singleLine = true,
                            label = { Text("Archive Name") }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (archiveName.isNotBlank()) {
                                viewModel.compress(
                                    item = item,
                                    format = ArchiveOps.Format.ZIP,
                                    level = ArchiveOps.Level.NORMAL,
                                    name = archiveName.trim()
                                )
                            }
                            compressItem = null
                        }) { Text("Compress") }
                    },
                    dismissButton = {
                        TextButton(onClick = { compressItem = null }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

/**
 * Circular FAB, bottom-right, that expands into three labeled actions
 * (Search / New File / New Folder) with a smooth scale+fade, colored from
 * the current theme's primary color.
 */
@Composable
private fun ExpandableFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    onSearch: () -> Unit
) {
    Column(horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200)) + expandVertically(tween(220)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(160))
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniFabAction(Icons.Default.Search, "Search", onSearch)
                MiniFabAction(Icons.Default.NoteAdd, "New File", onNewFile)
                MiniFabAction(Icons.Default.CreateNewFolder, "New Folder", onNewFolder)
                Spacer(Modifier.height(4.dp))
            }
        }

        val rotation by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (expanded) 45f else 0f,
            animationSpec = tween(220),
            label = "fabRotation"
        )

        FloatingActionButton(
            onClick = onToggle,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (expanded) "Close menu" else "Add",
                modifier = Modifier.graphicsLayer(rotationZ = rotation)
            )
        }
    }
}

@Composable
private fun MiniFabAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 2.dp
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}

private fun handleItemClick(item: FileItem, viewModel: FileManagerViewModel) {
    if (item.isDirectory) {
        viewModel.loadDirectory(item.path)
    } else {
        val lower = item.name.lowercase()
        when {
            lower.endsWith(".txt") || lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".log") || lower.endsWith(".md") -> {
                viewModel.openEditor(item)
            }
            lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif") -> {
                viewModel.openViewer(item, OpenKind.IMAGE)
            }
            lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") -> {
                viewModel.openViewer(item, OpenKind.VIDEO)
            }
            lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".flac") -> {
                viewModel.openViewer(item, OpenKind.AUDIO)
            }
            else -> viewModel.openEditor(item)
        }
    }
}

// Folders use the theme's primary color; files use tertiary, so the two are
// visually distinct at a glance in both list and grid modes.
private fun iconFor(item: FileItem) =
    if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    item: FileItem,
    compact: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = if (compact) 2.dp else 6.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 8.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconFor(item),
                contentDescription = null,
                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(if (compact) 20.dp else 24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = item.name,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridItem(
    item: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = iconFor(item),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
