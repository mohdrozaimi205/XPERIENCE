package com.xperience.camera.image

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.max
import kotlin.math.min

/** Imej mentah dalam bentuk saluran float (0..1) — memudahkan pemprosesan berketepatan tinggi. */
class FloatImage(val w: Int, val h: Int) {
    val r = FloatArray(w * h)
    val g = FloatArray(w * h)
    val b = FloatArray(w * h)

    fun copy(): FloatImage {
        val o = FloatImage(w, h)
        r.copyInto(o.r); g.copyInto(o.g); b.copyInto(o.b)
        return o
    }

    fun luminance(): FloatArray {
        val l = FloatArray(w * h)
        for (i in l.indices) l[i] = 0.2126f * r[i] + 0.7152f * g[i] + 0.0722f * b[i]
        return l
    }

    fun toArgb(): IntArray {
        val out = IntArray(w * h)
        for (i in out.indices) {
            val rr = (clamp01(r[i]) * 255f + 0.5f).toInt()
            val gg = (clamp01(g[i]) * 255f + 0.5f).toInt()
            val bb = (clamp01(b[i]) * 255f + 0.5f).toInt()
            out[i] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
        return out
    }

    companion object {
        fun fromArgb(px: IntArray, w: Int, h: Int): FloatImage {
            val img = FloatImage(w, h)
            val inv = 1f / 255f
            for (i in px.indices) {
                val c = px[i]
                img.r[i] = ((c shr 16) and 0xFF) * inv
                img.g[i] = ((c shr 8) and 0xFF) * inv
                img.b[i] = (c and 0xFF) * inv
            }
            return img
        }
    }
}

fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v
fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = clamp01((x - e0) / (e1 - e0))
    return t * t * (3f - 2f * t)
}

/** Jalankan gelung baris secara selari pada semua teras CPU. */
suspend fun parallelRows(h: Int, block: (y0: Int, y1: Int) -> Unit) = coroutineScope {
    val n = max(1, min(Runtime.getRuntime().availableProcessors(), 8))
    val step = (h + n - 1) / n
    (0 until n).map { k ->
        async(Dispatchers.Default) {
            val y0 = k * step
            val y1 = min(h, y0 + step)
            if (y0 < y1) block(y0, y1)
        }
    }.awaitAll()
}

object Pixels {

    /** Kabur kotak satu laluan (mendatar + menegak), O(n) tanpa mengira jejari. */
    fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        if (r <= 0) return src.copyOf()
        val tmp = FloatArray(w * h)
        val out = FloatArray(w * h)
        // mendatar
        for (y in 0 until h) {
            val row = y * w
            var acc = 0f
            for (x in -r..r) acc += src[row + x.coerceIn(0, w - 1)]
            val norm = 1f / (2 * r + 1)
            for (x in 0 until w) {
                tmp[row + x] = acc * norm
                val xa = (x + r + 1).coerceAtMost(w - 1)
                val xs = (x - r).coerceAtLeast(0)
                acc += src[row + xa] - src[row + xs]
            }
        }
        // menegak
        for (x in 0 until w) {
            var acc = 0f
            for (y in -r..r) acc += tmp[y.coerceIn(0, h - 1) * w + x]
            val norm = 1f / (2 * r + 1)
            for (y in 0 until h) {
                out[y * w + x] = acc * norm
                val ya = (y + r + 1).coerceAtMost(h - 1)
                val ys = (y - r).coerceAtLeast(0)
                acc += tmp[ya * w + x] - tmp[ys * w + x]
            }
        }
        return out
    }

    /** Anggaran kabur Gaussian: 3 laluan kabur kotak. */
    fun gaussian(src: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        if (sigma < 0.5f) return src.copyOf()
        val r = max(1, (sigma * 0.85f).toInt())
        return boxBlur(boxBlur(boxBlur(src, w, h, r), w, h, r), w, h, r)
    }

    /**
     * Guided filter (He et al.) — penapis memelihara tepi. Digunakan untuk
     * memperhalus topeng subjek supaya helaian rambut & tepi baju ikut imej asal.
     */
    fun guidedFilter(guide: FloatArray, p: FloatArray, w: Int, h: Int, r: Int, eps: Float): FloatArray {
        val n = w * h
        val ip = FloatArray(n)
        val ii = FloatArray(n)
        for (k in 0 until n) { ip[k] = guide[k] * p[k]; ii[k] = guide[k] * guide[k] }
        val meanI = boxBlur(guide, w, h, r)
        val meanP = boxBlur(p, w, h, r)
        val corrIp = boxBlur(ip, w, h, r)
        val corrII = boxBlur(ii, w, h, r)
        val a = FloatArray(n)
        val bb = FloatArray(n)
        for (k in 0 until n) {
            val varI = corrII[k] - meanI[k] * meanI[k]
            val covIp = corrIp[k] - meanI[k] * meanP[k]
            a[k] = covIp / (varI + eps)
            bb[k] = meanP[k] - a[k] * meanI[k]
        }
        val meanA = boxBlur(a, w, h, r)
        val meanB = boxBlur(bb, w, h, r)
        val q = FloatArray(n)
        for (k in 0 until n) q[k] = meanA[k] * guide[k] + meanB[k]
        return q
    }

    /** Ubah saiz saluran float secara bilinear. */
    fun resize(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        if (sw == dw && sh == dh) return src.copyOf()
        val out = FloatArray(dw * dh)
        val sx = sw.toFloat() / dw
        val sy = sh.toFloat() / dh
        for (y in 0 until dh) {
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, (sh - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = min(sh - 1, y0 + 1)
            val ty = fy - y0
            for (x in 0 until dw) {
                val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, (sw - 1).toFloat())
                val x0 = fx.toInt()
                val x1 = min(sw - 1, x0 + 1)
                val tx = fx - x0
                val a = src[y0 * sw + x0] + (src[y0 * sw + x1] - src[y0 * sw + x0]) * tx
                val c = src[y1 * sw + x0] + (src[y1 * sw + x1] - src[y1 * sw + x0]) * tx
                out[y * dw + x] = a + (c - a) * ty
            }
        }
        return out
    }

    fun resizeImage(src: FloatImage, dw: Int, dh: Int): FloatImage {
        val o = FloatImage(dw, dh)
        resize(src.r, src.w, src.h, dw, dh).copyInto(o.r)
        resize(src.g, src.w, src.h, dw, dh).copyInto(o.g)
        resize(src.b, src.w, src.h, dw, dh).copyInto(o.b)
        return o
    }

    /** Pengecilan purata kawasan (anti-alias) untuk imej besar. */
    fun downscaleArea(src: FloatArray, sw: Int, sh: Int, factor: Int): FloatArray {
        if (factor <= 1) return src.copyOf()
        val dw = max(1, sw / factor)
        val dh = max(1, sh / factor)
        val out = FloatArray(dw * dh)
        val inv = 1f / (factor * factor)
        for (y in 0 until dh) for (x in 0 until dw) {
            var acc = 0f
            for (j in 0 until factor) {
                val row = min(sh - 1, y * factor + j) * sw
                for (i in 0 until factor) acc += src[row + min(sw - 1, x * factor + i)]
            }
            out[y * dw + x] = acc * inv
        }
        return out
    }
}
