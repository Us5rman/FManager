package fmanager.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fmanager.model.ArchiveOps
import fmanager.model.FileItem
import fmanager.model.FileOps
import fmanager.model.OpenKind
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(viewModel: FileManagerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
    val viewMode by viewModel.viewMode.collectAsState()

    // Full-screen Navigation States
    var settingsOpen by remember { mutableStateOf(false) }
    var informationOpen by remember { mutableStateOf(false) }

    // Dialog States
    var selectedItemForMenu by remember { mutableStateOf<FileItem?>(null) }
    var renameItem by remember { mutableStateOf<FileItem?>(null) }
    var propertiesItem by remember { mutableStateOf<FileItem?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var compressItem by remember { mutableStateOf<FileItem?>(null) }

    // Handle Toast Messages
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    // Load initial directory
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
        settingsOpen -> SettingsScreen(
            onClose = { settingsOpen = false }
        )    
        informationOpen -> InformationScreen(
            onClose = { informationOpen = false }
        )
        else -> {
            var topMenuExpanded by remember { mutableStateOf(false) }

            BackHandler(enabled = currentPath != viewModel.rootPath) {
                viewModel.navigateUp()
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("File Manager", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = currentPath,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            if (currentPath != viewModel.rootPath) {
                                IconButton(onClick = { viewModel.navigateUp() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Up")
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { showCreateFolderDialog = true }) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                            }
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
                                    TextButton(onClick = { viewModel.cancelClip() }) {
                                        Text("Cancel")
                                    }
                                    Button(onClick = { viewModel.paste() }) {
                                        Text("Paste Here")
                                    }
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    val verticalPadding = if (compactSpacing) 2.dp else 6.dp

                    if (fileList.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Folder is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (viewMode == "Grid") {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(fileList, key = { it.path }) { item ->
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
                            items(fileList, key = { it.path }) { item ->
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
                                TextButton(onClick = { viewModel.cancelOperation() }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }

            // --- Context Menu Sheet / Dialog for Long-Pressed Item ---
            selectedItemForMenu?.let { item ->
                AlertDialog(
                    onDismissRequest = { selectedItemForMenu = null },
                    title = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    text = {
                        Column {
                            ListItem(
                                headlineContent = { Text("Copy") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                modifier = Modifier.combinedClickable {
                                    viewModel.setClip(item, ClipMode.COPY)
                                    selectedItemForMenu = null
                                }
                            )
                            ListItem(
                                headlineContent = { Text("Cut / Move") },
                                leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                                modifier = Modifier.combinedClickable {
                                    viewModel.setClip(item, ClipMode.MOVE)
                                    selectedItemForMenu = null
                                }
                            )
                            ListItem(
                                headlineContent = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                modifier = Modifier.combinedClickable {
                                    renameItem = item
                                    selectedItemForMenu = null
                                }
                            )
                            if (item.name.endsWith(".zip", ignoreCase = true) || item.name.endsWith(".tar", ignoreCase = true)) {
                                ListItem(
                                    headlineContent = { Text("Extract") },
                                    leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
                                    modifier = Modifier.combinedClickable {
                                        viewModel.extract(item, toFolder = true)
                                        selectedItemForMenu = null
                                    }
                                )
                            } else {
                                ListItem(
                                    headlineContent = { Text("Compress") },
                                    leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                    modifier = Modifier.combinedClickable {
                                        compressItem = item
                                        selectedItemForMenu = null
                                    }
                                )
                            }
                            ListItem(
                                headlineContent = { Text("Properties") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                modifier = Modifier.combinedClickable {
                                    propertiesItem = item
                                    selectedItemForMenu = null
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { selectedItemForMenu = null }) {
                            Text("Close")
                        }
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
                            viewModel.rename(item, text)
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
                            viewModel.createFolder(folderName)
                            showCreateFolderDialog = false
                        }) { Text("Create") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
                    }
                )
            }

            // --- Properties Dialog ---
            propertiesItem?.let { item ->
                var stats by remember { mutableStateOf<FileOps.Stats?>(null) }
                LaunchedEffect(item) {
                    stats = viewModel.stats(item)
                }

                AlertDialog(
                    onDismissRequest = { propertiesItem = null },
                    title = { Text("Properties") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Name: ${item.name}")
                            Text("Path: ${item.path}")
                            stats?.let { s ->
                                Text("Size: ${s.formattedSize}")
                                Text("Contains: ${s.fileCount} files, ${s.dirCount} folders")
                            } ?: Text("Calculating size...")
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
                            viewModel.compress(
                                item = item,
                                format = ArchiveOps.Format.ZIP,
                                level = ArchiveOps.Level.NORMAL,
                                name = archiveName
                            )
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
            else -> {
                viewModel.openEditor(item)
            }
        }
    }
}

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
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 8.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
