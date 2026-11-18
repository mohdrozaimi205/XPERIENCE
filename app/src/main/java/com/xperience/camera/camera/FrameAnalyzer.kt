package com.xperience.camera.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * Statistik satu bingkai pratonton (dalam orientasi PAPARAN, telah diputar).
 * Grid luma GW x GH, histogram 64 jalur, topeng puncak fokus & amaran pendedahan.
 */
class FrameStats(
    val gw: Int, val gh: Int,
    val luma: FloatArray,          // 0..255 purata sel
    val peak: BooleanArray,        // fokus puncak per sel
    val hist: IntArray,            // 64 bin
    val metered: Float,            // luma bermeter 0..255
    val clipHigh: BooleanArray,
    val clipLow: BooleanArray,
)

object Kelvin {
    /** Anggaran RGB (0..1) sumber cahaya pada suhu warna K (Tanner Helland). */
    fun rgb(k: Int): FloatArray {
        val t = k / 100.0
        val r = if (t <= 66) 255.0 else (329.698727446 * (t - 60).pow(-0.1332047592)).coerceIn(0.0, 255.0)
        val g = if (t <= 66) (99.4708025861 * ln(t) - 161.1195681661).coerceIn(0.0, 255.0)
        else (288.1221695283 * (t - 60).pow(-0.0755148492)).coerceIn(0.0, 255.0)
        val b = when {
            t >= 66 -> 255.0
            t <= 19 -> 0.0
            else -> (138.5177312231 * ln(t - 10) - 305.0447927307).coerceIn(0.0, 255.0)
        }
        return floatArrayOf((r / 255).toFloat(), (g / 255).toFloat(), (b / 255).toFloat().coerceAtLeast(0.02f))
    }

    /**
     * Gandaan R/B relatif kepada cahaya siang (5500K): pengganda yang perlu didarab
     * pada gandaan AWB-Daylight sensor supaya cahaya K kelihatan neutral.
     */
    fun relativeGains(k: Int): FloatArray {
        val d = rgb(5500); val c = rgb(k)
        val r = (d[0] / c[0]) / (d[1] / c[1])
        val b = (d[2] / c[2]) / (d[1] / c[1])
        return floatArrayOf(r.coerceIn(0.3f, 3.5f), b.coerceIn(0.3f, 3.5f))
    }
}

class FrameAnalyzer(
    private val settings: () -> CamSettings,
    private val spot: () -> Pair<Float, Float>?,
    private val onStats: (FrameStats) -> Unit,
) : ImageAnalysis.Analyzer {

    private var last = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - last < 90) return
            last = now
            process(image)
        } catch (_: Throwable) {
        } finally {
            image.close()
        }
    }

    private fun process(image: ImageProxy) {
        val plane = image.planes[0]
        val buf = plane.buffer
        val rs = plane.rowStride; val ps = plane.pixelStride
        val crop = image.cropRect
        val iw = crop.width(); val ih = crop.height()
        val rot = image.imageInfo.rotationDegrees
        // grid dalam orientasi sensor
        val sw = 96; val sh = max(8, (96f * ih / iw).toInt())
        val lum = FloatArray(sw * sh)
        val peakRaw = FloatArray(sw * sh)
        val hist = IntArray(64)
        val hi = BooleanArray(sw * sh); val lo = BooleanArray(sw * sh)
        fun px(x: Int, y: Int): Int {
            val xx = (crop.left + x.coerceIn(0, iw - 1)); val yy = (crop.top + y.coerceIn(0, ih - 1))
            return buf.get(yy * rs + xx * ps).toInt() and 0xFF
        }
        val stepX = iw / sw.toFloat(); val stepY = ih / sh.toFloat()
        var gmax = 1f
        for (gy in 0 until sh) for (gx in 0 until sw) {
            val cx = ((gx + 0.5f) * stepX).toInt(); val cy = ((gy + 0.5f) * stepY).toInt()
            // 4 sampel per sel
            val dx = (stepX / 4).toInt().coerceAtLeast(1); val dy = (stepY / 4).toInt().coerceAtLeast(1)
            val a = px(cx - dx, cy - dy); val b = px(cx + dx, cy - dy); val c = px(cx - dx, cy + dy); val d = px(cx + dx, cy + dy)
            val m = (a + b + c + d) / 4f
            val i = gy * sw + gx
            lum[i] = m
            hist[(a shr 2)]++; hist[(b shr 2)]++; hist[(c shr 2)]++; hist[(d shr 2)]++
            hi[i] = max(max(a, b), max(c, d)) >= 250
            lo[i] = m <= 6f
            // kecerunan tempatan (Sobel ringkas) untuk focus peaking
            val g = abs(px(cx + 1, cy) - px(cx - 1, cy)) + abs(px(cx, cy + 1) - px(cx, cy - 1)) +
                    (abs(px(cx + 2, cy) - px(cx - 2, cy)) + abs(px(cx, cy + 2) - px(cx, cy - 2))) / 2
            peakRaw[i] = g.toFloat()
            if (g > gmax) gmax = g.toFloat()
        }
        // ambang puncak: 8% teratas & cukup kuat
        val sorted = peakRaw.copyOf().also { it.sort() }
        val thr = max(sorted[(sorted.size * 0.92f).toInt().coerceAtMost(sorted.size - 1)], 38f)
        val peak = BooleanArray(sw * sh) { peakRaw[it] >= thr }

        // putar ke orientasi paparan
        val (rl, rw, rh) = rotate(lum, sw, sh, rot)
        val rp = rotateB(peak, sw, sh, rot)
        val rhi = rotateB(hi, sw, sh, rot); val rlo = rotateB(lo, sw, sh, rot)
        val metered = meter(rl, rw, rh, settings().metering, spot())
        onStats(FrameStats(rw, rh, rl, rp, hist, metered, rhi, rlo))
    }

    private fun meter(l: FloatArray, w: Int, h: Int, m: Metering, spot: Pair<Float, Float>?): Float {
        val sx = spot?.first ?: 0.5f; val sy = spot?.second ?: 0.5f
        return when (m) {
            Metering.MULTI -> {
                var s = 0f; var ws = 0f
                for (y in 0 until h) for (x in 0 until w) {
                    val dx = x / w.toFloat() - 0.5f; val dy = y / h.toFloat() - 0.5f
                    val wt = 0.6f + 0.4f * exp(-(dx * dx + dy * dy) * 6f)
                    // kurangkan pengaruh langit/sorotan terang yang terpotong
                    val v = l[y * w + x]; val vw = if (v > 245f) wt * 0.5f else wt
                    s += v * vw; ws += vw
                }
                s / ws
            }
            Metering.CENTER -> {
                var s = 0f; var ws = 0f
                for (y in 0 until h) for (x in 0 until w) {
                    val dx = x / w.toFloat() - 0.5f; val dy = y / h.toFloat() - 0.5f
                    val wt = exp(-(dx * dx + dy * dy) * 14f)
                    s += l[y * w + x] * wt; ws += wt
                }
                s / ws
            }
            Metering.SPOT -> {
                val r = 0.05f
                var s = 0f; var n = 0
                for (y in 0 until h) for (x in 0 until w) {
                    if (abs(x / w.toFloat() - sx) < r && abs(y / h.toFloat() - sy) < r * w / h) { s += l[y * w + x]; n++ }
                }
                if (n == 0) l[(sy * (h - 1)).toInt() * w + (sx * (w - 1)).toInt()] else s / n
            }
            Metering.HIGHLIGHT -> {
                // lindungi sorotan: persentil ke-97 dipetakan ke ~215 (≈ 118 setara purata)
                val c = l.copyOf().also { it.sort() }
                val p = c[(c.size * 0.97f).toInt().coerceAtMost(c.size - 1)]
                p * (118f / 215f)
            }
        }
    }

    private fun rotate(a: FloatArray, w: Int, h: Int, rot: Int): Triple<FloatArray, Int, Int> = when (rot) {
        90 -> Triple(FloatArray(w * h) { i -> val x = i % h; val y = i / h; a[(h - 1 - x) * w + y] }, h, w)
        180 -> Triple(FloatArray(w * h) { i -> a[w * h - 1 - i] }, w, h)
        270 -> Triple(FloatArray(w * h) { i -> val x = i % h; val y = i / h; a[x * w + (w - 1 - y)] }, h, w)
        else -> Triple(a, w, h)
    }

    private fun rotateB(a: BooleanArray, w: Int, h: Int, rot: Int): BooleanArray = when (rot) {
        90 -> BooleanArray(w * h) { i -> val x = i % h; val y = i / h; a[(h - 1 - x) * w + y] }
        180 -> BooleanArray(w * h) { i -> a[w * h - 1 - i] }
        270 -> BooleanArray(w * h) { i -> val x = i % h; val y = i / h; a[x * w + (w - 1 - y)] }
        else -> a
    }
}
