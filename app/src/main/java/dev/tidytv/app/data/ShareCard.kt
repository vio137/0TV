package dev.tidytv.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import dev.tidytv.app.R
import dev.tidytv.app.ui.BrandIcons
import dev.tidytv.core.lists.AppLabels
import java.io.File

/** What one tidy did, for the card people can share. */
data class ShareStats(
    val appsOff: Int,
    val tvName: String?,
    val freedMb: Int?,
    val newHome: Boolean,
    /** The apps that were turned off, as package to readable name, most recognisable first. */
    val apps: List<Pair<String, String>>,
)

/** The text that goes with the card when it is shared. */
fun ShareStats.caption(): String = buildString {
    append("I tidied my TV with 0TV: $appsOff junk ${if (appsOff == 1) "app" else "apps"} turned off")
    freedMb?.takeIf { it > 0 }?.let { append(", about $it MB of memory back") }
}


/**
 * Draws the share card: a 1080 x 1350 picture (the 4:5 shape feeds use) in the app's own look,
 * a blue room with a yellow floor, the cheering mascot, the numbers, and the icons of the apps
 * that were turned off, each crossed out.
 */
class ShareCard(private val context: Context, private val icons: Map<String, String>) {
    fun render(s: ShareStats): Bitmap {
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val font = runCatching { ResourcesCompat.getFont(context, R.font.nunito) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        fun paint(color: Int, size: Float, weight: Int = 900) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size
            typeface = font
            // Nunito is a variable font: the weight is an axis, not a separate face.
            fontVariationSettings = "'wght' $weight"
        }

        c.drawColor(BLUE)
        val floor = 1130f
        c.drawRect(0f, floor, w.toFloat(), h.toFloat(), Paint().apply { color = YELLOW })

        var y = 120f
        c.drawText("My TV,", 72f, y + 80f, paint(WHITE, 96f)); y += 112f
        c.drawText("cleaned up.", 72f, y + 80f, paint(YELLOW, 96f)); y += 170f

        // The big number.
        val n = s.appsOff.toString()
        val big = paint(WHITE, 220f)
        c.drawText(n, 72f, y + 170f, big)
        val nx = 72f + big.measureText(n) + 28f
        c.drawText(if (s.appsOff == 1) "junk app" else "junk apps", nx, y + 90f, paint(WHITE, 60f, 800))
        c.drawText("turned off", nx, y + 162f, paint(WHITE, 60f, 800))
        y += 230f

        val details = listOfNotNull(
            s.tvName?.let { "on my $it" },
            s.freedMb?.takeIf { it > 0 }?.let { "about $it MB of memory back" },
            if (s.newHome) "and a clean new home screen" else null,
        ).joinToString("\n")
        if (details.isNotEmpty()) {
            val lay = StaticLayout.Builder.obtain(details, 0, details.length, paint(WHITE, 44f, 700), w - 144)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(10f, 1f).setMaxLines(3)
                .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            c.save(); c.translate(72f, y); lay.draw(c); c.restore()
            y += lay.height + 50f
        }

        // A row of the apps that went, crossed out. Up to 6, then "+N".
        val shown = s.apps.take(6)
        val size = 118f; val gap = 26f
        val rowY = minOf(y, floor - size - 70f)
        shown.forEachIndexed { i, (pkg, label) -> drawApp(c, pkg, label, 72f + i * (size + gap), rowY, size, font) }
        if (s.apps.size > shown.size) {
            c.drawText("+${s.apps.size - shown.size}", 72f + shown.size * (size + gap) + 4f, rowY + size * 0.66f, paint(WHITE, 52f))
        }

        // The floor: where to get it.
        c.drawText("0TV", 72f, floor + 135f, paint(INK, 80f))
        val tag = paint(INK, 38f, 800)
        c.drawText("Free and open source", w - 72f - tag.measureText("Free and open source"), floor + 92f, tag)
        return bmp
    }

    private fun drawApp(c: Canvas, pkg: String, label: String, x: Float, y: Float, size: Float, font: Typeface) {
        val r = RectF(x, y, x + size, y + size)
        val real = icons[pkg]?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
        if (real != null) {
            c.save()
            c.clipPath(android.graphics.Path().apply { addRoundRect(r, size * 0.24f, size * 0.24f, android.graphics.Path.Direction.CW) })
            c.drawBitmap(real, null, r, Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
        } else {
            c.drawOval(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE })
            val brand = AppLabels.iconSlug(pkg)?.let { BrandIcons.get(context)[it] }
            if (brand != null) {
                val s = size * 0.58f / 24f
                c.save(); c.translate(x + size * 0.21f, y + size * 0.21f); c.scale(s, s)
                c.drawPath(brand.path.asAndroidPath(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brand.color.toArgb() })
                c.restore()
            } else {
                val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = size * 0.36f; typeface = font; fontVariationSettings = "'wght' 800"; textAlign = Paint.Align.CENTER }
                c.drawText(AppLabels.initials(label), r.centerX(), r.centerY() + p.textSize * 0.36f, p)
            }
        }
        // The "off" badge.
        val br = size * 0.2f
        val cx = r.right - br * 0.6f; val cy = r.top + br * 0.6f
        c.drawCircle(cx, cy, br + 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK })
        c.drawCircle(cx, cy, br, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED })
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE; strokeWidth = br * 0.32f; strokeCap = Paint.Cap.ROUND }
        c.drawLine(cx - br * 0.5f, cy, cx + br * 0.5f, cy, stroke)
    }

    /** Writes the card where the FileProvider shares from. */
    fun save(s: ShareStats, dir: File): File {
        dir.mkdirs()
        return File(dir, "tidytv-card.png").apply { outputStream().use { render(s).compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private companion object {
        const val BLUE = 0xFF070B16.toInt()
        const val YELLOW = 0xFF2DD4D0.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val INK = 0xFF1B1B3A.toInt()
        const val RED = 0xFFD93A3A.toInt()
    }
}
