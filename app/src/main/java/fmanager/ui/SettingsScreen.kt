package fmanager.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fmanager.ui.theme.ThemeSettings
import fmanager.ui.theme.ThemeSpec
import fmanager.ui.theme.presetThemes
import kotlinx.coroutines.launch

private val accentPalette = listOf(
    0xFF6750A4, 0xFF1E88E5, 0xFF00ACC1, 0xFF43A047, 0xFFFDD835,
    0xFFFB8C00, 0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF546E7A
)
private val backgroundPalette = listOf(
    0xFFFFFFFF, 0xFFF5F5F5, 0xFFFFF8E1, 0xFFE3F2FD, 0xFFFCE4EC,
    0xFF121212, 0xFF000000, 0xFF0E1F17, 0xFF1A1A2E, 0xFF2B1B17
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)

    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var busy by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val systemDark = isSystemInDarkTheme()
            val options = listOf(ThemeSettings.defaultSpec(systemDark)) +
                presetThemes + ThemeSettings.customSpec()
            options.forEach { spec ->
                ThemeOption(spec, spec.id == ThemeSettings.themeId) {
                    ThemeSettings.setTheme(spec.id)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Custom colors", style = MaterialTheme.typography.titleMedium)
            Text(
                "Changing any color switches to the Custom theme.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColorRow("Accent", ThemeSettings.customAccent, accentPalette) {
                ThemeSettings.updateCustom(accent = it)
            }
            ColorRow("Background", ThemeSettings.customBackground, backgroundPalette) {
                ThemeSettings.updateCustom(background = it)
            }

            Spacer(Modifier.height(24.dp))
            Text("Updates", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Version ${Updater.currentVersion(context)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    val r = runCatching { Updater.check(context) }
                    busy = false
                    r.onSuccess { info ->
                        if (info == null) {
                            Toast.makeText(context, "You're up to date", Toast.LENGTH_SHORT).show()
                        } else {
                            update = info
                        }
                    }.onFailure {
                        Toast.makeText(context, "Update check failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }) { Text(if (busy) "Checking..." else "Check for updates") }
            Spacer(Modifier.height(32.dp))
        }
    }

    update?.let { u ->
        AlertDialog(
            onDismissRequest = { if (!busy) update = null },
            title = { Text("Update available") },
            text = {
                Text(
                    if (busy) "Downloading..."
                    else "Version ${u.version} is available. You have ${Updater.currentVersion(context)}."
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        val r = runCatching { Updater.download(context, u.url) }
                        busy = false
                        r.onSuccess { apk ->
                            if (Updater.install(context, apk)) update = null
                        }.onFailure {
                            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { update = null }) { Text("Later") }
            }
        )
    }
}

@Composable
private fun ThemeOption(spec: ThemeSpec, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(shape)
                .background(
                    spec.gradient?.let { Brush.linearGradient(it) }
                        ?: SolidColor(spec.background)
                )
                .border(1.dp, MaterialTheme.colorScheme.outline, shape),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(spec.primary))
        }
        Spacer(Modifier.width(16.dp))
        Text(spec.title, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ColorRow(
    label: String,
    color: Int,
    palette: List<Long>,
    onChange: (Int) -> Unit
) {
    var hex by remember(color) { mutableStateOf("%06X".format(color and 0xFFFFFF)) }
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            palette.forEach { c ->
                val ci = c.toInt()
                val selected = ci == color
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(ci))
                        .border(
                            if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                        .clickable { onChange(ci) }
                )
            }
        }
        OutlinedTextField(
            value = hex,
            onValueChange = { v ->
                val clean = v.trim().removePrefix("#").uppercase().take(6)
                hex = clean
                if (clean.length == 6 && clean.all { it in "0123456789ABCDEF" }) {
                    onChange((0xFF000000 or clean.toLong(16)).toInt())
                }
            },
            label = { Text("Hex (RRGGBB)") },
            prefix = { Text("#") },
            singleLine = true
        )
    }
}
