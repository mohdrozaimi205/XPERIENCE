package com.xperience.camera.process

import android.graphics.ColorMatrix
import com.xperience.camera.image.Looks
import kotlin.math.pow

/**
 * Anggaran Creative Look untuk PRATONTON langsung (matriks warna GPU).
 * Gambar akhir diproses dengan enjin warna penuh (lengkung, HSL, split-tone, butiran).
 */
object PreviewLook {
    fun matrix(lookId: String?, intensity: Float): ColorMatrix? {
        val t = Looks.byId(lookId)?.tone ?: return null
        val k = intensity.coerceIn(0f, 1.5f)
        val m = ColorMatrix()
        // saturasi / monokrom
        val sat = (1f + (t.saturation + t.vibrance * 0.5f) * k).coerceIn(0f, 2.5f) * (1f - t.mono * k).coerceIn(0f, 1f)
        val s = ColorMatrix().apply { setSaturation(sat) }
        if (t.mono > 0f) {
            val r = t.monoR; val g = t.monoG; val b = t.monoB
            val mono = ColorMatrix(floatArrayOf(r, g, b, 0f, 0f, r, g, b, 0f, 0f, r, g, b, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
            val mk = (t.mono * k).coerceIn(0f, 1f)
            val a = s.array; val bArr = mono.array
            val mixed = FloatArray(20) { a[it] * (1 - mk) + bArr[it] * mk }
            s.set(mixed)
        }
        m.postConcat(s)
        // suhu & tint
        val tr = 1f + 0.14f * t.temperature * k; val tb = 1f - 0.14f * t.temperature * k
        val tg = 1f - 0.07f * t.tint * k
        m.postConcat(ColorMatrix().apply { setScale(tr, tg, tb, 1f) })
        // pendedahan + kontras + fade
        val gain = 2f.pow(t.exposure * k * 0.8f)
        val c = 1f + (t.contrast * 0.6f + (t.blacks * -0.2f)) * k
        val off = 128f * (1f - c) * gain + 255f * t.fade * 0.14f * k + 255f * t.shadows * 0.05f * k
        val cg = c * gain
        m.postConcat(ColorMatrix(floatArrayOf(cg, 0f, 0f, 0f, off, 0f, cg, 0f, 0f, off, 0f, 0f, cg, 0f, off, 0f, 0f, 0f, 1f, 0f)))
        // split-tone ringkas (warna bayang)
        if (t.shadowSat > 0f) {
            val h = Math.toRadians(t.shadowHue.toDouble())
            val amp = 18f * t.shadowSat * k
            val dr = (amp * kotlin.math.cos(h)).toFloat(); val db = (amp * kotlin.math.sin(h - 2.1)).toFloat()
            m.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, dr, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, db, 0f, 0f, 0f, 1f, 0f)))
        }
        return m
    }
}
