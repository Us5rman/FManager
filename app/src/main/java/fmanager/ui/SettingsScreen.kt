package fmanager.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
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
import androidx.compose.material.icons.filled.*
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
import androidx.core.content.ContextCompat
import fmanager.ui.theme.RgbMode
import fmanager.ui.theme.ThemeSettings
import fmanager.ui.theme.ThemeSpec
import fmanager.ui.theme.presetThemes
import fmanager.ui.theme.rememberRgbColor
import kotlinx.coroutines.launch
import java.util.Locale

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

    val prefs = remember { context.getSharedPreferences("fmanager_settings", Context.MODE_PRIVATE) }
    var showHiddenFiles by remember { mutableStateOf(prefs.getBoolean("show_hidden", false)) }
    var foldersFirst by remember { mutableStateOf(prefs.getBoolean("folders_first", true)) }
    var confirmDelete by remember { mutableStateOf(prefs.getBoolean("confirm_delete", true)) }
    var viewMode by remember { mutableStateOf(prefs.getString("view_mode", "List") ?: "List") }
    var isCompactDensity by remember { mutableStateOf(prefs.getBoolean("compact_density", false)) }
    var rgbDurationSeconds by remember { mutableStateOf(prefs.getFloat("rgb_speed", 10f)) }

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
            // --- Theme & Appearance ---
            Text("Theme & Customization", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            val systemDark = isSystemInDarkTheme()

            val presetSpecs = presetThemes.map { it.spec(systemDark) }
            val options = presetSpecs + ThemeSettings.customSpec()

            options.forEach { spec ->
                ThemeOption(spec, spec.id == ThemeSettings.themeId) {
                    ThemeSettings.setTheme(spec.id)
                }
            }

            // RGB Options (Only when RGB Theme is selected)
            if (ThemeSettings.themeId == "rgb") {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("RGB Screen Glow", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(12.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Screen Edge RGB Glow", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = ThemeSettings.rgbOutlineEnabled,
                                onCheckedChange = { ThemeSettings.setRgbOutline(it) }
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Animation Speed: ${rgbDurationSeconds.toInt()}s",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = rgbDurationSeconds,
                            onValueChange = {
                                rgbDurationSeconds = it
                                prefs.edit().putFloat("rgb_speed", it).apply()
                            },
                            valueRange = 3f..30f,
                            steps = 26,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Spacer(Modifier.height(12.dp))
                        Text("Color Transition Mode", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))

                        RgbMode.entries.forEach { mode ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { ThemeSettings.setRgbMode(mode) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = ThemeSettings.rgbMode == mode,
                                    onClick = { ThemeSettings.setRgbMode(mode) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Custom Colors (ONLY shown when Custom Theme is active)
            if (ThemeSettings.themeId == "custom") {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Custom Palette", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        ColorRow("Accent Color", ThemeSettings.customAccent, accentPalette) {
                            ThemeSettings.updateCustom(accent = it)
                        }
                        ColorRow("Background Color", ThemeSettings.customBackground, backgroundPalette) {
                            ThemeSettings.updateCustom(background = it)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- File & Display Settings ---
            Text("Display & Operations", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            SettingSwitchRow(
                title = "Show Hidden Files",
                subtitle = "Display files starting with a dot (.)",
                checked = showHiddenFiles,
                onCheckedChange = {
                    showHiddenFiles = it
                    prefs.edit().putBoolean("show_hidden", it).apply()
                }
            )

            SettingSwitchRow(
                title = "Folders First",
                subtitle = "Keep folders pinned at top of file list",
                checked = foldersFirst,
                onCheckedChange = {
                    foldersFirst = it
                    prefs.edit().putBoolean("folders_first", it).apply()
                }
            )

            SettingSwitchRow(
                title = "Compact Spacing",
                subtitle = "Reduce padding to fit more files on screen",
                checked = isCompactDensity,
                onCheckedChange = {
                    isCompactDensity = it
                    prefs.edit().putBoolean("compact_density", it).apply()
                }
            )

            SettingSwitchRow(
                title = "Confirm Before Delete",
                subtitle = "Require confirmation before removing files",
                checked = confirmDelete,
                onCheckedChange = {
                    confirmDelete = it
                    prefs.edit().putBoolean("confirm_delete", it).apply()
                }
            )

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Default View Mode", style = MaterialTheme.typography.bodyLarge)
                    Text("Choose default layout mode", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = viewMode == "List",
                        onClick = {
                            viewMode = "List"
                            prefs.edit().putString("view_mode", "List").apply()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("List") }
                    SegmentedButton(
                        selected = viewMode == "Grid",
                        onClick = {
                            viewMode = "Grid"
                            prefs.edit().putString("view_mode", "Grid").apply()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Grid") }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.cacheDir.deleteRecursively()
                        Toast.makeText(context, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "Failed to clear cache", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Clear App Cache")
            }

            Spacer(Modifier.height(24.dp))

            // --- Storage Info ---
            Text("Storage Overview", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            StorageInfoSection(context)

            Spacer(Modifier.height(24.dp))

            // --- System Info ---
            Text("System & Device Information", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            SystemInfoCard(context)

            Spacer(Modifier.height(24.dp))

            // --- Updates ---
            Text("Updates", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Version ${Updater.currentVersion(context)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = !busy,
                onClick = {
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
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(if (busy) "Checking..." else "Check for updates")
            }

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
private fun StorageInfoSection(context: Context) {
    val internalPath = Environment.getExternalStorageDirectory().path
    val internalStat = remember { StatFs(internalPath) }
    val intTotal = internalStat.blockCountLong * internalStat.blockSizeLong
    val intFree = internalStat.availableBlocksLong * internalStat.blockSizeLong

    StorageCard(
        title = "Internal Storage",
        icon = Icons.Default.SdCard,
        totalBytes = intTotal,
        freeBytes = intFree
    )

    val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
    if (externalDirs.size > 1 && externalDirs[1] != null) {
        val sdCardFile = externalDirs[1]
        val sdStat = remember { StatFs(sdCardFile.path) }
        val sdTotal = sdStat.blockCountLong * sdStat.blockSizeLong
        val sdFree = sdStat.availableBlocksLong * sdStat.blockSizeLong

        Spacer(Modifier.height(8.dp))
        StorageCard(
            title = "SD Card",
            icon = Icons.Default.Memory,
            totalBytes = sdTotal,
            freeBytes = sdFree
        )
    }
}

@Composable
private fun StorageCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    totalBytes: Long,
    freeBytes: Long
) {
    val usedBytes = totalBytes - freeBytes
    val usedGb = "%.1f".format(usedBytes / (1024f * 1024f * 1024f))
    val freeGb = "%.1f".format(freeBytes / (1024f * 1024f * 1024f))
    val totalGb = "%.1f".format(totalBytes / (1024f * 1024f * 1024f))
    val progress = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text("$usedGb GB / $totalGb GB", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(4.dp))
                Text("$freeGb GB free", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SystemInfoCard(context: Context) {
    val activityManager = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager }
    val memoryInfo = remember { ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) } }

    val totalRamGb = "%.1f".format(memoryInfo.totalMem / (1024f * 1024f * 1024f))
    val availRamGb = "%.1f".format(memoryInfo.availMem / (1024f * 1024f * 1024f))
    val kernelVersion = remember { System.getProperty("os.version") ?: "Unknown" }
    val manufacturer = remember { Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoRow(icon = Icons.Default.PhoneAndroid, label = "Device Model", value = "$manufacturer ${Build.MODEL}")
            InfoRow(icon = Icons.Default.Android, label = "Android Version", value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            InfoRow(icon = Icons.Default.DeveloperBoard, label = "RAM (Available / Total)", value = "$availRamGb GB / $totalRamGb GB")
            InfoRow(icon = Icons.Default.Terminal, label = "Kernel Version", value = kernelVersion)
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun ThemeOption(spec: ThemeSpec, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val isRgbOption = spec.id == "rgb"
    val animatedRgbColor = if (isRgbOption) rememberRgbColor() else spec.primary

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(shape)
                .background(
                    if (isRgbOption) SolidColor(spec.background)
                    else spec.gradient?.let { Brush.linearGradient(it) } ?: SolidColor(spec.background)
                )
                .border(
                    width = 1.dp,
                    brush = if (isRgbOption) Brush.sweepGradient(
                        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                    ) else SolidColor(MaterialTheme.colorScheme.outline),
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(animatedRgbColor)
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(spec.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
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
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
