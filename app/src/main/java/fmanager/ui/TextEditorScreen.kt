package fmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fmanager.model.FileItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(item: FileItem, viewModel: FileManagerViewModel) {
    var original by remember(item.path) { mutableStateOf("") }
    var text by remember(item.path) { mutableStateOf("") }
    var loaded by remember(item.path) { mutableStateOf(false) }
    var failed by remember(item.path) { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(item.path) {
        val r = viewModel.readText(item)
        if (r == null) failed = true else { original = r; text = r }
        loaded = true
    }

    val dirty = text != original

    BackHandler {
        if (dirty) confirmExit = true else viewModel.closeEditor()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (if (dirty) "* " else "") + item.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (dirty) confirmExit = true else viewModel.closeEditor()
                    }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(
                        enabled = dirty && loaded && !failed,
                        onClick = {
                            scope.launch {
                                val ok = viewModel.writeText(item, text)
                                if (ok) original = text
                                snackbar.showSnackbar(if (ok) "Saved" else "Save failed")
                            }
                        }
                    ) { Text("Save") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when {
            !loaded -> Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Loading...")
            }
            failed -> Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Can't open as text: the file is binary or larger than 1 MB.")
            }
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Unsaved changes") },
            text = { Text("Discard your changes?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    viewModel.closeEditor()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("Keep editing") }
            }
        )
    }
}
