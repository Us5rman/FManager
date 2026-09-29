package fmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fmanager.model.ArchiveOps
import fmanager.model.FileItem

/**
 * Bottom sheet shown when a file or folder is long-pressed.
 * Kept in its own file so FileExplorerScreen.kt stays focused on the list/grid itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileContextMenuSheet(
    item: FileItem,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Column(Modifier.verticalScroll(rememberScrollState())) {
                MenuRow(Icons.Default.ContentCopy, "Copy", onClick = onCopy)
                MenuRow(Icons.Default.ContentCut, "Cut / Move", onClick = onMove)
                MenuRow(Icons.Default.Edit, "Rename", onClick = onRename)

                if (ArchiveOps.canExtract(item.name)) {
                    MenuRow(Icons.Default.Unarchive, "Extract", onClick = onExtract)
                } else {
                    MenuRow(Icons.Default.Archive, "Compress", onClick = onCompress)
                }

                MenuRow(Icons.Default.Info, "Properties", onClick = onProperties)

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                MenuRow(
                    icon = Icons.Default.Delete,
                    label = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label, color = tint) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
