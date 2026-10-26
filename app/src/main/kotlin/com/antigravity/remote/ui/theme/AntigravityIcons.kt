package com.antigravity.remote.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom vector icon set so zero emojis are used as structural UI icons.
 */
object AntigravityIcons {

    val OrbitDelta: ImageVector by lazy {
        ImageVector.Builder(
            name = "OrbitDelta",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                lineTo(19.5f, 18.5f)
                lineTo(14.8f, 18.5f)
                lineTo(12f, 12.2f)
                lineTo(9.2f, 18.5f)
                lineTo(4.5f, 18.5f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 16.5f)
                arcToRelative(1.7f, 1.7f, 0f, true, true, 0f, 3.4f)
                arcToRelative(1.7f, 1.7f, 0f, true, true, 0f, -3.4f)
                close()
            }
        }.build()
    }

    val NightCrescent: ImageVector by lazy {
        ImageVector.Builder(
            name = "NightCrescent",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20.5f, 14.5f)
                curveTo(19.2f, 15.1f, 17.7f, 15.4f, 16.2f, 15.4f)
                curveTo(10.9f, 15.4f, 6.6f, 11.1f, 6.6f, 5.8f)
                curveTo(6.6f, 4.7f, 6.8f, 3.6f, 7.2f, 2.6f)
                curveTo(4.1f, 4.1f, 2f, 7.3f, 2f, 11f)
                curveTo(2f, 16.5f, 6.5f, 21f, 12f, 21f)
                curveTo(16.1f, 21f, 19.6f, 18.3f, 20.5f, 14.5f)
                close()
            }
        }.build()
    }

    val ThoughtLamp: ImageVector by lazy {
        ImageVector.Builder(
            name = "ThoughtLamp",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                lineTo(13.9f, 8.6f)
                lineTo(19.5f, 10.5f)
                lineTo(13.9f, 12.4f)
                lineTo(12f, 18f)
                lineTo(10.1f, 12.4f)
                lineTo(4.5f, 10.5f)
                lineTo(10.1f, 8.6f)
                close()
            }
        }.build()
    }

    val TerminalBox: ImageVector by lazy {
        ImageVector.Builder(
            name = "TerminalBox",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 5f)
                lineTo(20f, 5f)
                lineTo(20f, 19f)
                lineTo(4f, 19f)
                close()
                moveTo(7.5f, 9.5f)
                lineTo(10.5f, 12f)
                lineTo(7.5f, 14.5f)
                moveTo(12.5f, 14.5f)
                lineTo(16.5f, 14.5f)
            }
        }.build()
    }

    val PlayTriangle: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayTriangle",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(7f, 5.5f)
                lineTo(18.5f, 12f)
                lineTo(7f, 18.5f)
                close()
            }
        }.build()
    }

    val StopSquare: ImageVector by lazy {
        ImageVector.Builder(
            name = "StopSquare",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(6.5f, 6.5f)
                lineTo(17.5f, 6.5f)
                lineTo(17.5f, 17.5f)
                lineTo(6.5f, 17.5f)
                close()
            }
        }.build()
    }

    val SendArrow: ImageVector by lazy {
        ImageVector.Builder(
            name = "SendArrow",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 19f)
                lineTo(12f, 5f)
                moveTo(6f, 11f)
                lineTo(12f, 5f)
                lineTo(18f, 11f)
            }
        }.build()
    }

    val CopySheet: ImageVector by lazy {
        ImageVector.Builder(
            name = "CopySheet",
            defaultWidth = 18.dp,
            defaultHeight = 18.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 9f)
                lineTo(19f, 9f)
                lineTo(19f, 19f)
                lineTo(9f, 19f)
                close()
                moveTo(5f, 15f)
                lineTo(5f, 5f)
                lineTo(15f, 5f)
            }
        }.build()
    }

    val CheckMark: ImageVector by lazy {
        ImageVector.Builder(
            name = "CheckMark",
            defaultWidth = 18.dp,
            defaultHeight = 18.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 12.5f)
                lineTo(9.5f, 17f)
                lineTo(19f, 7.5f)
            }
        }.build()
    }

    val LinkBridge: ImageVector by lazy {
        ImageVector.Builder(
            name = "LinkBridge",
            defaultWidth = 18.dp,
            defaultHeight = 18.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 7f)
                lineTo(6f, 7f)
                curveTo(3.8f, 7f, 2f, 8.8f, 2f, 11f)
                curveTo(2f, 13.2f, 3.8f, 15f, 6f, 15f)
                lineTo(9f, 15f)
                moveTo(15f, 7f)
                lineTo(18f, 7f)
                curveTo(20.2f, 7f, 22f, 8.8f, 22f, 11f)
                curveTo(22f, 13.2f, 20.2f, 15f, 18f, 15f)
                lineTo(15f, 15f)
                moveTo(8f, 11f)
                lineTo(16f, 11f)
            }
        }.build()
    }

    val ChevronDown: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronDown",
            defaultWidth = 18.dp,
            defaultHeight = 18.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 9f)
                lineTo(12f, 15f)
                lineTo(18f, 9f)
            }
        }.build()
    }

    val ChevronUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronUp",
            defaultWidth = 18.dp,
            defaultHeight = 18.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(18f, 15f)
                lineTo(12f, 9f)
                lineTo(6f, 15f)
            }
        }.build()
    }

    val WarningTriangle: ImageVector by lazy {
        ImageVector.Builder(
            name = "WarningTriangle",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 4f)
                lineTo(21f, 19.5f)
                lineTo(3f, 19.5f)
                close()
                moveTo(12f, 10f)
                lineTo(12f, 14f)
                moveTo(12f, 17f)
                lineTo(12.02f, 17f)
            }
        }.build()
    }

    val RefreshArc: ImageVector by lazy {
        ImageVector.Builder(
            name = "RefreshArc",
            defaultWidth = 18.dp,
            defaultHeight = 18.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 11f)
                curveTo(19.5f, 6.5f, 15.6f, 3.5f, 11.2f, 4f)
                curveTo(6.8f, 4.5f, 3.5f, 8.2f, 4f, 12.6f)
                curveTo(4.5f, 17f, 8.4f, 20f, 12.8f, 19.5f)
                curveTo(16.1f, 19.1f, 18.8f, 16.9f, 19.7f, 14f)
                moveTo(20f, 6f)
                lineTo(20f, 11f)
                lineTo(15f, 11f)
            }
        }.build()
    }
}
