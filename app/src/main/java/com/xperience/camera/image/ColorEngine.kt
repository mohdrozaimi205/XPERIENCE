package com.xperience.camera.image

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Enjin warna per-piksel: pendedahan, lengkung tona, imbangan putih, HSL 8 jalur,
 * perlindungan kulit, split-toning, pencampur monokrom, lengkung per saluran.
 */
object ColorEngine {

    private const val LUT = 1024

    /** Nilai lengkung 5 titik (offset) pada x menggunakan Catmull-Rom. */
    fun curveAt(off: FloatArray, x: Float): Float {
        val p = x * 4f
        val i = p.toInt().coerceIn(0, 3)
        val t = p - i
        val p0 = off[max(0, i - 1)]; val p1 = off[i]; val p2 = off[i + 1]; val p3 = off[min(4, i + 2)]
        val t2 = t * t; val t3 = t2 * t
        return 0.5f * ((2f * p1) + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (-p0 + 3f * p1 - 3f * p2 + p3) * t3)
    }

    private fun invSmooth(x: Float): Float = (0.5 - sin(asin(1.0 - 2.0 * x) / 3.0)).toFloat()

    /** Bina LUT tona induk (per saluran sama). */
    private fun buildToneLut(t: ToneSet): FloatArray {
        val lut = FloatArray(LUT)
        val gain = 2.0.pow(t.exposure.toDouble()).toFloat()
        val bExp = if (t.brightness >= 0f) 1f - t.brightness * 0.4f else 1f - t.brightness * 0.6f
        val lift = t.fade * 0.16f
        for (k in 0 until LUT) {
            var x = k / (LUT - 1f)
            // pendedahan dalam ruang linear + bahu lembut highlight
            if (t.exposure != 0f) {
                var lin = x.pow(2.2f) * gain
                if (lin > 0.8f) lin = 0.8f + (lin - 0.8f) / (1f + (lin - 0.8f) / 0.2f)
                x = clamp01(lin).pow(1f / 2.2f)
            }
            if (t.brightness != 0f) x = x.pow(bExp)
            if (t.contrast > 0f) x = lerp(x, x * x * (3f - 2f * x), min(1f, t.contrast))
            else if (t.contrast < 0f) x = lerp(x, invSmooth(x), min(1f, -t.contrast) * 0.7f)
            if (t.blacks != 0f) x += t.blacks * 0.12f * (1f - smoothstep(0f, 0.35f, x))
            if (t.whites != 0f) x += t.whites * 0.12f * smoothstep(0.6f, 1f, x)
            x = clamp01(x)
            x = clamp01(x + curveAt(t.curve, x))
            if (lift > 0f) x = lift + x * (1f - lift - t.fade * 0.04f)
            lut[k] = clamp01(x)
        }
        return lut
    }

    private fun buildChannelLut(off: FloatArray): FloatArray? {
        if (off.all { it == 0f }) return null
        return FloatArray(LUT) { k -> val x = k / (LUT - 1f); clamp01(x + curveAt(off, x)) }
    }

    private val bandCenters = floatArrayOf(0f, 30f, 60f, 120f, 180f, 240f, 280f, 320f)

    /** Tukar hue 0..360 kepada sumbangan berwajaran dua jalur berjiran. */
    private fun bandValue(hsl: FloatArray, hue: Float, comp: Int): Float {
        var i = 7
        for (k in 0 until 8) {
            val next = if (k == 7) 360f else bandCenters[k + 1]
            if (hue >= bandCenters[k] && hue < next) { i = k; break }
        }
        val start = bandCenters[i]
        val end = if (i == 7) 360f else bandCenters[i + 1]
        val t = (hue - start) / (end - start)
        val a = hsl[i * 3 + comp]
        val b = hsl[((i + 1) % 8) * 3 + comp]
        return a + (b - a) * t
    }

    private fun hueToRgb(hue: Float, out: FloatArray) {
        val h = ((hue % 360f) + 360f) % 360f / 60f
        val x = 1f - abs(h % 2f - 1f)
        when (h.toInt()) {
            0 -> { out[0] = 1f; out[1] = x; out[2] = 0f }
            1 -> { out[0] = x; out[1] = 1f; out[2] = 0f }
            2 -> { out[0] = 0f; out[1] = 1f; out[2] = x }
            3 -> { out[0] = 0f; out[1] = x; out[2] = 1f }
            4 -> { out[0] = x; out[1] = 0f; out[2] = 1f }
            else -> { out[0] = 1f; out[1] = 0f; out[2] = x }
        }
    }

    /** Anggaran kebarangkalian piksel kulit (YCbCr) 0..1. */
    fun skinWeight(r: Float, g: Float, b: Float): Float {
        val y = 0.299f * r + 0.587f * g + 0.114f * b
        val cb = (b - y) * 0.564f
        val cr = (r - y) * 0.713f
        if (y < 0.12f || y > 0.97f) return 0f
        // pusat kulit sekitar cr≈0.09, cb≈-0.06
        val dcr = (cr - 0.095f) / 0.07f
        val dcb = (cb + 0.065f) / 0.07f
        val d = dcr * dcr + dcb * dcb
        return clamp01(1.2f - d * 0.6f)
    }

    /**
     * Gunakan ToneSet pada imej (di tempat). intensity 0..1 mencampurkan dengan input.
     */
    suspend fun apply(img: FloatImage, t: ToneSet, intensity: Float = 1f) {
        if (t.isIdentity() || intensity <= 0f) return
        val lut = buildToneLut(t)
        val lr = buildChannelLut(t.curveR)
        val lg = buildChannelLut(t.curveG)
        val lb = buildChannelLut(t.curveB)
        val hasHsl = t.hsl.any { it != 0f } || t.greenBias != 0f
        val hslArr = t.hsl.copyOf()
        if (t.greenBias != 0f) {
            hslArr[GREEN_I * 3] += -t.greenBias * 6f
            hslArr[GREEN_I * 3 + 1] += t.greenBias * 0.35f
            hslArr[AQUA_I * 3 + 1] += t.greenBias * 0.15f
        }
        val wbR = 1f + t.temperature * 0.16f + t.tint * 0.05f
        val wbG = 1f - t.tint * 0.10f
        val wbB = 1f - t.temperature * 0.16f + t.tint * 0.05f
        val hasWb = t.temperature != 0f || t.tint != 0f
        val satF = 1f + t.saturation
        val shTint = FloatArray(3); hueToRgb(t.shadowHue, shTint)
        val hiTint = FloatArray(3); hueToRgb(t.highlightHue, hiTint)
        val hasSplit = t.shadowSat > 0f || t.highlightSat > 0f
        val msum = max(0.001f, t.monoR + t.monoG + t.monoB)
        val mr = t.monoR / msum; val mg = t.monoG / msum; val mb = t.monoB / msum
        val q = LUT - 1
        val w = img.w
        parallelRows(img.h) { y0, y1 ->
            for (i in y0 * w until y1 * w) {
                val r0 = img.r[i]; val g0 = img.g[i]; val b0 = img.b[i]
                var r = r0; var g = g0; var b = b0
                if (hasWb) { r = clamp01(r * wbR); g = clamp01(g * wbG); b = clamp01(b * wbB) }
                r = lut[(r * q).toInt()]; g = lut[(g * q).toInt()]; b = lut[(b * q).toInt()]
                // bayang & highlight (memelihara warna)
                if (t.shadows != 0f || t.highlights != 0f) {
                    val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
                    val ws = 1f - smoothstep(0f, 0.55f, l)
                    val wh = smoothstep(0.45f, 1f, l)
                    val nl = clamp01(l + t.shadows * 0.32f * ws * (1f - l) + t.highlights * 0.30f * wh * l)
                    val ratio = min(4f, nl / max(l, 1e-4f))
                    r = clamp01(r * ratio); g = clamp01(g * ratio); b = clamp01(b * ratio)
                }
                val skin = if (t.skinProtect > 0f || t.skinWarmth != 0f) skinWeight(r0, g0, b0) else 0f
                val protect = skin * t.skinProtect
                // ketepuan & vibrance
                if (t.saturation != 0f || t.vibrance != 0f) {
                    val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
                    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
                    val s = mx - mn
                    var f = satF + t.vibrance * (1f - s) * 0.9f
                    f = lerp(f, 1f + (f - 1f) * 0.35f, protect)
                    r = clamp01(l + (r - l) * f); g = clamp01(l + (g - l) * f); b = clamp01(l + (b - l) * f)
                }
                if (hasHsl) {
                    // RGB -> HSV
                    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
                    val d = mx - mn
                    if (d > 1e-4f) {
                        var hue = when (mx) {
                            r -> 60f * (((g - b) / d) % 6f)
                            g -> 60f * ((b - r) / d + 2f)
                            else -> 60f * ((r - g) / d + 4f)
                        }
                        if (hue < 0f) hue += 360f
                        val sat = d / mx
                        val dh = bandValue(hslArr, hue, 0) * (1f - protect * 0.8f)
                        val ds = bandValue(hslArr, hue, 1) * (1f - protect * 0.6f)
                        val dl = bandValue(hslArr, hue, 2)
                        val nh = ((hue + dh) % 360f + 360f) % 360f
                        val ns = clamp01(sat * (1f + ds))
                        val nv = clamp01(mx * (1f + dl * 0.5f * sat))
                        // HSV -> RGB
                        val c = nv * ns
                        val hh = nh / 60f
                        val x = c * (1f - abs(hh % 2f - 1f))
                        val m = nv - c
                        when (hh.toInt()) {
                            0 -> { r = c + m; g = x + m; b = m }
                            1 -> { r = x + m; g = c + m; b = m }
                            2 -> { r = m; g = c + m; b = x + m }
                            3 -> { r = m; g = x + m; b = c + m }
                            4 -> { r = x + m; g = m; b = c + m }
                            else -> { r = c + m; g = m; b = x + m }
                        }
                    }
                }
                if (t.skinWarmth != 0f && skin > 0f) {
                    r = clamp01(r + t.skinWarmth * 0.05f * skin); b = clamp01(b - t.skinWarmth * 0.05f * skin)
                }
                if (t.mono > 0f) {
                    val m = clamp01(r * mr + g * mg + b * mb)
                    r = lerp(r, m, t.mono); g = lerp(g, m, t.mono); b = lerp(b, m, t.mono)
                }
                if (hasSplit) {
                    val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
                    val bal = t.splitBalance * 0.5f
                    val ws = (1f - smoothstep(0f, 0.6f + bal, l)) * t.shadowSat * 0.35f
                    val wh = smoothstep(0.4f + bal, 1f, l) * t.highlightSat * 0.35f
                    val sl = 0.2126f * shTint[0] + 0.7152f * shTint[1] + 0.0722f * shTint[2]
                    val hl = 0.2126f * hiTint[0] + 0.7152f * hiTint[1] + 0.0722f * hiTint[2]
                    r = clamp01(r + (shTint[0] - sl) * ws + (hiTint[0] - hl) * wh)
                    g = clamp01(g + (shTint[1] - sl) * ws + (hiTint[1] - hl) * wh)
                    b = clamp01(b + (shTint[2] - sl) * ws + (hiTint[2] - hl) * wh)
                }
                if (lr != null) r = lr[(r * q).toInt()]
                if (lg != null) g = lg[(g * q).toInt()]
                if (lb != null) b = lb[(b * q).toInt()]
                if (intensity < 1f) { r = lerp(r0, r, intensity); g = lerp(g0, g, intensity); b = lerp(b0, b, intensity) }
                img.r[i] = r; img.g[i] = g; img.b[i] = b
            }
        }
    }

    private const val GREEN_I = 3
    private const val AQUA_I = 4

    // ---------------------------------------------------------------- spatial

    /**
     * Kesan ruang: clarity, texture, sharpness, glow, halation, vignette, grain.
     * [scale] = saiz relatif imej (sisi panjang / 1000) supaya pratonton & eksport serupa.
     */
    suspend fun applySpatial(img: FloatImage, t: ToneSet, intensity: Float = 1f) {
        if (!t.hasSpatial() || intensity <= 0f) return
        val w = img.w; val h = img.h
        val scale = max(w, h) / 1000f
        val lum = img.luminance()
        fun addDetail(sigma: Float, amount: Float, midWeight: Boolean) {
            if (amount == 0f) return
            val blur = Pixels.gaussian(lum, w, h, max(0.6f, sigma))
            for (i in lum.indices) {
                val l = lum[i]
                var d = (l - blur[i]) * amount * intensity
                if (midWeight) d *= 4f * l * (1f - l)
                img.r[i] = clamp01(img.r[i] + d); img.g[i] = clamp01(img.g[i] + d); img.b[i] = clamp01(img.b[i] + d)
            }
        }
        addDetail(12f * scale, t.clarity * 1.3f, true)
        addDetail(2.2f * scale, t.texture * 1.6f, false)
        if (t.sharpness > 0f) addDetail(0.9f * scale, t.sharpness * 1.8f, false)

        if (t.glow > 0f || t.halation > 0f) {
            val l2 = img.luminance()
            if (t.glow > 0f) {
                val hi = FloatArray(l2.size) { k -> max(0f, l2[k] - 0.55f) / 0.45f }
                val gb = Pixels.gaussian(hi, w, h, 20f * scale)
                val a = t.glow * 0.55f * intensity
                for (i in gb.indices) {
                    val gv = gb[i] * a
                    img.r[i] = 1f - (1f - img.r[i]) * (1f - gv)
                    img.g[i] = 1f - (1f - img.g[i]) * (1f - gv)
                    img.b[i] = 1f - (1f - img.b[i]) * (1f - gv * 0.96f)
                }
            }
            if (t.halation > 0f) {
                val hi = FloatArray(l2.size) { k -> max(0f, l2[k] - 0.72f) / 0.28f }
                val hb = Pixels.gaussian(hi, w, h, 9f * scale)
                val a = t.halation * intensity
                for (i in hb.indices) {
                    val hv = hb[i] * a
                    img.r[i] = clamp01(img.r[i] + hv * 0.45f)
                    img.g[i] = clamp01(img.g[i] + hv * 0.12f)
                    img.b[i] = clamp01(img.b[i] + hv * 0.02f)
                }
            }
        }
        if (t.vignette != 0f) {
            val cx = w / 2f; val cy = h / 2f
            val v = t.vignette * intensity
            parallelRows(h) { y0, y1 ->
                for (y in y0 until y1) {
                    val dy = (y - cy) / cy
                    for (x in 0 until w) {
                        val dx = (x - cx) / cx
                        val d = sqrt(dx * dx * 0.85f + dy * dy * 0.85f)
                        val f = 1f + v * smoothstep(0.35f, 1.25f, d) * 0.85f
                        val i = y * w + x
                        img.r[i] = clamp01(img.r[i] * f); img.g[i] = clamp01(img.g[i] * f); img.b[i] = clamp01(img.b[i] * f)
                    }
                }
            }
        }
        if (t.grain > 0f) {
            val cell = max(1f, t.grainSize * scale * 1.1f)
            val amp = t.grain * 0.13f * intensity
            parallelRows(h) { y0, y1 ->
                for (y in y0 until y1) {
                    val gy = (y / cell).toInt()
                    for (x in 0 until w) {
                        val gx = (x / cell).toInt()
                        val n = hashNoise(gx, gy)
                        val i = y * w + x
                        val l = 0.2126f * img.r[i] + 0.7152f * img.g[i] + 0.0722f * img.b[i]
                        val d = n * amp * (1f - abs(2f * l - 1f) * 0.6f)
                        img.r[i] = clamp01(img.r[i] + d); img.g[i] = clamp01(img.g[i] + d); img.b[i] = clamp01(img.b[i] + d)
                    }
                }
            }
        }
    }

    /** Bunyi deterministik -1..1 (bijirin sama antara pratonton & eksport). */
    private fun hashNoise(x: Int, y: Int): Float {
        var n = x * 374761393 + y * 668265263
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        val u1 = ((n and 0xFFFF) + 1) / 65537f
        val u2 = (((n ushr 16) and 0xFFFF) + 1) / 65537f
        // anggaran Gaussian (jumlah dua seragam)
        return (u1 + u2 - 1f) * 1.2f
    }

    @Suppress("unused")
    private fun sigmoid(x: Float) = (1.0 / (1.0 + exp(-x.toDouble()))).toFloat()
}
