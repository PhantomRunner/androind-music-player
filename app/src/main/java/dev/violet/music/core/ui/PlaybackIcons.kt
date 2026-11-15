package dev.violet.music.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object PlaybackIcons {
    val Play = icon("Play") { moveTo(7f, 3f); lineTo(21f, 12f); lineTo(7f, 21f); close() }
    val Pause = icon("Pause") {
        moveTo(5f, 3f); lineTo(10f, 3f); lineTo(10f, 21f); lineTo(5f, 21f); close()
        moveTo(14f, 3f); lineTo(19f, 3f); lineTo(19f, 21f); lineTo(14f, 21f); close()
    }
    val Next = icon("Next") {
        moveTo(4f, 3f); lineTo(17f, 12f); lineTo(4f, 21f); close()
        moveTo(18f, 3f); lineTo(21f, 3f); lineTo(21f, 21f); lineTo(18f, 21f); close()
    }
    val Previous = icon("Previous") {
        moveTo(20f, 3f); lineTo(7f, 12f); lineTo(20f, 21f); close()
        moveTo(3f, 3f); lineTo(6f, 3f); lineTo(6f, 21f); lineTo(3f, 21f); close()
    }
    val Shuffle = icon("Shuffle") {
        moveTo(2f, 5f); lineTo(5f, 5f); lineTo(18f, 18f); lineTo(18f, 14f)
        lineTo(23f, 19f); lineTo(18f, 24f); lineTo(18f, 20f); lineTo(4f, 7f); lineTo(2f, 7f); close()
        moveTo(2f, 19f); lineTo(5f, 19f); lineTo(10f, 14f); lineTo(8.5f, 12.5f); lineTo(4f, 17f); lineTo(2f, 17f); close()
        moveTo(13f, 9f); lineTo(18f, 4f); lineTo(18f, 0f); lineTo(23f, 5f); lineTo(18f, 10f); lineTo(18f, 6f); lineTo(14.5f, 10.5f); close()
    }
    val Repeat = icon("Repeat") {
        moveTo(4f, 4f); lineTo(17f, 4f); lineTo(17f, 1f); lineTo(23f, 5f); lineTo(17f, 9f)
        lineTo(17f, 6f); lineTo(6f, 6f); lineTo(6f, 12f); lineTo(4f, 12f); close()
        moveTo(20f, 20f); lineTo(7f, 20f); lineTo(7f, 23f); lineTo(1f, 19f); lineTo(7f, 15f)
        lineTo(7f, 18f); lineTo(18f, 18f); lineTo(18f, 12f); lineTo(20f, 12f); close()
    }
    val Queue = icon("Queue") {
        moveTo(2f, 4f); lineTo(22f, 4f); lineTo(22f, 6f); lineTo(2f, 6f); close()
        moveTo(2f, 10f); lineTo(22f, 10f); lineTo(22f, 12f); lineTo(2f, 12f); close()
        moveTo(2f, 16f); lineTo(13f, 16f); lineTo(13f, 18f); lineTo(2f, 18f); close()
        moveTo(17f, 15f); lineTo(23f, 19f); lineTo(17f, 23f); close()
    }
    val More = icon("More") {
        for (y in listOf(3f, 10f, 17f)) {
            moveTo(10f, y); lineTo(14f, y); lineTo(14f, y + 4f); lineTo(10f, y + 4f); close()
        }
    }
    val Heart = icon("Heart") {
        moveTo(12f, 21f); lineTo(2f, 11f); curveTo(-3f, 3f, 7f, -2f, 12f, 5f)
        curveTo(17f, -2f, 27f, 3f, 22f, 11f); close()
    }
    private fun icon(name: String, block: PathBuilder.() -> Unit) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathBuilder = block)
    }.build()
}
