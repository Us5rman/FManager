package fmanager.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val url: String)

object Updater {
    private const val API =
        "https://api.github.com/repos/Us5rman/FManager/releases/latest"

    fun currentVersion(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"

    private fun build(v: String): Int =
        v.trim().removePrefix("v").substringAfterLast('.').toIntOrNull() ?: 0

    // Returns null if up to date; throws on network errors
    suspend fun check(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val c = URL(API).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        val json = JSONObject(c.inputStream.bufferedReader().readText())
        val tag = json.getString("tag_name")
        val assets = json.getJSONArray("assets")
        var url: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) {
                url = a.getString("browser_download_url")
                break
            }
        }
        if (url != null && build(tag) > build(currentVersion(ctx))) UpdateInfo(tag, url)
        else null
    }

    suspend fun download(ctx: Context, url: String): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "FManager.apk")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        out
    }

    // Returns false if the user first has to allow installs from this app
    fun install(ctx: Context, apk: File): Boolean {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
