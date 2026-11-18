package com.xperience.camera.ai

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.xperience.camera.image.FloatImage
import com.xperience.camera.image.Pixels
import com.xperience.camera.image.smoothstep
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pengesanan subjek AI di dalam peranti (offline):
 *  - ML Kit Selfie Segmentation (rangkaian neural, model ~4.5MB dibundel dalam APK)
 *  - ML Kit Face Detection (model dibundel)
 * Gambar TIDAK dihantar ke mana-mana pelayan.
 */
object Segmenter {

    data class Result(
        val mask: FloatArray,         // 0..1 pada saiz w x h
        val w: Int, val h: Int,
        val faces: List<RectF>,       // dinormalkan 0..1
        val personFound: Boolean,
    )

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }

    suspend fun analyse(bitmap: Bitmap): Result {
        val w = bitmap.width; val h = bitmap.height
        val input = InputImage.fromBitmap(bitmap, 0)
        val faces: List<RectF> = try {
            val det = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setMinFaceSize(0.05f)
                    .build()
            )
            val list = det.process(input).await()
            det.close()
            list.map { f ->
                val b = f.boundingBox
                RectF(b.left / w.toFloat(), b.top / h.toFloat(), b.right / w.toFloat(), b.bottom / h.toFloat())
            }
        } catch (e: Exception) { emptyList() }

        var mask = FloatArray(w * h)
        var person = false
        try {
            val seg = Segmentation.getClient(
                SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                    .build()
            )
            val m = seg.process(input).await()
            seg.close()
            val buf = m.buffer.order(ByteOrder.nativeOrder())
            buf.rewind()
            val mw = m.width; val mh = m.height
            val raw = FloatArray(mw * mh)
            for (i in raw.indices) raw[i] = buf.float
            mask = if (mw == w && mh == h) raw else Pixels.resize(raw, mw, mh, w, h)
            var area = 0
            for (v in mask) if (v > 0.5f) area++
            person = area > w * h * 0.01f
        } catch (e: Exception) { person = false }

        if (!person) {
            mask = saliency(bitmap)
        }
        return Result(mask, w, h, faces, person)
    }

    /**
     * Sandaran untuk subjek bukan manusia: kawasan paling tajam/berbeza
     * (subjek dalam fokus) digabung dengan keutamaan tengah bingkai.
     */
    private fun saliency(bitmap: Bitmap): FloatArray {
        val w = bitmap.width; val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val lum = FloatImage.fromArgb(px, w, h).luminance()
        val grad = FloatArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val gx = lum[i + 1] - lum[i - 1]
            val gy = lum[i + w] - lum[i - w]
            grad[i] = sqrt(gx * gx + gy * gy)
        }
        val s = Pixels.gaussian(grad, w, h, max(w, h) / 40f)
        var mx = 1e-6f
        for (v in s) if (v > mx) mx = v
        val out = FloatArray(w * h)
        var mean = 0f
        for (y in 0 until h) for (x in 0 until w) {
            val dx = (x - w / 2f) / (w / 2f); val dy = (y - h * 0.48f) / (h / 2f)
            val center = 1f - smoothstep(0.2f, 1.2f, sqrt(dx * dx + dy * dy))
            val v = (s[y * w + x] / mx) * 0.65f + center * 0.35f
            out[y * w + x] = v; mean += v
        }
        mean /= out.size
        for (i in out.indices) out[i] = smoothstep(mean * 1.05f, mean * 1.6f, out[i])
        return out
    }
}
