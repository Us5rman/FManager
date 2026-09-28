package fmanager.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import fmanager.model.FileItem
import fmanager.model.OpenKind
import java.io.File

object AppImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        val ctx = context.applicationContext
        return instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(ctx)
                .components {
                    if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                    else add(GifDecoder.Factory())
                    add(SvgDecoder.Factory())
                    add(VideoFrameDecoder.Factory())
                }
                .memoryCache { MemoryCache.Builder(ctx).maxSizePercent(0.15).build() }
                .crossfade(false)
                .build()
                .also { instance = it }
        }
    }
}

@Composable
fun FileThumb(item: FileItem, kind: OpenKind, size: Dp = 44.dp) {
    val context = LocalContext.current
    val request = remember(item.path, item.lastModified) {
        ImageRequest.Builder(context)
            .data(File(item.path))
            .size(160)
            .videoFrameMillis(1000)
            .build()
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        // Shown until the thumbnail loads (or if it can't be made)
        Icon(
            if (kind == OpenKind.VIDEO) Icons.Default.Movie else Icons.Default.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        AsyncImage(
            model = request,
            imageLoader = AppImageLoader.get(context),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size)
        )
        if (kind == OpenKind.VIDEO) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            )
        }
    }
}
