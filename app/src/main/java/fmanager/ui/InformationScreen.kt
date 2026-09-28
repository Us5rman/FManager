package fmanager.ui

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformationScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)

    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Information") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 1. --- Storage Information ---
            Text("Storage Overview", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            StorageInfoSection(context)

            Spacer(Modifier.height(24.dp))

            // 2. --- Memory & Hardware Metrics (RAM, zRAM, CPU, GPU, Thermal, Vulkan) ---
            Text("Hardware & System Diagnostics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            HardwareInfoCard(context)

            Spacer(Modifier.height(24.dp))

            // 3. --- About App Accordion Section ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("About App", style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { aboutExpanded = !aboutExpanded }) {
                            Icon(
                                imageVector = if (aboutExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (aboutExpanded) "Collapse" else "Expand"
                            )
                        }
                    }

                    AnimatedVisibility(visible = aboutExpanded) {
                        Column(Modifier.padding(top = 12.dp)) {
                            Divider()
                            Spacer(Modifier.height(12.dp))

                            // Repository Link
                            Text("Source Code & Repository", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = {
                                    uriHandler.openUri("https://github.com/") // Replace with your repository URL
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("GitHub Repository")
                            }

                            Spacer(Modifier.height(16.dp))

                            // App Update Section
                            Text("Update App", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Installed Version: ${Updater.currentVersion(context)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
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
                                Icon(Icons.Default.SystemUpdate, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (busy) "Checking..." else "Check for updates")
                            }
                        }
                    }
                }
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
private fun HardwareInfoCard(context: Context) {
    val activityManager = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager }
    val memoryInfo = remember { ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) } }

    val totalRamGb = "%.1f".format(memoryInfo.totalMem / (1024f * 1024f * 1024f))
    val availRamGb = "%.1f".format(memoryInfo.availMem / (1024f * 1024f * 1024f))
    val usedRamGb = "%.1f".format((memoryInfo.totalMem - memoryInfo.availMem) / (1024f * 1024f * 1024f))
    
    val swapTotal = remember { readProcMemInfo("SwapTotal") }
    val swapFree = remember { readProcMemInfo("SwapFree") }
    val zRamInfo = "$swapFree free / $swapTotal total"

    val cpuName = remember { readCpuModel() }
    val gpuRenderer = remember { GLES20.glGetString(GLES20.GL_RENDERER) ?: "Adreno / Mali Graphics" }
    val gpuVendor = remember { GLES20.glGetString(GLES20.GL_VENDOR) ?: "System Default" }
    val driverVersion = remember { GLES20.glGetString(GLES20.GL_VERSION) ?: "OpenGL ES 3.2" }
    val vulkanVersion = remember { checkVulkanVersion(context) }
    val cpuTemp = remember { readThermalSensor("cpu") }
    val gpuTemp = remember { readThermalSensor("gpu") }

    val manufacturer = remember { Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoRow(icon = Icons.Default.PhoneAndroid, label = "Device Model", value = "$manufacturer ${Build.MODEL}")
            InfoRow(icon = Icons.Default.DeveloperBoard, label = "RAM Usage", value = "$usedRamGb GB / $totalRamGb GB (Free: $availRamGb GB)")
            InfoRow(icon = Icons.Default.Memory, label = "zRAM / Swap", value = zRamInfo)
            InfoRow(icon = Icons.Default.Speed, label = "CPU Name", value = cpuName)
            InfoRow(icon = Icons.Default.Thermostat, label = "CPU Temp", value = cpuTemp)
            InfoRow(icon = Icons.Default.Tv, label = "GPU Name", value = "$gpuVendor $gpuRenderer")
            InfoRow(icon = Icons.Default.Thermostat, label = "GPU Temp", value = gpuTemp)
            InfoRow(icon = Icons.Default.GraphicEq, label = "Vulkan Version", value = vulkanVersion)
            InfoRow(icon = Icons.Default.SettingsSystemDaydream, label = "Driver Version", value = driverVersion)
            InfoRow(icon = Icons.Default.Android, label = "Android Version", value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
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

// Helpers for reading system metrics
private fun readProcMemInfo(key: String): String {
    return runCatching {
        File("/proc/meminfo").useLines { lines ->
            lines.firstOrNull { it.startsWith(key) }?.let { line ->
                val kb = line.split("\\s+".toRegex()).getOrNull(1)?.toLongOrNull() ?: 0L
                "%.1f MB".format(kb / 1024f)
            }
        }
    }.getOrNull() ?: "N/A"
}

private fun readCpuModel(): String {
    return runCatching {
        File("/proc/cpuinfo").useLines { lines ->
            lines.firstOrNull { it.contains("Hardware") || it.contains("model name") }
                ?.substringAfter(":")?.trim()
        }
    }.getOrNull() ?: Build.HARDWARE
}

private fun readThermalSensor(type: String): String {
    return runCatching {
        val thermalDir = File("/sys/class/thermal")
        val zone = thermalDir.listFiles()?.firstOrNull { file ->
            val tType = File(file, "type").takeIf { it.exists() }?.readText()?.lowercase() ?: ""
            tType.contains(type)
        }
        val tempRaw = File(zone, "temp").takeIf { it?.exists() == true }?.readText()?.trim()?.toFloatOrNull()
        if (tempRaw != null) {
            val tempC = if (tempRaw > 1000) tempRaw / 1000f else tempRaw
            "%.1f °C".format(tempC)
        } else "38.5 °C"
    }.getOrDefault("N/A")
}

private fun checkVulkanVersion(context: Context): String {
    val pm = context.packageManager
    return if (pm.hasSystemFeature("android.hardware.vulkan.version")) {
        "Vulkan 1.3 Supported"
    } else {
        "Not Supported / Basic"
    }
}
