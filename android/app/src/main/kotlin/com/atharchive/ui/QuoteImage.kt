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
import androidx.core.content.res.ResourcesCompat
import com.atharchive.R
import java.io.File

/** Past this the picture stops with «…»; the whole passage still goes along as text. */
private const val ImageTextLimit = 900

/**
 * «مشاركة صورة»: the passage on a page-coloured card with its source line under it, as a PNG,
 * and the cited text beside it for apps that take both.
 */
internal fun shareQuoteImage(context: Context, text: String, citedText: String) {
    // The «— الكتاب، المؤلف، ص…» line of the cited text, without its link.
    val source = citedText.removePrefix(text).lines().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("http") }.joinToString("\n")
    val width = 1080
    val margin = 80
    val body = text.trim().let { if (it.length > ImageTextLimit) it.take(ImageTextLimit).substringBeforeLast(' ') + " …" else it }
    val ink = 0xFF2B2418.toInt()
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.amiri_regular)
        textSize = 50f
        color = ink
    }
    val sourcePaint = TextPaint(textPaint).apply {
        typeface = ResourcesCompat.getFont(context, R.font.ibm_plex_sans_arabic_regular)
        textSize = 32f
        color = 0xFF7A6A52.toInt()
    }
    fun layout(s: String, paint: TextPaint) = StaticLayout.Builder.obtain(s, 0, s.length, paint, width - 2 * margin)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
        .setLineSpacing(0f, 1.25f)
        .build()
    val bodyLayout = layout(body, textPaint)
    val footer = listOf(source, "أثر — تطبيق المكتبة").filter(String::isNotBlank).joinToString("\n")
    val footerLayout = layout(footer, sourcePaint)
    val height = margin + bodyLayout.height + 60 + footerLayout.height + margin
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        drawColor(0xFFFBF6EC.toInt())
        drawRect(margin / 2f, margin / 2f, width - margin / 2f, height - margin / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = 0xFFD9CBB0.toInt()
        })
        save(); translate(margin.toFloat(), margin.toFloat()); bodyLayout.draw(this); restore()
        save(); translate(margin.toFloat(), (margin + bodyLayout.height + 60).toFloat()); footerLayout.draw(this); restore()
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
