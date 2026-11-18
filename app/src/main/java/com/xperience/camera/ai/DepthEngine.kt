package com.xperience.camera.ai

import com.xperience.camera.image.FloatImage
import com.xperience.camera.image.Pixels
import com.xperience.camera.image.clamp01
import com.xperience.camera.image.lerp
import com.xperience.camera.image.parallelRows
import com.xperience.camera.image.smoothstep
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class BokehStyle(val label: String) {
    NATURAL("Natural"),
    CIRCULAR("Circular"),
    CINEMATIC("Soft Cinematic"),
    CREAMY("Creamy"),
    VINTAGE("Lens Vintage"),
}

val APERTURES = floatArrayOf(1.0f, 1.2f, 1.4f, 1.8f, 2.0f, 2.8f, 4.0f, 5.6f, 8.0f, 11f)

data class PortraitSettings(
    val enabled: Boolean = false,
    val aperture: Float = 1.4f,
    val intensity: Float = 1f,        // 0..1.5 kekuatan kabur
    val focusDepth: Float = -1f,      // -1 = auto (subjek utama)
    val focusX: Float = -1f, val focusY: Float = -1f,
    val transition: Float = 0.18f,    // 0.03..0.6
    val style: BokehStyle = BokehStyle.NATURAL,
    val blurForeground: Boolean = true,
    val keepAllSubjects: Boolean = true,
)

/**
 * Enjin Potret Kedalaman AI.
 * 1) Topeng subjek (ML Kit) + pembetulan berus pengguna
 * 2) Penghalusan tepi dengan Guided Filter (rambut, telinga, cermin mata, tepi baju)
 * 3) Peta kedalaman: subjek (setiap komponen ada kedalaman sendiri) + latar berlapis (atas = jauh)
 * 4) Kabur kanta berubah-ubah ikut kedalaman, dengan kernel bokeh berbentuk cakera,
 *    penguat highlight (bola bokeh) dan pemberat latar (tiada "halo" warna subjek).
 */
class DepthModel(val w: Int, val h: Int, val mask: FloatArray, val depth: FloatArray, val subjectDepth: Float)

object DepthEngine {

    /** Gabung topeng AI + suntingan berus, halusi tepi, bina peta kedalaman. */
    suspend fun build(img: FloatImage, rawMask: FloatArray, edits: FloatArray?, keepAll: Boolean): DepthModel {
        val w = img.w; val h = img.h
        val m0 = FloatArray(w * h)
        for (i in m0.indices) m0[i] = clamp01(rawMask[i] + (edits?.get(i) ?: 0f))
        val guide = img.luminance()
        val r = max(3, max(w, h) / 170)
        var refined = Pixels.guidedFilter(guide, m0, w, h, r, 1.2e-4f)
        // laluan kedua yang lebih halus untuk helaian rambut
        refined = Pixels.guidedFilter(guide, refined.also { for (k in it.indices) it[k] = clamp01(it[k]) }, w, h, max(2, r / 3), 4e-4f)
        for (i in refined.indices) refined[i] = smoothstep(0.12f, 0.88f, refined[i])

        // komponen bersambung (pada resolusi rendah) — subjek berbilang
        val ds = max(1, max(w, h) / 320)
        val sw = max(1, w / ds); val sh = max(1, h / ds)
        val small = Pixels.resize(refined, w, h, sw, sh)
        val labels = IntArray(sw * sh) { -1 }
        val areas = ArrayList<Int>()
        val stack = IntArray(sw * sh)
        for (start in small.indices) {
            if (small[start] < 0.5f || labels[start] >= 0) continue
            val id = areas.size
            var sp = 0; stack[sp++] = start; labels[start] = id; var area = 0
            while (sp > 0) {
                val p = stack[--sp]; area++
                val px = p % sw; val py = p / sw
                if (px > 0) { val q = p - 1; if (labels[q] < 0 && small[q] >= 0.5f) { labels[q] = id; stack[sp++] = q } }
                if (px < sw - 1) { val q = p + 1; if (labels[q] < 0 && small[q] >= 0.5f) { labels[q] = id; stack[sp++] = q } }
                if (py > 0) { val q = p - sw; if (labels[q] < 0 && small[q] >= 0.5f) { labels[q] = id; stack[sp++] = q } }
                if (py < sh - 1) { val q = p + sw; if (labels[q] < 0 && small[q] >= 0.5f) { labels[q] = id; stack[sp++] = q } }
            }
            areas.add(area)
        }
        val maxArea = max(1, areas.maxOrNull() ?: 1)
        // subjek lebih besar = lebih dekat kamera
        val compDepth = FloatArray(areas.size) { k ->
            if (keepAll) 0.2f else 0.2f + 0.25f * (1f - sqrt(areas[k] / maxArea.toFloat()))
        }
        val subjDepthSmall = FloatArray(sw * sh) { k -> if (labels[k] >= 0) compDepth[labels[k]] else 0.2f }
        val subjDepth = Pixels.resize(Pixels.boxBlur(subjDepthSmall, sw, sh, 2), sw, sh, w, h)

        val depth = FloatArray(w * h)
        parallelRows(h) { y0, y1 ->
            for (y in y0 until y1) {
                val bg = 0.58f + 0.42f * (1f - y / (h - 1f)).pow(0.8f) // atas bingkai = paling jauh
                for (x in 0 until w) {
                    val i = y * w + x
                    depth[i] = lerp(bg, subjDepth[i], refined[i])
                }
            }
        }
        return DepthModel(w, h, refined, depth, 0.2f)
    }

    /** Skala model ke resolusi lain (untuk eksport resolusi penuh), dengan penghalusan semula tepi. */
    suspend fun scaleTo(model: DepthModel, img: FloatImage): DepthModel {
        if (model.w == img.w && model.h == img.h) return model
        val m = Pixels.resize(model.mask, model.w, model.h, img.w, img.h)
        val guide = img.luminance()
        val r = max(3, max(img.w, img.h) / 400)
        val refined = Pixels.guidedFilter(guide, m, img.w, img.h, r, 2e-4f)
        for (i in refined.indices) refined[i] = clamp01(refined[i])
        val d = Pixels.resize(model.depth, model.w, model.h, img.w, img.h)
        // kekalkan kedalaman subjek pada tepi yang diperhalus
        for (i in d.indices) d[i] = lerp(d[i], min(d[i], model.subjectDepth + 0.05f), refined[i] * 0.5f)
        return DepthModel(img.w, img.h, refined, d, model.subjectDepth)
    }

    /** Kekuatan relatif dari nilai f (f/1.0 = 1.0 ... f/11 ≈ 0.03). */
    fun apertureStrength(f: Float): Float = ((1f / f - 1f / 16f) / (1f - 1f / 16f)).coerceIn(0f, 1f)

    fun depthAt(model: DepthModel, nx: Float, ny: Float): Float {
        val x = (nx * (model.w - 1)).roundToInt().coerceIn(0, model.w - 1)
        val y = (ny * (model.h - 1)).roundToInt().coerceIn(0, model.h - 1)
        return model.depth[y * model.w + x]
    }

    private class Kernel(val dx: FloatArray, val dy: FloatArray, val wt: FloatArray)

    private fun kernel(style: BokehStyle, radius: Float): Kernel {
        val n = when { radius < 3f -> 24; radius < 7f -> 48; else -> 80 }
        val dx = FloatArray(n); val dy = FloatArray(n); val wt = FloatArray(n)
        val golden = PI * (3.0 - sqrt(5.0))
        for (k in 0 until n) {
            val rr = sqrt((k + 0.5) / n).toFloat()
            val a = k * golden
            var x = (rr * cos(a)).toFloat(); var y = (rr * sin(a)).toFloat()
            var weight = 1f
            when (style) {
                BokehStyle.CREAMY -> weight = exp(-2.2f * rr * rr)
                BokehStyle.CINEMATIC -> { x *= 0.62f; weight = 1f - 0.25f * rr } // bokeh bujur anamorfik
                BokehStyle.VINTAGE -> weight = 0.6f + 0.9f * rr * rr               // tepi "soap bubble"
                BokehStyle.CIRCULAR -> weight = 1f
                BokehStyle.NATURAL -> weight = 1f - 0.15f * rr
            }
            dx[k] = x * radius; dy[k] = y * radius; wt[k] = weight
        }
        return Kernel(dx, dy, wt)
    }

    private fun highlightBoost(style: BokehStyle) = when (style) {
        BokehStyle.NATURAL -> 1.2f
        BokehStyle.CIRCULAR -> 3.0f
        BokehStyle.CINEMATIC -> 1.8f
        BokehStyle.CREAMY -> 0.2f
        BokehStyle.VINTAGE -> 2.4f
    }

    /**
     * Hasilkan imej potret. [img] diubah suai di tempat.
     */
    suspend fun render(img: FloatImage, model: DepthModel, s: PortraitSettings) {
        if (!s.enabled) return
        val w = img.w; val h = img.h
        val strength = apertureStrength(s.aperture) * s.intensity
        val maxR = strength * 0.045f * max(w, h)
        if (maxR < 0.8f) return
        val fd = if (s.focusDepth >= 0f) s.focusDepth else model.subjectDepth

        // Resolusi kerja untuk lapisan kabur
        val ds = max(1, (maxR / 11f).toInt())
        val sw = max(1, w / ds); val sh = max(1, h / ds)
        val sr = Pixels.downscaleArea(img.r, w, h, ds)
        val sg = Pixels.downscaleArea(img.g, w, h, ds)
        val sb = Pixels.downscaleArea(img.b, w, h, ds)
        val sm = Pixels.downscaleArea(model.mask, w, h, ds)
        val sd = Pixels.downscaleArea(model.depth, w, h, ds)
        val boost = highlightBoost(s.style)
        // linear + penguat highlight untuk bola bokeh
        val lr = FloatArray(sw * sh); val lg = FloatArray(sw * sh); val lb = FloatArray(sw * sh)
        val bw = FloatArray(sw * sh)
        for (i in lr.indices) {
            val l = 0.2126f * sr[i] + 0.7152f * sg[i] + 0.0722f * sb[i]
            val k = 1f + boost * smoothstep(0.72f, 1f, l) * 3f
            lr[i] = sr[i].pow(2.2f) * k; lg[i] = sg[i].pow(2.2f) * k; lb[i] = sb[i].pow(2.2f) * k
            // pemberat: piksel subjek tidak dibaurkan ke latar (elak halo)
            val inFocusSubject = 1f - smoothstep(0.02f, 0.12f, abs(sd[i] - fd))
            bw[i] = 0.02f + (1f - sm[i] * inFocusSubject).let { it * it }
        }
        val levels = 4
        val layers = Array(levels) { Array(3) { FloatArray(sw * sh) } }
        for (j in 0 until levels) {
            val rad = (maxR / ds) * (j + 1) / levels
            val ker = kernel(s.style, rad)
            val outR = layers[j][0]; val outG = layers[j][1]; val outB = layers[j][2]
            parallelRows(sh) { y0, y1 ->
                for (y in y0 until y1) for (x in 0 until sw) {
                    var ar = 0f; var ag = 0f; var ab = 0f; var aw = 0f
                    for (k in ker.dx.indices) {
                        val xx = (x + ker.dx[k]).roundToInt().coerceIn(0, sw - 1)
                        val yy = (y + ker.dy[k]).roundToInt().coerceIn(0, sh - 1)
                        val q = yy * sw + xx
                        val wq = ker.wt[k] * bw[q]
                        ar += lr[q] * wq; ag += lg[q] * wq; ab += lb[q] * wq; aw += wq
                    }
                    val i = y * sw + x
                    val inv = 1f / max(aw, 1e-6f)
                    outR[i] = clamp01(ar * inv).pow(1f / 2.2f)
                    outG[i] = clamp01(ag * inv).pow(1f / 2.2f)
                    outB[i] = clamp01(ab * inv).pow(1f / 2.2f)
                }
            }
        }
        val tol = 0.025f
        val trans = max(0.02f, s.transition)
        val fx = sw.toFloat() / w; val fy = sh.toFloat() / h
        parallelRows(h) { y0, y1 ->
            for (y in y0 until y1) {
                val syf = ((y + 0.5f) * fy - 0.5f).coerceIn(0f, sh - 1f)
                val y0i = syf.toInt(); val y1i = min(sh - 1, y0i + 1); val ty = syf - y0i
                for (x in 0 until w) {
                    val i = y * w + x
                    val d = model.depth[i]
                    var a = clamp01((abs(d - fd) - tol) / trans)
                    if (!s.blurForeground && d < fd) a = 0f
                    if (a <= 0.001f) continue
                    val sxf = ((x + 0.5f) * fx - 0.5f).coerceIn(0f, sw - 1f)
                    val x0i = sxf.toInt(); val x1i = min(sw - 1, x0i + 1); val tx = sxf - x0i
                    val t = a * levels
                    val j = min(levels - 1, t.toInt())
                    val frac = if (t >= levels) 1f else t - j
                    for (c in 0 until 3) {
                        val hi = layers[j][c]
                        val vHi = bil(hi, sw, x0i, x1i, y0i, y1i, tx, ty)
                        val vLo = if (j == 0) when (c) { 0 -> img.r[i]; 1 -> img.g[i]; else -> img.b[i] }
                        else bil(layers[j - 1][c], sw, x0i, x1i, y0i, y1i, tx, ty)
                        val v = if (j == 0) lerp(vLo, vHi, frac) else lerp(vLo, vHi, frac)
                        when (c) { 0 -> img.r[i] = v; 1 -> img.g[i] = v; else -> img.b[i] = v }
                    }
                }
            }
        }
    }

    private fun bil(a: FloatArray, sw: Int, x0: Int, x1: Int, y0: Int, y1: Int, tx: Float, ty: Float): Float {
        val p = a[y0 * sw + x0] + (a[y0 * sw + x1] - a[y0 * sw + x0]) * tx
        val q = a[y1 * sw + x0] + (a[y1 * sw + x1] - a[y1 * sw + x0]) * tx
        return p + (q - p) * ty
    }

    /** Visualisasi peta kedalaman (dekat = putih, jauh = gelap). */
    fun depthPreview(model: DepthModel): IntArray = IntArray(model.w * model.h) { i ->
        val v = ((1f - model.depth[i]) * 255f).toInt().coerceIn(0, 255)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
}
