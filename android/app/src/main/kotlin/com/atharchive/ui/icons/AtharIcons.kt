package com.atharchive.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Athar's primary-navigation symbols share one 24 dp grid and one rounded
 * 1.65 dp stroke. Keeping them here avoids mixing unrelated Material icon
 * weights in the app's most persistent piece of chrome.
 */
object AtharIcons {
    private val Stroke = SolidColor(Color.Black)

    /** Same brush; solid glyphs (play, pause) fill instead of stroking. */
    private val Fill = Stroke

    val Books: ImageVector by lazy {
        icon("AtharBooks") {
            path(stroke = Stroke, strokeLineWidth = 1.65f) {
                moveTo(12f, 7.2f)
                curveTo(9.8f, 5.55f, 7.15f, 4.8f, 3.75f, 4.8f)
                lineTo(3.75f, 17.25f)
                curveTo(7.05f, 17.25f, 9.75f, 18.05f, 12f, 19.75f)
                close()
                moveTo(12f, 7.2f)
                curveTo(14.2f, 5.55f, 16.85f, 4.8f, 20.25f, 4.8f)
                lineTo(20.25f, 17.25f)
                curveTo(16.95f, 17.25f, 14.25f, 18.05f, 12f, 19.75f)
                close()
            }
        }
    }

    val Poetry: ImageVector by lazy {
        icon("AtharPoetry") {
            path(stroke = Stroke, strokeLineWidth = 1.65f) {
                moveTo(20.25f, 3.75f)
                curveTo(16.15f, 3.2f, 11.15f, 5.25f, 8.3f, 9.4f)
                curveTo(6.55f, 11.95f, 5.95f, 14.65f, 6.55f, 17.35f)
                curveTo(9.35f, 17.35f, 12.05f, 16.85f, 14.5f, 15.4f)
                curveTo(18.7f, 12.9f, 20.65f, 7.95f, 20.25f, 3.75f)
                close()
                moveTo(18.25f, 5.7f)
                lineTo(5f, 19.3f)
                moveTo(13.2f, 10.7f)
                lineTo(16.75f, 10.25f)
                moveTo(10.3f, 13.6f)
                lineTo(9.85f, 10.1f)
                moveTo(5f, 19.3f)
                lineTo(3.7f, 20.65f)
            }
        }
    }

    val Kannashah: ImageVector by lazy {
        icon("AtharKannashah") {
            path(stroke = Stroke, strokeLineWidth = 1.65f) {
                moveTo(7f, 3.75f)
                lineTo(18.5f, 3.75f)
                curveTo(19.45f, 3.75f, 20.2f, 4.5f, 20.2f, 5.45f)
                lineTo(20.2f, 19.1f)
                curveTo(20.2f, 20.05f, 19.45f, 20.8f, 18.5f, 20.8f)
                lineTo(7f, 20.8f)
                close()
                moveTo(7f, 3.75f)
                lineTo(7f, 20.8f)
                moveTo(3.8f, 7.2f)
                lineTo(8.5f, 7.2f)
                moveTo(3.8f, 12.25f)
                lineTo(8.5f, 12.25f)
                moveTo(3.8f, 17.3f)
                lineTo(8.5f, 17.3f)
                moveTo(11f, 8.1f)
                lineTo(16.8f, 8.1f)
                moveTo(11f, 12.25f)
                lineTo(16.8f, 12.25f)
                moveTo(11f, 16.4f)
                lineTo(15.1f, 16.4f)
            }
        }
    }

    val Articles: ImageVector by lazy {
        icon("AtharArticles") {
            path(stroke = Stroke, strokeLineWidth = 1.65f) {
                moveTo(6f, 3.5f)
                lineTo(15.1f, 3.5f)
                lineTo(19.2f, 7.6f)
                lineTo(19.2f, 20.5f)
                lineTo(6f, 20.5f)
                close()
                moveTo(15.1f, 3.5f)
                lineTo(15.1f, 7.6f)
                lineTo(19.2f, 7.6f)
                moveTo(9f, 11f)
                lineTo(16.2f, 11f)
                moveTo(9f, 14.5f)
                lineTo(16.2f, 14.5f)
                moveTo(9f, 18f)
                lineTo(13.7f, 18f)
            }
        }
    }

    val Issues: ImageVector by lazy {
        icon("AtharIssues") {
            path(stroke = Stroke, strokeLineWidth = 1.65f) {
                moveTo(12f, 3.7f)
                curveTo(16.58f, 3.7f, 20.3f, 7.42f, 20.3f, 12f)
                curveTo(20.3f, 16.58f, 16.58f, 20.3f, 12f, 20.3f)
                curveTo(7.42f, 20.3f, 3.7f, 16.58f, 3.7f, 12f)
                curveTo(3.7f, 7.42f, 7.42f, 3.7f, 12f, 3.7f)
                close()
                moveTo(9.55f, 9.2f)
                curveTo(9.75f, 7.9f, 10.65f, 7.15f, 12.05f, 7.15f)
                curveTo(13.6f, 7.15f, 14.65f, 8.05f, 14.65f, 9.35f)
                curveTo(14.65f, 10.35f, 14.05f, 10.95f, 12.95f, 11.55f)
                curveTo(12.25f, 11.95f, 12f, 12.5f, 12f, 13.35f)
                moveTo(12f, 16.55f)
                lineTo(12.01f, 16.55f)
            }
        }
    }

    val Search: ImageVector by lazy {
        icon("AtharSearch") {
            path(stroke = Stroke, strokeLineWidth = 1.7f) {
                moveTo(10.4f, 4f)
                curveTo(13.95f, 4f, 16.8f, 6.85f, 16.8f, 10.4f)
                curveTo(16.8f, 13.95f, 13.95f, 16.8f, 10.4f, 16.8f)
                curveTo(6.85f, 16.8f, 4f, 13.95f, 4f, 10.4f)
                curveTo(4f, 6.85f, 6.85f, 4f, 10.4f, 4f)
                close()
                moveTo(15.1f, 15.1f)
                lineTo(20.2f, 20.2f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        icon("AtharSettings") {
            path(stroke = Stroke, strokeLineWidth = 1.55f) {
                moveTo(9.55f, 3.35f)
                lineTo(14.45f, 3.35f)
                lineTo(14.9f, 5.55f)
                curveTo(15.55f, 5.8f, 16.15f, 6.15f, 16.7f, 6.6f)
                lineTo(18.8f, 5.9f)
                lineTo(21.25f, 10.1f)
                lineTo(19.6f, 11.55f)
                curveTo(19.7f, 12.25f, 19.7f, 12.95f, 19.6f, 13.65f)
                lineTo(21.25f, 15.1f)
                lineTo(18.8f, 19.3f)
                lineTo(16.7f, 18.6f)
                curveTo(16.15f, 19.05f, 15.55f, 19.4f, 14.9f, 19.65f)
                lineTo(14.45f, 21.85f)
                lineTo(9.55f, 21.85f)
                lineTo(9.1f, 19.65f)
                curveTo(8.45f, 19.4f, 7.85f, 19.05f, 7.3f, 18.6f)
                lineTo(5.2f, 19.3f)
                lineTo(2.75f, 15.1f)
                lineTo(4.4f, 13.65f)
                curveTo(4.3f, 12.95f, 4.3f, 12.25f, 4.4f, 11.55f)
                lineTo(2.75f, 10.1f)
                lineTo(5.2f, 5.9f)
                lineTo(7.3f, 6.6f)
                curveTo(7.85f, 6.15f, 8.45f, 5.8f, 9.1f, 5.55f)
                close()
                moveTo(12f, 9.2f)
                curveTo(13.88f, 9.2f, 15.4f, 10.72f, 15.4f, 12.6f)
                curveTo(15.4f, 14.48f, 13.88f, 16f, 12f, 16f)
                curveTo(10.12f, 16f, 8.6f, 14.48f, 8.6f, 12.6f)
                curveTo(8.6f, 10.72f, 10.12f, 9.2f, 12f, 9.2f)
                close()
            }
        }
    }

    val Adhkar: ImageVector by lazy {
        icon("AtharAdhkar") {
            path(stroke = Stroke, strokeLineWidth = 1.6f) {
                moveTo(12f, 8.45f)
                curveTo(13.96f, 8.45f, 15.55f, 10.04f, 15.55f, 12f)
                curveTo(15.55f, 13.96f, 13.96f, 15.55f, 12f, 15.55f)
                curveTo(10.04f, 15.55f, 8.45f, 13.96f, 8.45f, 12f)
                curveTo(8.45f, 10.04f, 10.04f, 8.45f, 12f, 8.45f)
                close()
                moveTo(12f, 2.8f)
                lineTo(12f, 5.55f)
                moveTo(12f, 18.45f)
                lineTo(12f, 21.2f)
                moveTo(2.8f, 12f)
                lineTo(5.55f, 12f)
                moveTo(18.45f, 12f)
                lineTo(21.2f, 12f)
                moveTo(5.5f, 5.5f)
                lineTo(7.45f, 7.45f)
                moveTo(16.55f, 16.55f)
                lineTo(18.5f, 18.5f)
                moveTo(18.5f, 5.5f)
                lineTo(16.55f, 7.45f)
                moveTo(7.45f, 16.55f)
                lineTo(5.5f, 18.5f)
            }
        }
    }

    val Audio: ImageVector by lazy {
        icon("AtharAudio") {
            path(stroke = Stroke, strokeLineWidth = 1.7f) {
                moveTo(4.4f, 13.2f)
                lineTo(4.4f, 11.2f)
                curveTo(4.4f, 6.95f, 7.8f, 3.55f, 12f, 3.55f)
                curveTo(16.2f, 3.55f, 19.6f, 6.95f, 19.6f, 11.2f)
                lineTo(19.6f, 13.2f)
                moveTo(4.4f, 12.4f)
                lineTo(6.1f, 12.4f)
                curveTo(6.85f, 12.4f, 7.45f, 13f, 7.45f, 13.75f)
                lineTo(7.45f, 18.8f)
                curveTo(7.45f, 19.55f, 6.85f, 20.15f, 6.1f, 20.15f)
                lineTo(4.4f, 20.15f)
                close()
                moveTo(19.6f, 12.4f)
                lineTo(17.9f, 12.4f)
                curveTo(17.15f, 12.4f, 16.55f, 13f, 16.55f, 13.75f)
                lineTo(16.55f, 18.8f)
                curveTo(16.55f, 19.55f, 17.15f, 20.15f, 17.9f, 20.15f)
                lineTo(19.6f, 20.15f)
                close()
            }
        }
    }

    val People: ImageVector by lazy {
        icon("AtharPeople") {
            path(stroke = Stroke, strokeLineWidth = 1.65f) {
                moveTo(9.2f, 4.2f)
                curveTo(11.05f, 4.2f, 12.55f, 5.7f, 12.55f, 7.55f)
                curveTo(12.55f, 9.4f, 11.05f, 10.9f, 9.2f, 10.9f)
                curveTo(7.35f, 10.9f, 5.85f, 9.4f, 5.85f, 7.55f)
                curveTo(5.85f, 5.7f, 7.35f, 4.2f, 9.2f, 4.2f)
                close()
                moveTo(3.7f, 19.3f)
                curveTo(4.15f, 15.55f, 6.05f, 13.65f, 9.2f, 13.65f)
                curveTo(11.15f, 13.65f, 12.65f, 14.35f, 13.55f, 15.75f)
                moveTo(17.2f, 13.2f)
                curveTo(19.08f, 13.2f, 20.6f, 14.72f, 20.6f, 16.6f)
                curveTo(20.6f, 18.48f, 19.08f, 20f, 17.2f, 20f)
                curveTo(15.32f, 20f, 13.8f, 18.48f, 13.8f, 16.6f)
                curveTo(13.8f, 14.72f, 15.32f, 13.2f, 17.2f, 13.2f)
                close()
                moveTo(19.65f, 19.05f)
                lineTo(21.3f, 20.7f)
            }
        }
    }

    val Archive: ImageVector by lazy {
        icon("AtharArchive") {
            path(stroke = Stroke, strokeLineWidth = 1.65f) {
                moveTo(4.2f, 8f)
                lineTo(19.8f, 8f)
                lineTo(19.8f, 20f)
                lineTo(4.2f, 20f)
                close()
                moveTo(3.25f, 4f)
                lineTo(20.75f, 4f)
                lineTo(20.75f, 8f)
                lineTo(3.25f, 8f)
                close()
                moveTo(9.2f, 12.15f)
                lineTo(14.8f, 12.15f)
            }
        }
    }

    val Play: ImageVector by lazy {
        icon("AtharPlay") {
            path(fill = Fill) {
                moveTo(8f, 5.4f)
                lineTo(19f, 12f)
                lineTo(8f, 18.6f)
                close()
            }
        }
    }

    val Pause: ImageVector by lazy {
        icon("AtharPause") {
            path(fill = Fill) {
                moveTo(7.2f, 5.5f)
                lineTo(10.2f, 5.5f)
                lineTo(10.2f, 18.5f)
                lineTo(7.2f, 18.5f)
                close()
                moveTo(13.8f, 5.5f)
                lineTo(16.8f, 5.5f)
                lineTo(16.8f, 18.5f)
                lineTo(13.8f, 18.5f)
                close()
            }
        }
    }

    val More: ImageVector by lazy {
        icon("AtharMore") {
            path(stroke = Stroke, strokeLineWidth = 2.2f) {
                moveTo(12f, 5.6f)
                lineTo(12f, 5.7f)
                moveTo(12f, 11.95f)
                lineTo(12f, 12.05f)
                moveTo(12f, 18.3f)
                lineTo(12f, 18.4f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        icon("AtharCopy") {
            path(stroke = Stroke, strokeLineWidth = 1.65f) {
                moveTo(9.2f, 8.4f)
                lineTo(18.2f, 8.4f)
                lineTo(18.2f, 20.2f)
                lineTo(9.2f, 20.2f)
                close()
                moveTo(14.8f, 8.4f)
                lineTo(14.8f, 3.8f)
                lineTo(5.8f, 3.8f)
                lineTo(5.8f, 15.6f)
                lineTo(9.2f, 15.6f)
            }
        }
    }

    val Share: ImageVector by lazy {
        icon("AtharShare") {
            path(stroke = Stroke, strokeLineWidth = 1.65f) {
                moveTo(12f, 3.6f)
                lineTo(12f, 14.6f)
                moveTo(8.1f, 7.5f)
                lineTo(12f, 3.6f)
                lineTo(15.9f, 7.5f)
                moveTo(5.6f, 12.4f)
                lineTo(5.6f, 20.4f)
                lineTo(18.4f, 20.4f)
                lineTo(18.4f, 12.4f)
            }
        }
    }

    val Recent: ImageVector by lazy {
        icon("AtharRecent") {
            path(stroke = Stroke, strokeLineWidth = 1.65f) {
                moveTo(12f, 4.4f)
                curveTo(16.2f, 4.4f, 19.6f, 7.8f, 19.6f, 12f)
                curveTo(19.6f, 16.2f, 16.2f, 19.6f, 12f, 19.6f)
                curveTo(7.8f, 19.6f, 4.4f, 16.2f, 4.4f, 12f)
                curveTo(4.4f, 7.8f, 7.8f, 4.4f, 12f, 4.4f)
                close()
                moveTo(12f, 7.6f)
                lineTo(12f, 12.3f)
                lineTo(15.4f, 14.2f)
            }
        }
    }

    val Info: ImageVector by lazy {
        icon("AtharInfo") {
            path(stroke = Stroke, strokeLineWidth = 1.65f) {
                moveTo(12f, 4.4f)
                curveTo(16.2f, 4.4f, 19.6f, 7.8f, 19.6f, 12f)
                curveTo(19.6f, 16.2f, 16.2f, 19.6f, 12f, 19.6f)
                curveTo(7.8f, 19.6f, 4.4f, 16.2f, 4.4f, 12f)
                curveTo(4.4f, 7.8f, 7.8f, 4.4f, 12f, 4.4f)
                close()
                moveTo(12f, 10.6f)
                lineTo(12f, 16.1f)
                moveTo(12f, 7.7f)
                lineTo(12f, 7.8f)
            }
        }
    }

    val ChevronDown: ImageVector by lazy {
        icon("AtharChevronDown") {
            path(stroke = Stroke, strokeLineWidth = 1.8f) {
                moveTo(6.5f, 9.5f)
                lineTo(12f, 15f)
                lineTo(17.5f, 9.5f)
            }
        }
    }

    /** Points right: in an RTL layout this is "go back". */
    val Back: ImageVector by lazy {
        icon("AtharBack") {
            path(stroke = Stroke, strokeLineWidth = 1.8f) {
                moveTo(4f, 12f)
                lineTo(19.5f, 12f)
                moveTo(13.6f, 6.1f)
                lineTo(19.5f, 12f)
                lineTo(13.6f, 17.9f)
            }
        }
    }

    val Forward: ImageVector by lazy {
        icon("AtharForward") {
            path(stroke = Stroke, strokeLineWidth = 1.8f) {
                moveTo(20f, 12f)
                lineTo(4.5f, 12f)
                moveTo(10.4f, 6.1f)
                lineTo(4.5f, 12f)
                lineTo(10.4f, 17.9f)
            }
        }
    }

    val Palette: ImageVector by lazy {
        icon("AtharPalette") {
            path(stroke = Stroke, strokeLineWidth = 1.65f) {
                moveTo(12f, 3.3f)
                curveTo(7.15f, 3.3f, 3.3f, 6.85f, 3.3f, 11.35f)
                curveTo(3.3f, 15.7f, 6.8f, 19.2f, 11.15f, 19.2f)
                lineTo(12.25f, 19.2f)
                curveTo(13.15f, 19.2f, 13.65f, 18.2f, 13.1f, 17.5f)
                curveTo(12.4f, 16.6f, 13.05f, 15.25f, 14.2f, 15.25f)
                lineTo(16.7f, 15.25f)
                curveTo(19.05f, 15.25f, 20.7f, 13.35f, 20.7f, 11.1f)
                curveTo(20.7f, 6.75f, 16.85f, 3.3f, 12f, 3.3f)
                close()
                moveTo(7f, 10.1f)
                lineTo(7.01f, 10.1f)
                moveTo(9.5f, 6.8f)
                lineTo(9.51f, 6.8f)
                moveTo(14f, 6.7f)
                lineTo(14.01f, 6.7f)
                moveTo(17f, 9.75f)
                lineTo(17.01f, 9.75f)
            }
        }
    }

    val TextSize: ImageVector by lazy {
        icon("AtharTextSize") {
            path(stroke = Stroke, strokeLineWidth = 1.65f) {
                moveTo(4f, 6f)
                lineTo(13.5f, 6f)
                moveTo(8.75f, 6f)
                lineTo(8.75f, 19f)
                moveTo(5.8f, 19f)
                lineTo(11.7f, 19f)
                moveTo(14.8f, 10.4f)
                lineTo(20.2f, 10.4f)
                moveTo(17.5f, 10.4f)
                lineTo(17.5f, 19f)
                moveTo(15.6f, 19f)
                lineTo(19.4f, 19f)
            }
        }
    }

    val Download: ImageVector by lazy {
        icon("AtharDownload") {
            path(stroke = Stroke, strokeLineWidth = 1.7f) {
                moveTo(12f, 3.5f)
                lineTo(12f, 15.2f)
                moveTo(7.6f, 11f)
                lineTo(12f, 15.4f)
                lineTo(16.4f, 11f)
                moveTo(4.2f, 19.8f)
                lineTo(19.8f, 19.8f)
            }
        }
    }

    val Filter: ImageVector by lazy {
        icon("AtharFilter") {
            path(stroke = Stroke, strokeLineWidth = 1.65f) {
                moveTo(4f, 6.2f)
                lineTo(20f, 6.2f)
                moveTo(4f, 12f)
                lineTo(20f, 12f)
                moveTo(4f, 17.8f)
                lineTo(20f, 17.8f)
                moveTo(8.2f, 4.1f)
                lineTo(8.2f, 8.3f)
                moveTo(15.8f, 9.9f)
                lineTo(15.8f, 14.1f)
                moveTo(10.5f, 15.7f)
                lineTo(10.5f, 19.9f)
            }
        }
    }

    val Sort: ImageVector by lazy {
        icon("AtharSort") {
            path(stroke = Stroke, strokeLineWidth = 1.7f) {
                moveTo(5f, 6f)
                lineTo(19f, 6f)
                moveTo(8f, 12f)
                lineTo(19f, 12f)
                moveTo(11f, 18f)
                lineTo(19f, 18f)
                moveTo(5f, 10f)
                lineTo(5f, 19f)
                moveTo(2.8f, 16.8f)
                lineTo(5f, 19f)
                lineTo(7.2f, 16.8f)
            }
        }
    }

    val Check: ImageVector by lazy {
        icon("AtharCheck") {
            path(stroke = Stroke, strokeLineWidth = 1.9f) {
                moveTo(4.4f, 12.4f)
                lineTo(9.5f, 17.5f)
                lineTo(19.8f, 6.5f)
            }
        }
    }

    val Close: ImageVector by lazy {
        icon("AtharClose") {
            path(stroke = Stroke, strokeLineWidth = 1.75f) {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }
    }

    val Bookmark: ImageVector by lazy {
        icon("AtharBookmark") {
            path(stroke = Stroke, strokeLineWidth = 1.65f) {
                moveTo(7.2f, 3.8f)
                lineTo(16.8f, 3.8f)
                curveTo(17.55f, 3.8f, 18.15f, 4.4f, 18.15f, 5.15f)
                lineTo(18.15f, 20.2f)
                lineTo(12f, 16.5f)
                lineTo(5.85f, 20.2f)
                lineTo(5.85f, 5.15f)
                curveTo(5.85f, 4.4f, 6.45f, 3.8f, 7.2f, 3.8f)
                close()
            }
        }
    }

    private fun icon(
        name: String,
        content: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = false,
    ).apply(content).build()

    private fun ImageVector.Builder.path(
        stroke: SolidColor,
        strokeLineWidth: Float,
        content: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ) {
        path(
            fill = null,
            stroke = stroke,
            strokeLineWidth = strokeLineWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = content,
        )
    }
}
