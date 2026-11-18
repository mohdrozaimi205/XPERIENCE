package com.xperience.camera.process

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.xperience.camera.ai.BokehStyle
import com.xperience.camera.ai.DepthEngine
import com.xperience.camera.ai.PortraitSettings
import com.xperience.camera.ai.Segmenter
import com.xperience.camera.camera.Dro
import com.xperience.camera.image.Collection
import com.xperience.camera.image.ColorEngine
import com.xperience.camera.image.FloatImage
import com.xperience.camera.image.LightStyle
import com.xperience.camera.image.Looks
import com.xperience.camera.image.Pixels
import com.xperience.camera.image.StudioLighting
import com.xperience.camera.image.ToneSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.sqrt

/** Pemprosesan selepas tangkapan (di dalam peranti — tiada gambar dihantar keluar). */
data class ProcessOptions(
    val lookId: String? = null,
    val lookIntensity: Float = 1f,
    val dro: Dro = Dro.OFF,
    val bokeh: Boolean = false,
    val aperture: Float = 1.8f,
    val bokehIntensity: Float = 1f,
    val bokehStyle: String = "NATURAL",
    val quality: Int = 95,
) {
    val active: Boolean get() = lookId != null || dro != Dro.OFF || bokeh
}

object PhotoProcessor {

    /** Had piksel kerja (memori): ~12MP. */
    private const val MAX_PX = 12_500_000

    private val EXIF_TAGS = listOf(
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_FLASH, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_METERING_MODE, ExifInterface.TAG_EXPOSURE_PROGRAM, ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE, ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
    )

    fun decodeUpright(open: () -> InputStream, maxPx: Int = MAX_PX): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0) return null
        var sample = 1
        while (o.outWidth.toLong() * o.outHeight / (sample.toLong() * sample) > maxPx) sample *= 2
        val bmp = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val orient = open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        val m = Matrix()
        when (orient) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private fun toFloat(bmp: Bitmap): FloatImage {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return FloatImage.fromArgb(px, bmp.width, bmp.height)
    }

    /** Saluran: DRO → AI Bokeh → Creative Look (+ pencahayaan studio Harcourt). */
    suspend fun render(src: Bitmap, o: ProcessOptions): Bitmap = withContext(Dispatchers.Default) {
        val img = toFloat(src)
        if (o.dro != Dro.OFF) {
            val k = if (o.dro == Dro.STRONG) 1f else 0.55f
            ColorEngine.apply(img, ToneSet(highlights = -0.45f * k, shadows = 0.45f * k, clarity = 0.08f * k), 1f)
            ColorEngine.applySpatial(img, ToneSet(clarity = 0.08f * k), 1f)
        }
        val look = Looks.byId(o.lookId)
        val needMask = o.bokeh || (look?.collection == Collection.HARCOURT)
        var mask: FloatArray? = null
        var faces = emptyList<android.graphics.RectF>()
        var person = false
        if (needMask) {
            // analisis AI pada resolusi kerja kecil, kemudian skala
            val scale = sqrt(1_600_000f / (img.w * img.h)).coerceAtMost(1f)
            val sw = max(64, (img.w * scale).toInt()); val sh = max(64, (img.h * scale).toInt())
            val small = Bitmap.createScaledBitmap(src, sw, sh, true)
            val res = Segmenter.analyse(small)
            faces = res.faces; person = res.personFound
            if (o.bokeh) {
                val smallImg = toFloat(small)
                val model = DepthEngine.build(smallImg, res.mask, null, true)
                val full = DepthEngine.scaleTo(model, img)
                mask = full.mask
                val style = try { BokehStyle.valueOf(o.bokehStyle) } catch (_: Exception) { BokehStyle.NATURAL }
                DepthEngine.render(img, full, PortraitSettings(enabled = true, aperture = o.aperture,
                    intensity = o.bokehIntensity, style = style))
            } else {
                mask = Pixels.resize(res.mask, res.w, res.h, img.w, img.h)
            }
        }
        if (look != null) {
            val k = o.lookIntensity
            ColorEngine.apply(img, look.tone, k)
            if (look.light != LightStyle.NONE || look.backgroundDark > 0f) {
                StudioLighting.apply(img, mask?.takeIf { person || o.bokeh }, faces,
                    StudioLighting.Params(look.light, look.lightStrength * k, look.backgroundDark * k))
            }
            ColorEngine.applySpatial(img, look.tone, k)
        }
        Bitmap.createBitmap(img.toArgb(), img.w, img.h, Bitmap.Config.ARGB_8888)
    }

    /** Simpan bitmap ke DCIM/XPERIENCE, salin EXIF asal (orientasi dinormalkan). */
    fun save(ctx: Context, bmp: Bitmap, name: String, quality: Int, exifFrom: (() -> InputStream)?, software: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/XPERIENCE")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val r = ctx.contentResolver
        val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        // tulis ke fail sementara dahulu supaya EXIF boleh disunting
        val tmp = File(ctx.cacheDir, "out_${System.nanoTime()}.jpg")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(60, 100), it) }
        try {
            val dst = ExifInterface(tmp.absolutePath)
            if (exifFrom != null) {
                val srcExif = exifFrom().use { ExifInterface(it) }
                for (t in EXIF_TAGS) srcExif.getAttribute(t)?.let { dst.setAttribute(t, it) }
            }
            dst.setAttribute(ExifInterface.TAG_SOFTWARE, software)
            dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            dst.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, bmp.width.toString())
            dst.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, bmp.height.toString())
            dst.saveAttributes()
        } catch (_: Exception) { }
        r.openOutputStream(uri)?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
        tmp.delete()
        if (Build.VERSION.SDK_INT >= 29) {
            r.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
        return uri
    }

    /** Salin fail JPEG asal (tanpa proses) ke galeri. */
    fun saveOriginal(ctx: Context, file: File, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/XPERIENCE")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        ctx.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        return uri
    }

    /** Proses fail tangkapan sementara → galeri. */
    suspend fun processCapture(ctx: Context, file: File, o: ProcessOptions, keepOriginal: Boolean): Uri? = withContext(Dispatchers.IO) {
        val base = file.nameWithoutExtension
        try {
            val bmp = decodeUpright({ file.inputStream() }) ?: return@withContext saveOriginal(ctx, file, base)
            val out = render(bmp, o)
            val uri = save(ctx, out, base, o.quality, { file.inputStream() }, "XPERIENCE")
            if (keepOriginal) saveOriginal(ctx, file, base + "_ORIG")
            uri
        } catch (e: Throwable) {
            // jika memori tidak cukup dsb — jangan hilang gambar
            saveOriginal(ctx, file, base)
        } finally {
            file.delete()
        }
    }
}
