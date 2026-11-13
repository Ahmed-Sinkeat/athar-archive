package com.atharchive.feature.reader

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReaderImageEntryPoint {
    fun http(): OkHttpClient
}

/** Pictures come through the content client (its Sudan DNS fallback) plus a disk cache, so a seen picture opens offline. */
private object ReaderImages {
    @Volatile private var client: OkHttpClient? = null

    // 64 MB LRU on disk, no memory cache (a bitmap lives while its block is on screen); raise if articles grow image-heavy.
    private fun client(context: Context): OkHttpClient = client ?: synchronized(this) {
        client ?: EntryPoints.get(context.applicationContext, ReaderImageEntryPoint::class.java).http().newBuilder()
            .cache(Cache(File(context.cacheDir, "reader-images"), 64L * 1024 * 1024))
            .build().also { client = it }
    }

    /** Decoded no wider than [maxWidth] pixels; null when it cannot be fetched or decoded. */
    suspend fun load(context: Context, src: String, maxWidth: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            client(context).newCall(Request.Builder().url(src).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body.bytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
            }
        }.getOrNull()
    }
}

/** A picture in the text, full width; tap opens it full screen, where it pinches to zoom. */
@Composable
internal fun ReaderImage(src: String, colors: ReaderColors, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var attempt by remember(src) { mutableIntStateOf(0) }
    var failed by remember(src) { mutableStateOf(false) }
    var zoomed by remember(src) { mutableStateOf(false) }
    val image by produceState<ImageBitmap?>(null, src, attempt) {
        failed = false
        value = ReaderImages.load(context, src, context.resources.displayMetrics.widthPixels)
        failed = value == null
    }
    val bitmap = image
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (bitmap == null) 160.dp else 0.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.divider.copy(alpha = 0.25f))
            .clickable(enabled = bitmap != null || failed) { if (bitmap != null) zoomed = true else attempt++ },
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(bitmap, contentDescription = "صورة من المنشور", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            failed -> Text(
                "تعذّر تحميل الصورة · اضغط لإعادة المحاولة",
                modifier = Modifier.padding(16.dp),
                color = colors.secondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
    if (zoomed && bitmap != null) Dialog(onDismissRequest = { zoomed = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onTap = { zoomed = false }) },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap,
                contentDescription = "صورة من المنشور",
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}
