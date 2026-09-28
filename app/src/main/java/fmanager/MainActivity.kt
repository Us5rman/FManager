package fmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import fmanager.ui.FileExplorerScreen
import fmanager.ui.FileManagerViewModel
import fmanager.ui.theme.FManagerTheme
import fmanager.ui.theme.ThemeSettings

class MainActivity : ComponentActivity() {
    private val viewModel: FileManagerViewModel by viewModels()
    private var hasAccess by mutableStateOf(false)

    private val legacyPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshAccess() }

    private fun applyBestRefreshRate() {
        val d = if (Build.VERSION.SDK_INT >= 30) display
                else @Suppress("DEPRECATION") windowManager.defaultDisplay
        d ?: return
        val cur = d.mode
        val best = d.supportedModes
            .filter {
                it.physicalWidth == cur.physicalWidth &&
                it.physicalHeight == cur.physicalHeight
            }
            .maxByOrNull { it.refreshRate } ?: return
        val lp = window.attributes
        lp.preferredDisplayModeId = best.modeId
        window.attributes = lp
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyBestRefreshRate()
        ThemeSettings.init(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        setContent {
            FManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    if (hasAccess) {
                        FileExplorerScreen(viewModel)
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("FManager needs access to your files.")
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { requestAccess() }) {
                                Text("Grant access")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAccess()
    }

    private fun refreshAccess() {
        hasAccess = if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        if (hasAccess && viewModel.fileList.value.isEmpty()) {
            viewModel.loadDirectory(viewModel.currentPath.value)
        }
    }

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            legacyPermission.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
}
