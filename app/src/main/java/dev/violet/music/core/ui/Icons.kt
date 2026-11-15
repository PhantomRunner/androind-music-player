package dev.violet.music.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small original geometric icon set, kept local instead of adding an icon-pack dependency. */
object VioletIcons {
    val Home = icon("Home") {
        moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f); lineTo(21f, 21f)
        lineTo(14f, 21f); lineTo(14f, 14f); lineTo(10f, 14f); lineTo(10f, 21f)
        lineTo(3f, 21f); close()
    }
    val Search = icon("Search") {
        moveTo(10f, 2f); arcToRelative(8f, 8f, 0f, true, false, 4.9f, 14.3f)
        lineTo(21f, 22f); lineTo(22f, 21f); lineTo(16.3f, 14.9f)
        arcToRelative(8f, 8f, 0f, false, false, -6.3f, -12.9f); close()
        moveTo(10f, 4f); arcToRelative(6f, 6f, 0f, true, true, 0f, 12f)
        arcToRelative(6f, 6f, 0f, true, true, 0f, -12f); close()
    }
    val Library = icon("Library") {
        moveTo(3f, 3f); lineTo(6f, 3f); lineTo(6f, 21f); lineTo(3f, 21f); close()
        moveTo(9f, 3f); lineTo(12f, 3f); lineTo(12f, 21f); lineTo(9f, 21f); close()
        moveTo(15f, 4f); lineTo(18f, 3f); lineTo(23f, 20f); lineTo(20f, 21f); close()
    }
    val Settings = icon("Settings") {
        moveTo(10f, 2f); lineTo(14f, 2f); lineTo(15f, 5f); lineTo(18f, 6f)
        lineTo(21f, 6f); lineTo(23f, 10f); lineTo(20f, 12f); lineTo(20f, 15f)
        lineTo(21f, 18f); lineTo(17f, 21f); lineTo(14f, 20f); lineTo(11f, 20f)
        lineTo(8f, 22f); lineTo(4f, 19f); lineTo(5f, 16f); lineTo(4f, 13f)
        lineTo(1f, 11f); lineTo(3f, 7f); lineTo(6f, 7f); lineTo(9f, 5f); close()
        moveTo(12f, 8f); arcToRelative(4f, 4f, 0f, true, true, 0f, 8f)
        arcToRelative(4f, 4f, 0f, true, true, 0f, -8f); close()
    }
    val Music = icon("Music") {
        moveTo(10f, 3f); lineTo(20f, 1f); lineTo(20f, 17f)
        arcToRelative(4f, 3f, 0f, true, true, -2f, -2.6f)
        lineTo(18f, 6f); lineTo(12f, 7.2f); lineTo(12f, 19f)
        arcToRelative(4f, 3f, 0f, true, true, -2f, -2.6f); close()
    }
    val Back = icon("Back") {
        moveTo(12f, 3f); lineTo(3f, 12f); lineTo(12f, 21f); lineTo(13.5f, 19.5f)
        lineTo(7f, 13f); lineTo(22f, 13f); lineTo(22f, 11f); lineTo(7f, 11f)
        lineTo(13.5f, 4.5f); close()
    }
    val Close = icon("Close") {
        moveTo(5f, 3f); lineTo(12f, 10f); lineTo(19f, 3f); lineTo(21f, 5f)
        lineTo(14f, 12f); lineTo(21f, 19f); lineTo(19f, 21f); lineTo(12f, 14f)
        lineTo(5f, 21f); lineTo(3f, 19f); lineTo(10f, 12f); lineTo(3f, 5f); close()
    }

    private fun icon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd, pathBuilder = block)
        }.build()
}
