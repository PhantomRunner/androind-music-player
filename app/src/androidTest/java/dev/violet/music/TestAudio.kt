package dev.violet.music

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** Only creates disposable, clearly named fixtures; never modifies existing media. */
@androidx.annotation.RequiresApi(29)
object TestAudio {
    fun create(context: Context, seconds: Int = 30): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "Violet test fixture ${UUID.randomUUID()}.wav")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/VioletTestFixtures")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values))
        try {
            val size = seconds * 16_000
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + size); put("WAVEfmt ".toByteArray())
                putInt(16); putShort(1); putShort(1); putInt(8_000); putInt(16_000)
                putShort(2); putShort(16); put("data".toByteArray()); putInt(size)
            }.array()
            requireNotNull(context.contentResolver.openOutputStream(uri)).use { stream ->
                stream.write(header)
                val silence = ByteArray(16_000)
                repeat(seconds) { stream.write(silence) }
            }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    }
}
