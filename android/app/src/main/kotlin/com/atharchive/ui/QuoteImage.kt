package com.atharchive.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.atharchive.R
import java.io.File

/** Past this the picture stops with «…»; the whole passage still goes along as text. */
private const val ImageTextLimit = 900

/**
 * «مشاركة صورة»: the passage on a page-coloured card with its source line under it, as a PNG,
 * and the cited text beside it for apps that take both. [verses] is (صدر, عجز) per verse: drawn
 * staggered like the poem page, صدر at the start and عجز at the far end, instead of [text].
 */
internal fun shareQuoteImage(
    context: Context,
    text: String,
    citedText: String,
    colors: com.atharchive.feature.reader.ReaderColors? = null,
    verses: List<Pair<String, String?>> = emptyList(),
) {
    // The «— الكتاب، المؤلف، ص…» line of the cited text, without its link.
    val source = citedText.removePrefix(text).lines().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("http") }.joinToString("\n")
    val width = 1080
    val margin = 80
    val body = text.trim().let { if (it.length > ImageTextLimit) it.take(ImageTextLimit).substringBeforeLast(' ') + " …" else it }
    // The card wears the reader's current theme; without one, the warm paper card.
    fun argb(c: androidx.compose.ui.graphics.Color?, fallback: Int) = c?.toArgb() ?: fallback
    val ink = argb(colors?.text, 0xFF2B2418.toInt())
    val paper = argb(colors?.page, 0xFFFBF6EC.toInt())
    val muted = argb(colors?.secondary, 0xFF7A6A52.toInt())
    val rule = argb(colors?.divider, 0xFFD9CBB0.toInt())
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.amiri_regular)
        textSize = 50f
        color = ink
    }
    val sourcePaint = TextPaint(textPaint).apply {
        typeface = ResourcesCompat.getFont(context, R.font.ibm_plex_sans_arabic_regular)
        textSize = 32f
        color = muted
    }
    fun layout(s: String, paint: TextPaint, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) = StaticLayout.Builder.obtain(s, 0, s.length, paint, width - 2 * margin)
        .setAlignment(align)
        .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
        .setLineSpacing(0f, 1.25f)
        .build()
    // (layout, space above it): verses cut whole at the limit, never mid-verse.
    val bodyParts = if (verses.isEmpty()) listOf(layout(body, textPaint) to 0) else {
        var used = 0
        val kept = verses.takeWhile { (sadr, ajuz) -> (used == 0 || used + sadr.length + (ajuz?.length ?: 0) <= ImageTextLimit).also { used += sadr.length + (ajuz?.length ?: 0) } }
        kept.flatMapIndexed { i, (sadr, ajuz) ->
            val gap = if (i == 0) 0 else 36
            if (ajuz == null) listOf(layout(sadr, textPaint, Layout.Alignment.ALIGN_CENTER) to gap)
            else listOf(layout(sadr, textPaint) to gap, layout(ajuz, textPaint, Layout.Alignment.ALIGN_OPPOSITE) to 0)
        } + (if (kept.size < verses.size) listOf(layout("…", textPaint, Layout.Alignment.ALIGN_CENTER) to 36) else emptyList())
    }
    val bodyHeight = bodyParts.sumOf { (l, gap) -> l.height + gap }
    val footer = listOf(source, "أثر — تطبيق المكتبة").filter(String::isNotBlank).joinToString("\n")
    val footerLayout = layout(footer, sourcePaint)
    val height = margin + bodyHeight + 60 + footerLayout.height + margin
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        drawColor(paper)
        drawRect(margin / 2f, margin / 2f, width - margin / 2f, height - margin / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = rule
        })
        var y = margin
        bodyParts.forEach { (l, gap) -> y += gap; save(); translate(margin.toFloat(), y.toFloat()); l.draw(this); restore(); y += l.height }
        save(); translate(margin.toFloat(), (margin + bodyHeight + 60).toFloat()); footerLayout.draw(this); restore()
    }
    val file = File(context.cacheDir, "shared/quote.png").apply { parentFile?.mkdirs() }
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, citedText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "مشاركة صورة",
        ),
    )
}
