package et.tibeb.examprep.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

const val MAX_UPLOAD_BYTES = 20L * 1024 * 1024

fun Context.displayName(uri: Uri): String =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }?.substringBeforeLast('.') ?: "Untitled"

fun Context.sizeOf(uri: Uri): Long =
    contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
        if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L
    } ?: -1L

/**
 * Loads an image scaled so its long side is <= maxSide and re-encodes as JPEG.
 * A 12 MP phone photo (~4 MB) becomes ~250 KB: cheaper mobile data and faster AI grading.
 */
fun Context.compressedJpeg(uri: Uri, maxSide: Int = 1600, quality: Int = 82): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        ?: error("Could not read image")
    val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
    val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    return ByteArrayOutputStream().use { s ->
        out.compress(Bitmap.CompressFormat.JPEG, quality, s)
        if (out !== bmp) out.recycle()
        bmp.recycle()
        s.toByteArray()
    }
}

fun Context.readBytes(uri: Uri): ByteArray =
    contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read file")

fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

/** App-private folder for photos of answers (deleted with the app, never in shared storage). */
fun Context.answersDir(): File = File(filesDir, "answers").apply { mkdirs() }
