package app.imreal

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun loadOriented(f: File, maxSide: Int): Bitmap? {
    if (!f.exists()) return null
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    if (o.outWidth <= 0) return null
    var s = 1
    while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
    val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
    val deg = when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
        6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
    }
    return if (deg == 0f) bmp
    else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
}

private fun drawCrop(c: Canvas, b: Bitmap, dst: RectF) {
    val s = maxOf(dst.width() / b.width, dst.height() / b.height)
    val sw = dst.width() / s
    val sh = dst.height() / s
    val sx = (b.width - sw) / 2
    val sy = (b.height - sh) / 2
    c.drawBitmap(
        b, Rect(sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt()), dst,
        Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
    )
}

/** Satu gambar 3:4: foto besar + kotak kecil rounded ber-border hitam di kiri bawah. */
fun composeBitmap(m: Moment, swapped: Boolean, width: Int = 1080, rounded: Boolean = false): Bitmap? {
    val h = width * 4 / 3
    val big = loadOriented(if (swapped) m.front else m.back, width * 3 / 2) ?: return null
    val small = loadOriented(if (swapped) m.back else m.front, width / 2) ?: return null
    val out = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val k = width / 1080f
    if (rounded) {
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, width.toFloat(), h.toFloat()), 64f * k, 64f * k, Path.Direction.CW) })
    }
    drawCrop(c, big, RectF(0f, 0f, width.toFloat(), h.toFloat()))

    val sw = width * 0.3f
    val sh = sw * 4 / 3
    val mg = 42f * k
    val r = RectF(mg, h - mg - sh, mg + sw, h - mg)
    val rad = 54f * k
    c.save()
    c.clipPath(Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) })
    drawCrop(c, small, r)
    c.restore()
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 9f * k; color = Color.BLACK }
    val i = border.strokeWidth / 2
    c.drawRoundRect(RectF(r.left + i, r.top + i, r.right - i, r.bottom - i), rad, rad, border)

    if (!rounded) {
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 255, 255, 255); textSize = 40f * k; textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(8f * k, 0f, 2f * k, Color.BLACK)
        }
        c.drawText("ImReal", width - mg, h - mg, t)
    }
    big.recycle(); small.recycle()
    return out
}

suspend fun shareMoment(ctx: Context, m: Moment, swapped: Boolean) {
    val uri: Uri? = withContext(Dispatchers.IO) {
        val bmp = composeBitmap(m, swapped) ?: return@withContext null
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "imreal-${m.date}.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }
    if (uri == null) return
    val send = Intent(Intent.ACTION_SEND).setType("image/jpeg")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Bagikan momen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
