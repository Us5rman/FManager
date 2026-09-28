package fmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fmanager.model.FileItem
import kotlinx.coroutines.launch
import java.util.regex.Pattern

// GitHub Exact Color Palettes
private object GitHubColors {
    // Canvas Backgrounds
    val LightBg = Color(0xFFFFFFFF)
    val DarkBg = Color(0xFF0D1117)

    // Default Foreground Text
    val LightText = Color(0xFF24292E)
    val DarkText = Color(0xFFC9D1D9)

    // GitHub Light Syntax Colors
    val LightKeyword = Color(0xFFD73A49)    // Red
    val LightString = Color(0xFF032F62)     // Dark Blue
    val LightComment = Color(0xFF6A737D)    // Gray
    val LightNumber = Color(0xFF005CC5)     // Blue
    val LightFunction = Color(0xFF6F42C1)   // Purple
    val LightVariable = Color(0xFFE36209)   // Orange

    // GitHub Dark Syntax Colors
    val DarkKeyword = Color(0xFFFF7B72)     // Coral Red
    val DarkString = Color(0xFFA5D6FF)      // Light Blue
    val DarkComment = Color(0xFF8B949E)     // Muted Gray
    val DarkNumber = Color(0xFF79C0FF)      // Cyan Blue
    val DarkFunction = Color(0xD2A8FF)     // Soft Violet
    val DarkVariable = Color(0xFFFFA657)   // Orange

    // Search Match Highlights
    val LightSearchMatch = Color(0xFFFFE885) // GitHub Light Search Yellow
    val DarkSearchMatch = Color(0xFF5A4D00)  // GitHub Dark Search Gold
}

// GitHub Syntax & Search Visual Transformation
class GitHubCodeHighlighter(
    val isDark: Boolean,
    val searchQuery: String = ""
) : VisualTransformation {

    private val keywordPattern = Pattern.compile(
        "\\b(class|object|interface|fun|val|var|import|package|return|if|else|when|for|while|do|break|continue|try|catch|finally|throw|new|public|private|protected|internal|override|static|final|data|enum|sealed|typealias|null|true|false|this|super|const|lateinit|by|type|struct|fn|let|mut|async|await|import|export|from)\\b"
    )
    private val stringPattern = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")
    private val commentPattern = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")
    private val numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?([fFLl])?\\b")
    private val functionPattern = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*(?=\\s*\\()")
    private val annotationPattern = Pattern.compile("@[a-zA-Z_][a-zA-Z0-9_]*")

    override fun filter(text: AnnotatedString): TransformedText {
        val rawText = text.text
        val builder = AnnotatedString.Builder(rawText)

        val textColor = if (isDark) GitHubColors.DarkText else GitHubColors.LightText
        val keywordColor = if (isDark) GitHubColors.DarkKeyword else GitHubColors.LightKeyword
        val stringColor = if (isDark) GitHubColors.DarkString else GitHubColors.LightString
        val commentColor = if (isDark) GitHubColors.DarkComment else GitHubColors.LightComment
        val numberColor = if (isDark) GitHubColors.DarkNumber else GitHubColors.LightNumber
        val functionColor = if (isDark) GitHubColors.DarkFunction else GitHubColors.LightFunction
        val variableColor = if (isDark) GitHubColors.DarkVariable else GitHubColors.LightVariable

        // Base text style
        builder.addStyle(
            style = SpanStyle(color = textColor, fontFamily = FontFamily.Monospace),
            start = 0,
            end = rawText.length
        )

        fun highlight(pattern: Pattern, color: Color, weight: FontWeight = FontWeight.Normal) {
            val matcher = pattern.matcher(rawText)
            while (matcher.find()) {
                builder.addStyle(
                    style = SpanStyle(color = color, fontWeight = weight),
                    start = matcher.start(),
                    end = matcher.end()
                )
            }
        }

        // Apply GitHub token highlighting rules
        highlight(keywordPattern, keywordColor, FontWeight.Bold)
        highlight(functionPattern, functionColor)
        highlight(annotationPattern, variableColor)
        highlight(numberPattern, numberColor)
        highlight(stringPattern, stringColor)
        highlight(commentPattern, commentColor)

        // Highlight active search matches
        if (searchQuery.isNotEmpty()) {
            val searchMatchBg = if (isDark) GitHubColors.DarkSearchMatch else GitHubColors.LightSearchMatch
            var searchIndex = rawText.indexOf(searchQuery, ignoreCase = true)
            while (searchIndex >= 0 && searchIndex < rawText.length) {
                val matchEnd = searchIndex + searchQuery.length
                builder.addStyle(
                    style = SpanStyle(
                        background = searchMatchBg,
                        color = if (isDark) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    ),
                    start = searchIndex,
                    end = matchEnd
                )
                searchIndex = rawText.indexOf(searchQuery, searchIndex + searchQuery.length, ignoreCase = true)
            }
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(item: FileItem, viewModel: FileManagerViewModel) {
    var original by remember(item.path) { mutableStateOf("") }
    var text by remember(item.path) { mutableStateOf("") }
    var loaded by remember(item.path) { mutableStateOf(false) }
    var failed by remember(item.path) { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    // Search state
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matchIndices by remember { mutableStateOf(listOf<Int>()) }
    var currentMatchIndex by remember { mutableStateOf(-1) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    val editorBackground = if (isDark) GitHubColors.DarkBg else GitHubColors.LightBg
    val editorTextColor = if (isDark) GitHubColors.DarkText else GitHubColors.LightText
    val cursorColor = if (isDark) Color(0xFF58A6FF) else Color(0xFF0969DA)

    LaunchedEffect(item.path) {
        val r = viewModel.readText(item)
        if (r == null) failed = true else { original = r; text = r }
        loaded = true
    }

    // Update search matches when query or text changes
    LaunchedEffect(searchQuery, text) {
        if (searchQuery.isNotEmpty()) {
            val matches = mutableListOf<Int>()
            var idx = text.indexOf(searchQuery, ignoreCase = true)
            while (idx >= 0) {
                matches.add(idx)
                idx = text.indexOf(searchQuery, idx + searchQuery.length, ignoreCase = true)
            }
            matchIndices = matches
            currentMatchIndex = if (matches.isNotEmpty()) 0 else -1
        } else {
            matchIndices = emptyList()
            currentMatchIndex = -1
        }
    }

    val dirty = text != original

    BackHandler {
        if (isSearching) {
            isSearching = false
            searchQuery = ""
        } else if (dirty) {
            confirmExit = true
        } else {
            viewModel.closeEditor()
        }
    }

    Scaffold(
        topBar = {
            if (isSearching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search in file...", fontSize = 14.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearching = false
                            searchQuery = ""
                        }) { Icon(Icons.Default.Close, contentDescription = "Close Search") }
                    },
                    actions = {
                        if (matchIndices.isNotEmpty()) {
                            Text(
                                text = "${currentMatchIndex + 1}/${matchIndices.size}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(onClick = {
                                if (matchIndices.isNotEmpty()) {
                                    currentMatchIndex = (currentMatchIndex - 1 + matchIndices.size) % matchIndices.size
                                }
                            }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match")
                            }
                            IconButton(onClick = {
                                if (matchIndices.isNotEmpty()) {
                                    currentMatchIndex = (currentMatchIndex + 1) % matchIndices.size
                                }
                            }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match")
                            }
                        }
                    }
                )
            } else {
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
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search File")
                        }
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
            }
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
                    .background(editorBackground)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = editorTextColor
                    ),
                    cursorBrush = SolidColor(cursorColor),
                    visualTransformation = remember(isDark, searchQuery) {
                        GitHubCodeHighlighter(isDark = isDark, searchQuery = searchQuery)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
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
