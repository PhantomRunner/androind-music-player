package dev.violet.music.core.data

import android.content.Context
import android.graphics.Color
import android.util.LruCache
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ArtworkColorRepository
@Inject
constructor(@param:ApplicationContext private val context: Context) {
    private val cache = LruCache<String, Int>(128)

    suspend fun color(uri: String?, revision: Long = 0): Int =
        withContext(Dispatchers.IO) {
            val fallback = 0xFF101116.toInt()
            if (uri == null) return@withContext fallback
            val key = "$uri:$revision"
            cache.get(key)?.let {
                return@withContext it
            }
            val result =
                context.imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(uri)
                        .size(64)
                        .allowHardware(false)
                        .memoryCacheKey("palette:$key")
                        .build()
                )
            if (result !is SuccessResult) return@withContext fallback
            val bitmap = result.drawable.toBitmap(64, 64)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val value =
                withContext(Dispatchers.Default) {
                    val counts = HashMap<Int, Int>()
                    for (pixel in pixels) if (Color.alpha(pixel) > 128) {
                        val bucket =
                            Color.rgb(
                                Color.red(pixel) and 0xF0,
                                Color.green(pixel) and 0xF0,
                                Color.blue(pixel) and 0xF0,
                            )
                        counts[bucket] = (counts[bucket] ?: 0) + 1
                    }
                    val representative = counts.maxByOrNull { it.value }?.key ?: fallback
                    var tint = ColorUtils.blendARGB(fallback, representative, 0.28f)
                    repeat(8) {
                        if (
                            ColorUtils.calculateContrast(0xFFA9ABBA.toInt(), tint) < 4.5 ||
                                ColorUtils.calculateContrast(0xFFA78BFA.toInt(), tint) < 4.5
                        )
                            tint = ColorUtils.blendARGB(tint, fallback, 0.2f)
                    }
                    tint
                }
            cache.put(key, value)
            value
        }
}
