package dev.boreaslab.boreas.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Custom icon family on one 24-unit grid and stroke; every icon accompanies text. */
private const val WEIGHT = 2f

private fun icon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = WEIGHT,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object BoreasIcons {

    val Shield = icon(
        "Shield",
        "M12 3L20 6.2V11.2C20 16 16.6 19.6 12 21C7.4 19.6 4 16 4 11.2V6.2Z",
    )

    val Activity = icon(
        "Activity",
        "M3 12H7L10 5L14 19L17 12H21",
    )

    val Policy = icon(
        "Policy",
        "M4 5H20L14 12.2V19L10 21V12.2Z",
    )

    val Settings = icon(
        "Settings",
        "M4 8H20",
        "M4 16H20",
        "M11.5 8A2.5 2.5 0 1 1 6.5 8A2.5 2.5 0 1 1 11.5 8",
        "M17.5 16A2.5 2.5 0 1 1 12.5 16A2.5 2.5 0 1 1 17.5 16",
    )

    val ChevronRight = icon(
        "ChevronRight",
        "M9.5 5L16.5 12L9.5 19",
    )

    val ArrowLeft = icon(
        "ArrowLeft",
        "M19 12H5",
        "M11 6L5 12L11 18",
    )

    val AlertTriangle = icon(
        "AlertTriangle",
        "M12 4L21 19.5H3Z",
        "M12 10.5V14",
        "M12 17.2V17.3",
    )

    val AlertCircle = icon(
        "AlertCircle",
        "M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12",
        "M12 7.5V13",
        "M12 16.3V16.4",
    )

    val Certificate = icon(
        "Certificate",
        "M18 9A6 6 0 1 1 6 9A6 6 0 1 1 18 9",
        "M8.8 14.2L7.5 21L12 18.8L16.5 21L15.2 14.2",
    )

    val Apps = icon(
        "Apps",
        "M4.5 4.5H10V10H4.5Z",
        "M14 4.5H19.5V10H14Z",
        "M4.5 14H10V19.5H4.5Z",
        "M14 14H19.5V19.5H14Z",
    )

    val Check = icon(
        "Check",
        "M4.5 12.5L9.5 17.5L19.5 6.5",
    )

    val Search = icon(
        "Search",
        "M18 10.5A7 7 0 1 1 4 10.5A7 7 0 1 1 18 10.5",
        "M15.6 15.6L20.5 20.5",
    )

    val Close = icon(
        "Close",
        "M6.5 6.5L17.5 17.5",
        "M17.5 6.5L6.5 17.5",
    )

    val Info = icon(
        "Info",
        "M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12",
        "M12 11V16.5",
        "M12 7.6V7.7",
    )

    val Globe = icon(
        "Globe",
        "M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12",
        "M3.3 12H20.7",
        "M12 3C15 6.2 15 17.8 12 21",
        "M12 3C9 6.2 9 17.8 12 21",
    )

    val Document = icon(
        "Document",
        "M6 3H14.5L18 6.5V21H6Z",
        "M9 12.5H15",
        "M9 16.5H15",
    )
}
