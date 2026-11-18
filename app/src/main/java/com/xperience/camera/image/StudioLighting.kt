package com.xperience.camera.image

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Pencahayaan studio (Harcourt): membina "peta cahaya" berarah berdasarkan
 * kedudukan muka (ML Kit) dan topeng subjek, kemudian menggelapkan latar
 * seperti latar studio kelabu-hitam dengan halo lembut di belakang kepala.
 */
object StudioLighting {

    data class Params(
        val style: LightStyle,
        val strength: Float,      // 0..1.5
        val backgroundDark: Float // 0..1
    )

    /**
     * @param mask topeng subjek 0..1 pada saiz imej (boleh null)
     * @param faces kotak muka dinormalkan 0..1
     */
    suspend fun apply(img: FloatImage, mask: FloatArray?, faces: List<RectF>, p: Params) {
        if ((p.style == LightStyle.NONE || p.strength <= 0f) && p.backgroundDark <= 0f) return
        val w = img.w; val h = img.h
        // Tentukan pusat & saiz muka utama
        val face = faces.maxByOrNull { it.width() * it.height() }
        var cx: Float; var cy: Float; var fr: Float
        if (face != null) {
            cx = face.centerX() * w; cy = face.centerY() * h
            fr = max(face.width() * w, face.height() * h) * 0.6f
        } else if (mask != null) {
            // guna kotak sempadan topeng, bahagian atas = kepala
            var minX = w; var maxX = 0; var minY = h; var maxY = 0; var any = false
            for (y in 0 until h step 2) for (x in 0 until w step 2) {
                if (mask[y * w + x] > 0.5f) {
                    any = true
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                }
            }
            if (any) {
                val bw = (maxX - minX).toFloat()
                cx = (minX + maxX) / 2f; fr = bw * 0.35f; cy = minY + fr * 1.1f
            } else { cx = w / 2f; cy = h * 0.4f; fr = max(w, h) * 0.18f }
        } else { cx = w / 2f; cy = h * 0.4f; fr = max(w, h) * 0.18f }
        fr = max(fr, 8f)

        val (dx, dy) = when (p.style) {
            LightStyle.REMBRANDT -> -0.72f to -0.69f
            LightStyle.BUTTERFLY -> 0f to -1f
            LightStyle.SPLIT -> -1f to 0f
            LightStyle.SIDE -> -0.98f to -0.2f
            LightStyle.SOFT_KEY -> -0.6f to -0.8f
            LightStyle.CINEMATIC -> 0.89f to -0.45f
            LightStyle.NONE -> 0f to 0f
        }
        val s = p.strength
        parallelRows(h) { y0, y1 ->
            for (y in y0 until y1) {
                for (x in 0 until w) {
                    val i = y * w + x
                    val px = (x - cx) / fr; val py = (y - cy) / fr
                    val dist = sqrt(px * px + py * py)
                    // komponen ke arah cahaya (positif = menghadap lampu)
                    val u = px * dx + py * dy
                    val radial = 1f - smoothstep(0f, 2.6f, dist)
                    val m: Float = when (p.style) {
                        LightStyle.REMBRANDT -> 0.55f * tanh(1.4f * u) + 0.40f * radial - 0.18f
                        LightStyle.BUTTERFLY -> 0.30f * tanh(1.2f * u) + 0.65f * radial - 0.28f
                        LightStyle.SPLIT -> 0.90f * tanh(3.5f * u)
                        LightStyle.SIDE -> 0.75f * tanh(2.4f * u) + 0.2f * radial - 0.12f
                        LightStyle.SOFT_KEY -> 0.25f * tanh(1.0f * u) + 0.45f * radial - 0.12f
                        LightStyle.CINEMATIC -> 0.50f * tanh(1.6f * u) + 0.38f * radial - 0.2f
                        LightStyle.NONE -> 0f
                    }
                    val sub = mask?.get(i) ?: 1f
                    var f = 1f + s * 0.55f * m * (0.35f + 0.65f * sub)
                    if (p.backgroundDark > 0f && mask != null) {
                        // latar studio: gelap dengan halo lembut di belakang kepala
                        val halo = 1f - smoothstep(0f, 4.2f, dist)
                        val bg = 1f - p.backgroundDark * (0.92f - 0.42f * halo)
                        f *= lerp(bg, 1f, sub)
                    }
                    img.r[i] = clamp01(img.r[i] * f); img.g[i] = clamp01(img.g[i] * f); img.b[i] = clamp01(img.b[i] * f)
                }
            }
        }
    }
}
