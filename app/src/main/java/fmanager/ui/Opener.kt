package fmanager.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import fmanager.model.FileItem
import fmanager.model.OpenKind
import java.io.File

fun openFile(context: Context, viewModel: FileManagerViewModel, item: FileItem, kind: OpenKind) {
    when (kind) {
        OpenKind.TEXT -> viewModel.openEditor(item)
        OpenKind.VIDEO, OpenKind.AUDIO, OpenKind.IMAGE -> viewModel.openViewer(item, kind)
        OpenKind.ANY -> {
            val ext = item.name.substringAfterLast('.', "").lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", File(item.path)
            )
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "No app can open this", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
