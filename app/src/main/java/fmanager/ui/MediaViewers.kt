package fmanager.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import fmanager.model.FileItem
import fmanager.model.OpenKind
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

data class ViewerTarget(val item: FileItem, val kind: OpenKind)

private fun fmt(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun trackLabel(f: Format, n: Int): String {
    val lang = f.language?.takeIf { it != "und" }?.let { Locale(it).displayLanguage }
    return f.label ?: lang ?: "Track ${n + 1}"
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MediaPlayerScreen(item: FileItem, isVideo: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    BackHandler(onBack = onClose)

    val exo = remember(item.path) {
        ExoPlayer.Builder(
            context,
            DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ).build()
    }

    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var speed by remember { mutableStateOf(1f) }
    var fill by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var tracksDialog by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var seekPos by remember { mutableStateOf(0f) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var error by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }

    // Load file plus any same-name subtitle files next to it (.srt .vtt .ass .ssa)
    LaunchedEffect(exo) {
        val f = File(item.path)
        val subs = listOf("srt", "vtt", "ass", "ssa")
            .mapNotNull { ext -> File(f.parentFile, f.nameWithoutExtension + "." + ext).takeIf { it.exists() } }
            .mapIndexed { i, sf ->
                MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(sf))
                    .setMimeType(
                        when (sf.extension.lowercase()) {
                            "vtt" -> MimeTypes.TEXT_VTT
                            "ass", "ssa" -> MimeTypes.TEXT_SSA
                            else -> MimeTypes.APPLICATION_SUBRIP
                        }
                    )
                    .setLanguage("und")
                    .setSelectionFlags(if (i == 0) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            }
        exo.setMediaItem(
            MediaItem.Builder()
                .setUri(Uri.fromFile(f))
                .setSubtitleConfigurations(subs)
                .build()
        )
        exo.prepare()
        exo.playWhenReady = true
    }

    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onPlayerError(e: PlaybackException) { error = e.errorCodeName }
        }
        exo.addListener(l)
        onDispose {
            exo.removeListener(l)
            exo.release()
        }
    }

    // Keep screen on while playing, pause when the app goes to background
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exo) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_PAUSE) exo.pause()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(exo) {
        while (true) {
            if (!dragging) position = exo.currentPosition
            duration = exo.duration.coerceAtLeast(0L)
            delay(250)
        }
    }
    LaunchedEffect(showControls, isPlaying, dragging) {
        if (showControls && isPlaying && !dragging) {
            delay(3500)
            showControls = false
        }
    }
    LaunchedEffect(hint) {
        if (hint != null) { delay(700); hint = null }
    }

    fun seekBy(delta: Long) {
        val max = if (exo.duration > 0) exo.duration else Long.MAX_VALUE
        exo.seekTo((exo.currentPosition + delta).coerceIn(0L, max))
        hint = if (delta < 0) "-10s" else "+10s"
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isVideo) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setPlayer(exo)
                    }
                },
                update = {
                    it.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(120.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    item.name, color = Color.White, maxLines = 3,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
            }
        }

        // Tap to show/hide controls, double-tap left/right to seek 10s
        Row(Modifier.fillMaxSize()) {
            listOf(-10_000L, 10_000L).forEach { delta ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().pointerInput(delta) {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = { seekBy(delta) }
                        )
                    }
                )
            }
        }

        hint?.let {
            Text(
                it, color = Color.White, style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        error?.let {
            Text(
                "Can't play this file ($it).\nTry Open as... > Open with other app.",
                color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }

        if (showControls) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                    Text(
                        item.name, color = Color.White, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(
                    Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt(if (dragging) seekPos.toLong() else position), color = Color.White)
                        Slider(
                            value = if (dragging) seekPos else position.toFloat(),
                            onValueChange = { dragging = true; seekPos = it },
                            onValueChangeFinished = {
                                exo.seekTo(seekPos.toLong())
                                position = seekPos.toLong()
                                dragging = false
                            },
                            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text(fmt(duration), color = Color.White)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { speedDialog = true }) {
                            Text("${speed}x", color = Color.White)
                        }
                        IconButton(onClick = { seekBy(-10_000L) }) {
                            Icon(Icons.Default.Replay10, "Back 10 seconds", tint = Color.White)
                        }
                        IconButton(onClick = {
                            if (isPlaying) exo.pause() else exo.play()
                        }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                "Play or pause", tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        IconButton(onClick = { seekBy(10_000L) }) {
                            Icon(Icons.Default.Forward10, "Forward 10 seconds", tint = Color.White)
                        }
                        IconButton(onClick = { tracksDialog = true }) {
                            Icon(Icons.Default.Subtitles, "Audio and subtitles", tint = Color.White)
                        }
                        if (isVideo) {
                            IconButton(onClick = { fill = !fill }) {
                                Icon(Icons.Default.AspectRatio, "Fit or fill", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    if (speedDialog) {
        AlertDialog(
            onDismissRequest = { speedDialog = false },
            title = { Text("Playback speed") },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f).forEach { s ->
                        TextButton(onClick = {
                            speed = s
                            exo.setPlaybackSpeed(s)
                            speedDialog = false
                        }) { Text(if (s == speed) "${s}x  (current)" else "${s}x") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { speedDialog = false }) { Text("Close") } }
        )
    }

    if (tracksDialog) {
        val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val textOff = exo.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        AlertDialog(
            onDismissRequest = { tracksDialog = false },
            title = { Text("Audio and subtitles") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Audio", style = MaterialTheme.typography.titleSmall)
                    if (audio.isEmpty()) Text("No audio tracks")
                    var n = 0
                    audio.forEach { g ->
                        for (i in 0 until g.length) {
                            if (!g.isTrackSupported(i)) continue
                            val label = trackLabel(g.getTrackFormat(i), n++)
                            TextButton(onClick = {
                                exo.trackSelectionParameters = exo.trackSelectionParameters
                                    .buildUpon()
                                    .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                                    .build()
                            }) { Text(if (g.isTrackSelected(i)) "✓ $label" else label) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Subtitles", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = {
                        exo.trackSelectionParameters = exo.trackSelectionParameters
                            .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                        tracksDialog = false
                    }) { Text(if (textOff) "✓ Off" else "Off") }
                    var m = 0
                    text.forEach { g ->
                        for (i in 0 until g.length) {
                            if (!g.isTrackSupported(i)) continue
                            val label = trackLabel(g.getTrackFormat(i), m++)
                            TextButton(onClick = {
                                exo.trackSelectionParameters = exo.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                                    .build()
                                tracksDialog = false
                            }) { Text(if (g.isTrackSelected(i) && !textOff) "✓ $label" else label) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { tracksDialog = false }) { Text("Close") } }
        )
    }
}

@Composable
fun ImageViewerScreen(item: FileItem, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)

    val loader = remember {
        ImageLoader.Builder(context).components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
            else add(GifDecoder.Factory())
            add(SvgDecoder.Factory())
        }.build()
    }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = File(item.path),
            imageLoader = loader,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    })
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        val maxX = size.width * (scale - 1f) / 2f
                        val maxY = size.height * (scale - 1f) / 2f
                        offset = if (scale <= 1f) Offset.Zero else Offset(
                            (offset.x + pan.x).coerceIn(-maxX, maxX),
                            (offset.y + pan.y).coerceIn(-maxY, maxY)
                        )
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.4f)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                item.name, color = Color.White, maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
