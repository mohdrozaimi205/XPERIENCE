package com.xperience.camera.image

import com.xperience.camera.image.ToneSet.Companion.c

/**
 * Semua preset LEICA FRAME. Ini adalah profil ASLI yang diilhamkan oleh estetika
 * fotografi umum (Leica, Harcourt, Sony Xperia/Alpha) — BUKAN preset rasmi atau berlesen.
 * Setiap profil menggunakan parameter berbeza (lengkung, HSL, split-tone, pencahayaan).
 */
object Looks {

    // HSL helper: senarai pasangan (jalur -> [hue, sat, lum])
    private fun hsl(vararg e: Pair<Int, FloatArray>): FloatArray {
        val a = FloatArray(24)
        for ((band, v) in e) { a[band * 3] = v[0]; a[band * 3 + 1] = v[1]; a[band * 3 + 2] = v[2] }
        return a
    }
    private fun v(h: Float, s: Float, l: Float) = floatArrayOf(h, s, l)
    private const val RED = 0; private const val ORANGE = 1; private const val YELLOW = 2; private const val GREEN = 3
    private const val AQUA = 4; private const val BLUE = 5; private const val PURPLE = 6; private const val MAGENTA = 7

    // ------------------------------------------------------------------ LEICA
    private val leica = listOf(
        Look("leica_natural", "Natural Leica Colour", Collection.LEICA, "Leica",
            ToneSet(contrast = 0.08f, saturation = -0.04f, vibrance = 0.10f, highlights = -0.15f, shadows = 0.08f,
                curve = c(0f, -0.015f, 0f, 0.01f, 0f), skinProtect = 0.6f, greenBias = -0.15f,
                hsl = hsl(GREEN to v(-4f, -0.15f, -0.05f), BLUE to v(0f, -0.05f, -0.06f)), clarity = 0.08f)),
        Look("leica_standard", "Leica Standard", Collection.LEICA, "Leica",
            ToneSet(contrast = 0.16f, saturation = 0.02f, vibrance = 0.08f, highlights = -0.20f, blacks = -0.06f,
                curve = c(0f, -0.03f, 0f, 0.025f, 0f), skinProtect = 0.5f, clarity = 0.12f, vignette = -0.12f,
                hsl = hsl(RED to v(0f, 0.08f, -0.04f), GREEN to v(-6f, -0.10f, -0.08f)))),
        Look("leica_vivid", "Leica Vivid", Collection.LEICA, "Leica",
            ToneSet(contrast = 0.24f, saturation = 0.20f, vibrance = 0.22f, highlights = -0.25f, shadows = 0.05f,
                blacks = -0.10f, curve = c(0f, -0.04f, 0f, 0.04f, 0f), skinProtect = 0.7f, clarity = 0.15f,
                hsl = hsl(RED to v(0f, 0.15f, -0.06f), BLUE to v(-4f, 0.18f, -0.10f), YELLOW to v(-3f, 0.10f, 0f)),
                vignette = -0.15f)),
        Look("leica_classic", "Leica Classic", Collection.LEICA, "Leica",
            ToneSet(contrast = 0.20f, saturation = -0.12f, highlights = -0.30f, shadows = -0.05f, blacks = -0.08f,
                temperature = 0.06f, curve = c(0.01f, -0.035f, -0.01f, 0.03f, -0.01f),
                curveB = c(0.02f, 0f, -0.01f, -0.015f, -0.02f), skinProtect = 0.4f, clarity = 0.18f,
                hsl = hsl(GREEN to v(8f, -0.25f, -0.10f), BLUE to v(6f, -0.20f, -0.08f), RED to v(2f, -0.05f, -0.05f)),
                vignette = -0.22f, grain = 0.08f)),
        Look("leica_warm", "Leica Warm", Collection.LEICA, "Leica",
            ToneSet(contrast = 0.12f, temperature = 0.22f, tint = 0.03f, vibrance = 0.08f, highlights = -0.18f,
                shadows = 0.06f, highlightHue = 40f, highlightSat = 0.12f, shadowHue = 25f, shadowSat = 0.06f,
                skinProtect = 0.5f, skinWarmth = 0.15f, curve = c(0f, -0.02f, 0f, 0.02f, 0f), vignette = -0.1f)),
        Look("leica_cool", "Leica Cool", Collection.LEICA, "Leica",
            ToneSet(contrast = 0.14f, temperature = -0.20f, tint = -0.02f, saturation = -0.06f, highlights = -0.18f,
                shadowHue = 210f, shadowSat = 0.12f, highlightHue = 195f, highlightSat = 0.04f, skinProtect = 0.6f,
                curve = c(0f, -0.025f, 0f, 0.02f, 0f), hsl = hsl(BLUE to v(-6f, 0.10f, -0.06f), AQUA to v(0f, 0.08f, 0f)))),
        Look("leica_monochrom", "Leica Monochrom Inspired", Collection.LEICA, "Leica",
            ToneSet(mono = 1f, monoR = 0.36f, monoG = 0.52f, monoB = 0.12f, contrast = 0.18f, highlights = -0.25f,
                shadows = 0.10f, whites = 0.05f, curve = c(0f, -0.03f, 0f, 0.035f, 0f), clarity = 0.22f,
                texture = 0.15f, grain = 0.10f, grainSize = 0.8f, vignette = -0.10f)),
        Look("leica_street", "Leica Street", Collection.LEICA, "Leica",
            ToneSet(contrast = 0.30f, saturation = -0.22f, highlights = -0.22f, shadows = -0.10f, blacks = -0.14f,
                temperature = 0.04f, curve = c(0f, -0.05f, -0.01f, 0.04f, 0f), clarity = 0.28f, texture = 0.12f,
                hsl = hsl(GREEN to v(10f, -0.35f, -0.12f), BLUE to v(0f, -0.15f, -0.10f), YELLOW to v(-4f, -0.15f, 0f)),
                vignette = -0.30f, grain = 0.12f)),
        Look("leica_cinematic", "Leica Cinematic", Collection.LEICA, "Leica",
            ToneSet(contrast = 0.18f, saturation = -0.10f, highlights = -0.30f, shadows = 0.06f, fade = 0.06f,
                shadowHue = 195f, shadowSat = 0.14f, highlightHue = 35f, highlightSat = 0.12f, skinProtect = 0.7f,
                curve = c(0.02f, -0.02f, 0f, 0.02f, -0.02f), clarity = 0.10f, vignette = -0.22f, halation = 0.15f)),
        Look("leica_portrait", "Leica Portrait", Collection.LEICA, "Leica",
            ToneSet(contrast = 0.06f, saturation = -0.06f, vibrance = 0.06f, highlights = -0.22f, shadows = 0.14f,
                temperature = 0.05f, skinProtect = 0.9f, skinWarmth = 0.10f, texture = -0.15f, clarity = -0.05f,
                hsl = hsl(ORANGE to v(-2f, -0.05f, 0.06f), RED to v(2f, -0.04f, 0.03f)), glow = 0.08f,
                curve = c(0.01f, -0.01f, 0.005f, 0.015f, -0.005f), vignette = -0.12f)),
        Look("leica_vintage", "Vintage Film", Collection.LEICA, "Leica",
            ToneSet(contrast = 0.04f, saturation = -0.18f, fade = 0.12f, temperature = 0.12f, highlights = -0.2f,
                curveR = c(0.02f, 0.01f, 0.01f, 0f, -0.02f), curveG = c(0.01f, 0f, 0f, 0f, -0.02f),
                curveB = c(0.05f, 0.015f, -0.02f, -0.03f, -0.06f), grain = 0.22f, grainSize = 1.4f,
                hsl = hsl(GREEN to v(12f, -0.25f, 0f), BLUE to v(-8f, -0.15f, 0.04f)), vignette = -0.25f, halation = 0.1f)),
        Look("leica_bw", "Classic Black & White", Collection.LEICA, "Leica",
            ToneSet(mono = 1f, monoR = 0.30f, monoG = 0.59f, monoB = 0.11f, contrast = 0.25f, blacks = -0.08f,
                curve = c(0f, -0.04f, 0f, 0.04f, 0f), clarity = 0.15f, grain = 0.12f, vignette = -0.15f)),
    )

    // ------------------------------------------------------------------ HARCOURT
    private fun h(id: String, name: String, group: String, tone: ToneSet, light: LightStyle, ls: Float, bg: Float) =
        Look(id, name, Collection.HARCOURT, group, tone, light, ls, bg)

    private val monoStudio = ToneSet(mono = 1f, monoR = 0.42f, monoG = 0.48f, monoB = 0.10f, skinProtect = 0f)

    private val harcourt = listOf(
        // A. CLASSIC
        h("hc_classic_bw", "Classic Black & White", "Classic Harcourt",
            monoStudio.copy(contrast = 0.30f, highlights = -0.15f, shadows = -0.05f, blacks = -0.12f,
                curve = c(0f, -0.045f, 0f, 0.045f, 0f), texture = -0.25f, glow = 0.12f, vignette = -0.35f),
            LightStyle.SOFT_KEY, 0.45f, 0.55f),
        h("hc_dramatic", "Dramatic Studio Portrait", "Classic Harcourt",
            monoStudio.copy(contrast = 0.42f, highlights = -0.10f, shadows = -0.20f, blacks = -0.20f,
                curve = c(0f, -0.07f, -0.01f, 0.05f, 0f), texture = -0.15f, clarity = 0.10f, vignette = -0.5f),
            LightStyle.REMBRANDT, 0.75f, 0.85f),
        h("hc_timeless", "Timeless Portrait", "Classic Harcourt",
            monoStudio.copy(contrast = 0.22f, highlights = -0.25f, shadows = 0.02f, fade = 0.04f,
                curve = c(0.01f, -0.03f, 0f, 0.035f, -0.01f), texture = -0.30f, glow = 0.18f, grain = 0.06f,
                vignette = -0.38f), LightStyle.BUTTERFLY, 0.5f, 0.6f),
        h("hc_hollywood", "Classic Hollywood", "Classic Harcourt",
            monoStudio.copy(contrast = 0.34f, highlights = -0.08f, whites = 0.12f, blacks = -0.15f,
                curve = c(0f, -0.05f, 0.01f, 0.06f, 0f), texture = -0.35f, glow = 0.35f, vignette = -0.45f,
                grain = 0.08f), LightStyle.BUTTERFLY, 0.7f, 0.8f),
        h("hc_elegant_mono", "Elegant Monochrome", "Classic Harcourt",
            monoStudio.copy(contrast = 0.16f, highlights = -0.20f, shadows = 0.08f,
                curve = c(0.005f, -0.02f, 0.005f, 0.025f, -0.01f), texture = -0.2f, glow = 0.1f, vignette = -0.28f,
                shadowHue = 220f, shadowSat = 0.04f), LightStyle.SOFT_KEY, 0.35f, 0.45f),
        // B. LIGHTING
        h("hc_rembrandt", "Rembrandt Lighting", "Harcourt Lighting",
            monoStudio.copy(contrast = 0.32f, shadows = -0.15f, blacks = -0.12f, curve = c(0f, -0.05f, 0f, 0.04f, 0f),
                texture = -0.2f, vignette = -0.42f), LightStyle.REMBRANDT, 0.9f, 0.75f),
        h("hc_butterfly", "Butterfly Lighting", "Harcourt Lighting",
            monoStudio.copy(contrast = 0.26f, highlights = -0.10f, curve = c(0f, -0.035f, 0.01f, 0.05f, 0f),
                texture = -0.3f, glow = 0.2f, vignette = -0.38f), LightStyle.BUTTERFLY, 0.9f, 0.7f),
        h("hc_split", "Split Lighting", "Harcourt Lighting",
            monoStudio.copy(contrast = 0.40f, shadows = -0.25f, blacks = -0.18f, curve = c(0f, -0.07f, 0f, 0.05f, 0f),
                texture = -0.12f, vignette = -0.45f), LightStyle.SPLIT, 1f, 0.85f),
        h("hc_side", "Dramatic Side Lighting", "Harcourt Lighting",
            monoStudio.copy(contrast = 0.46f, shadows = -0.30f, blacks = -0.22f, whites = 0.08f,
                curve = c(0f, -0.08f, -0.01f, 0.06f, 0f), clarity = 0.12f, vignette = -0.55f),
            LightStyle.SIDE, 1f, 0.9f),
        h("hc_softkey", "Soft Key Lighting", "Harcourt Lighting",
            monoStudio.copy(contrast = 0.12f, highlights = -0.18f, shadows = 0.12f, curve = c(0.01f, -0.015f, 0.01f, 0.02f, -0.005f),
                texture = -0.35f, glow = 0.22f, vignette = -0.25f), LightStyle.SOFT_KEY, 0.8f, 0.4f),
        h("hc_cine_light", "Cinematic Portrait Lighting", "Harcourt Lighting",
            monoStudio.copy(contrast = 0.36f, shadows = -0.12f, blacks = -0.15f, fade = 0.03f,
                curve = c(0.01f, -0.06f, 0f, 0.05f, -0.01f), texture = -0.2f, glow = 0.15f, vignette = -0.5f, grain = 0.07f),
            LightStyle.CINEMATIC, 0.9f, 0.85f),
        // C. TONAL
        h("hc_deep_black", "Deep Black", "Harcourt Tonal",
            monoStudio.copy(contrast = 0.30f, blacks = -0.35f, shadows = -0.25f, curve = c(0f, -0.08f, -0.02f, 0.03f, 0f),
                texture = -0.2f, vignette = -0.6f), LightStyle.REMBRANDT, 0.6f, 1f),
        h("hc_silver_hl", "Silver Highlights", "Harcourt Tonal",
            monoStudio.copy(contrast = 0.20f, highlights = 0.10f, whites = 0.15f, curve = c(0f, -0.02f, 0.02f, 0.06f, 0f),
                texture = -0.25f, glow = 0.30f, shadowHue = 215f, shadowSat = 0.05f, vignette = -0.3f),
            LightStyle.BUTTERFLY, 0.5f, 0.6f),
        h("hc_soft_skin", "Soft Skin Contrast", "Harcourt Tonal",
            monoStudio.copy(contrast = 0.18f, highlights = -0.2f, shadows = 0.05f, curve = c(0f, -0.025f, 0.01f, 0.03f, 0f),
                texture = -0.55f, glow = 0.2f, vignette = -0.35f), LightStyle.SOFT_KEY, 0.6f, 0.6f),
        h("hc_rich_shadow", "Rich Shadow", "Harcourt Tonal",
            monoStudio.copy(contrast = 0.28f, shadows = -0.08f, blacks = -0.10f, curve = c(0.01f, -0.06f, -0.025f, 0.03f, 0f),
                clarity = 0.1f, texture = -0.15f, vignette = -0.45f), LightStyle.REMBRANDT, 0.7f, 0.8f),
        h("hc_high_contrast", "High Contrast Monochrome", "Harcourt Tonal",
            monoStudio.copy(contrast = 0.60f, blacks = -0.25f, whites = 0.15f, curve = c(0f, -0.09f, 0f, 0.09f, 0f),
                clarity = 0.2f, vignette = -0.4f, grain = 0.1f), LightStyle.SIDE, 0.6f, 0.9f),
        h("hc_gelatin", "Classic Silver Gelatin", "Harcourt Tonal",
            monoStudio.copy(contrast = 0.22f, highlights = -0.15f, fade = 0.03f, curve = c(0.01f, -0.035f, 0f, 0.035f, -0.015f),
                shadowHue = 30f, shadowSat = 0.06f, highlightHue = 45f, highlightSat = 0.04f, texture = -0.2f,
                grain = 0.18f, grainSize = 0.9f, vignette = -0.4f), LightStyle.SOFT_KEY, 0.5f, 0.65f),
        // D. COLOUR
        h("hc_warm_studio", "Warm Studio Portrait", "Harcourt Colour",
            ToneSet(contrast = 0.22f, saturation = -0.18f, temperature = 0.15f, highlights = -0.2f, shadows = -0.08f,
                skinProtect = 0.8f, skinWarmth = 0.15f, texture = -0.3f, glow = 0.15f, vignette = -0.4f,
                curve = c(0f, -0.04f, 0f, 0.03f, 0f)), LightStyle.REMBRANDT, 0.6f, 0.75f),
        h("hc_elegant_warm", "Elegant Warm Tone", "Harcourt Colour",
            ToneSet(contrast = 0.16f, saturation = -0.28f, temperature = 0.10f, highlightHue = 40f, highlightSat = 0.15f,
                shadowHue = 20f, shadowSat = 0.10f, skinProtect = 0.6f, texture = -0.3f, glow = 0.12f, vignette = -0.35f,
                curve = c(0.01f, -0.03f, 0f, 0.025f, -0.01f)), LightStyle.SOFT_KEY, 0.5f, 0.6f),
        h("hc_brown", "Classic Brown", "Harcourt Colour",
            monoStudio.copy(mono = 0.92f, contrast = 0.22f, highlightHue = 38f, highlightSat = 0.18f, shadowHue = 22f,
                shadowSat = 0.30f, curve = c(0f, -0.04f, 0f, 0.03f, 0f), texture = -0.2f, vignette = -0.42f),
            LightStyle.REMBRANDT, 0.55f, 0.7f),
        h("hc_vintage", "Vintage Studio", "Harcourt Colour",
            ToneSet(contrast = 0.10f, saturation = -0.40f, fade = 0.10f, temperature = 0.12f, highlightHue = 45f,
                highlightSat = 0.10f, shadowHue = 30f, shadowSat = 0.12f, grain = 0.18f, texture = -0.2f,
                vignette = -0.45f), LightStyle.SOFT_KEY, 0.45f, 0.6f),
        h("hc_champagne", "Soft Champagne", "Harcourt Colour",
            ToneSet(contrast = 0.04f, saturation = -0.25f, exposure = 0.12f, temperature = 0.08f, highlightHue = 48f,
                highlightSat = 0.14f, shadowHue = 35f, shadowSat = 0.05f, fade = 0.05f, skinProtect = 0.7f,
                texture = -0.4f, glow = 0.28f, vignette = -0.2f), LightStyle.BUTTERFLY, 0.45f, 0.35f),
    )

    // ------------------------------------------------------------------ SONY XPERIA
    private fun s(id: String, name: String, group: String, tone: ToneSet, note: String = "") =
        Look(id, name, Collection.SONY, group, tone, note = note)

    private val sony = listOf(
        // A. NATURAL COLOUR — warna realistik, hijau terkawal, kulit semula jadi
        s("xp_natural", "Xperia Natural", "Natural Colour",
            ToneSet(contrast = 0.10f, vibrance = 0.14f, highlights = -0.22f, shadows = 0.18f, skinProtect = 0.8f,
                greenBias = -0.25f, curve = c(0f, -0.015f, 0f, 0.015f, -0.005f), clarity = 0.06f)),
        s("xp_authentic", "Xperia Authentic Colour", "Natural Colour",
            ToneSet(contrast = 0.12f, saturation = -0.06f, highlights = -0.24f, shadows = 0.12f, skinProtect = 0.9f,
                temperature = 0.03f, clarity = 0.05f,
                greenBias = -0.35f, hsl = hsl(GREEN to v(6f, -0.12f, -0.04f), YELLOW to v(-4f, -0.08f, 0f),
                    BLUE to v(2f, -0.04f, -0.02f)))),
        s("xp_skin", "Realistic Skin Tone", "Natural Colour",
            ToneSet(contrast = 0.06f, saturation = -0.08f, highlights = -0.22f, shadows = 0.16f, skinProtect = 1f,
                skinWarmth = 0.08f, texture = -0.12f, hsl = hsl(ORANGE to v(-3f, -0.08f, 0.04f), RED to v(3f, -0.06f, 0.02f)))),
        s("xp_balanced", "Balanced Contrast", "Natural Colour",
            ToneSet(contrast = 0.14f, highlights = -0.20f, shadows = 0.14f, blacks = -0.04f, skinProtect = 0.7f,
                greenBias = -0.2f, curve = c(0f, -0.025f, 0f, 0.02f, 0f), clarity = 0.08f)),
        s("xp_dr", "Natural Dynamic Range", "Natural Colour",
            ToneSet(contrast = -0.04f, highlights = -0.40f, shadows = 0.34f, whites = -0.05f, blacks = 0.04f,
                skinProtect = 0.7f, greenBias = -0.2f, clarity = 0.12f)),
        // B. CREATIVE LOOK — ciri umum seperti didokumenkan (ST/NT/VV/FL/IN/SH Xperia 1 VII; VV2/FL2/FL3/PT dari kamera Alpha)
        s("cl_st", "ST", "Creative Look",
            ToneSet(contrast = 0.10f, saturation = 0.05f, highlights = -0.1f, skinProtect = 0.6f), "Standard: seimbang"),
        s("cl_pt", "PT", "Creative Look",
            ToneSet(contrast = -0.05f, saturation = -0.06f, highlights = -0.15f, shadows = 0.10f, skinProtect = 1f,
                skinWarmth = 0.06f, texture = -0.25f, sharpness = 0f), "Portrait: kulit lembut, kontras rendah"),
        s("cl_nt", "NT", "Creative Look",
            ToneSet(contrast = -0.10f, saturation = -0.25f, highlights = -0.1f, shadows = 0.08f, sharpness = 0f,
                texture = -0.1f), "Neutral: warna & ketajaman ditekan, sesuai untuk diedit"),
        s("cl_vv", "VV", "Creative Look",
            ToneSet(contrast = 0.25f, saturation = 0.30f, vibrance = 0.15f, blacks = -0.08f, skinProtect = 0.4f,
                curve = c(0f, -0.04f, 0f, 0.04f, 0f)), "Vivid: tepu & kontras tinggi"),
        s("cl_vv2", "VV2", "Creative Look",
            ToneSet(contrast = 0.15f, saturation = 0.20f, vibrance = 0.25f, exposure = 0.08f, highlights = -0.15f,
                shadows = 0.10f, skinProtect = 0.6f, clarity = 0.1f), "Vivid 2: cerah, jelas, tepu tetapi bersih"),
        s("cl_fl", "FL", "Creative Look",
            ToneSet(contrast = 0.12f, saturation = -0.20f, temperature = -0.06f, fade = 0.06f, highlights = -0.2f,
                shadowHue = 190f, shadowSat = 0.10f, curve = c(0.02f, -0.04f, -0.01f, 0.02f, -0.02f), skinProtect = 0.6f,
                hsl = hsl(GREEN to v(12f, -0.20f, -0.05f), BLUE to v(-6f, -0.05f, -0.05f))),
            "Film: tenang, kontras dalam, warna redup"),
        s("cl_fl2", "FL2", "Creative Look",
            ToneSet(contrast = 0.16f, saturation = -0.12f, temperature = 0.05f, fade = 0.05f, highlights = -0.25f,
                highlightHue = 40f, highlightSat = 0.08f, shadowHue = 200f, shadowSat = 0.06f, skinProtect = 0.6f,
                curve = c(0.015f, -0.04f, 0f, 0.025f, -0.02f), halation = 0.08f), "Film 2: hangat, nostalgia"),
        s("cl_fl3", "FL3", "Creative Look",
            ToneSet(contrast = 0.20f, saturation = -0.08f, temperature = -0.04f, highlights = -0.25f, blacks = -0.08f,
                shadowHue = 180f, shadowSat = 0.14f, highlightHue = 30f, highlightSat = 0.06f, skinProtect = 0.7f,
                curve = c(0f, -0.05f, 0f, 0.035f, -0.01f), hsl = hsl(GREEN to v(-8f, -0.10f, -0.08f))),
            "Film 3: kontras sinematik, bayang teal"),
        s("cl_in", "IN", "Creative Look",
            ToneSet(contrast = -0.12f, saturation = -0.22f, fade = 0.14f, temperature = 0.04f, highlights = -0.18f,
                curveB = c(0.03f, 0.01f, 0f, -0.01f, -0.03f), grain = 0.06f), "Instant: matte, kontras rendah, redup"),
        s("cl_sh", "SH", "Creative Look",
            ToneSet(exposure = 0.30f, contrast = -0.18f, saturation = -0.10f, highlights = -0.25f, shadows = 0.25f,
                fade = 0.05f, temperature = 0.03f, glow = 0.18f, skinProtect = 0.7f), "Soft Highkey: cerah, lembut, lutsinar"),
        // C. PROFESSIONAL PORTRAIT
        s("xp_portrait", "Xperia Portrait", "Professional Portrait",
            ToneSet(contrast = 0.06f, highlights = -0.18f, shadows = 0.10f, skinProtect = 1f, skinWarmth = 0.05f,
                texture = -0.20f, vignette = -0.10f)),
        s("xp_nat_skin", "Natural Skin Portrait", "Professional Portrait",
            ToneSet(contrast = 0.02f, saturation = -0.05f, highlights = -0.12f, shadows = 0.12f, skinProtect = 1f,
                texture = -0.10f, hsl = hsl(ORANGE to v(-2f, -0.10f, 0.05f)))),
        s("xp_soft_portrait", "Soft Portrait", "Professional Portrait",
            ToneSet(exposure = 0.10f, contrast = -0.12f, saturation = -0.08f, highlights = -0.2f, shadows = 0.18f,
                skinProtect = 1f, texture = -0.40f, glow = 0.15f)),
        s("xp_bokeh", "Professional Bokeh", "Professional Portrait",
            ToneSet(contrast = 0.10f, vibrance = 0.06f, highlights = -0.15f, skinProtect = 0.9f, clarity = 0.06f,
                vignette = -0.18f), "Paling sesuai digabung dengan AI PORTRAIT"),
        s("xp_cine_portrait", "Cinematic Portrait", "Professional Portrait",
            ToneSet(contrast = 0.18f, saturation = -0.12f, highlights = -0.28f, shadowHue = 195f, shadowSat = 0.12f,
                highlightHue = 32f, highlightSat = 0.08f, skinProtect = 0.9f, fade = 0.04f, vignette = -0.25f)),
        // D. PROFESSIONAL PHOTOGRAPHY
        s("xp_street", "Street Photography", "Professional Photography",
            ToneSet(contrast = 0.24f, saturation = -0.10f, highlights = -0.2f, blacks = -0.08f, clarity = 0.22f,
                greenBias = -0.2f, vignette = -0.2f)),
        s("xp_urban_night", "Urban Night", "Professional Photography",
            ToneSet(contrast = 0.18f, temperature = -0.10f, highlights = -0.35f, shadows = 0.12f, blacks = -0.10f,
                hsl = hsl(ORANGE to v(-4f, 0.12f, 0f), BLUE to v(-6f, 0.15f, -0.05f), AQUA to v(0f, 0.12f, 0f)),
                clarity = 0.12f, glow = 0.10f)),
        s("xp_landscape", "Landscape", "Professional Photography",
            ToneSet(contrast = 0.16f, vibrance = 0.18f, highlights = -0.30f, shadows = 0.18f, clarity = 0.20f,
                greenBias = -0.15f, hsl = hsl(BLUE to v(-4f, 0.12f, -0.10f), GREEN to v(4f, 0.05f, -0.04f)))),
        s("xp_golden", "Golden Hour", "Professional Photography",
            ToneSet(contrast = 0.10f, temperature = 0.24f, tint = 0.04f, vibrance = 0.12f, highlights = -0.25f,
                highlightHue = 38f, highlightSat = 0.14f, skinProtect = 0.6f, glow = 0.12f)),
        s("xp_lowlight", "Low Light", "Professional Photography",
            ToneSet(exposure = 0.35f, contrast = 0.05f, highlights = -0.35f, shadows = 0.35f, saturation = -0.05f,
                texture = -0.15f)),
        s("xp_cine_night", "Cinematic Night", "Professional Photography",
            ToneSet(contrast = 0.22f, temperature = -0.12f, saturation = -0.10f, highlights = -0.30f, blacks = -0.12f,
                shadowHue = 205f, shadowSat = 0.18f, highlightHue = 35f, highlightSat = 0.10f, halation = 0.18f,
                vignette = -0.3f)),
        s("xp_hdr", "Natural HDR", "Professional Photography",
            ToneSet(contrast = -0.05f, highlights = -0.55f, shadows = 0.45f, whites = 0.04f, blacks = -0.04f,
                vibrance = 0.08f, clarity = 0.25f, skinProtect = 0.6f)),
    )

    // ------------------------------------------------------------------ CINEMATIC
    private fun cin(id: String, name: String, tone: ToneSet) = Look(id, name, Collection.CINEMATIC, "Cinematic", tone)
    private val cinematic = listOf(
        cin("cn_teal_orange", "Teal & Orange", ToneSet(contrast = 0.20f, shadowHue = 190f, shadowSat = 0.25f,
            highlightHue = 30f, highlightSat = 0.18f, skinProtect = 0.8f, saturation = -0.05f, vignette = -0.2f,
            hsl = hsl(GREEN to v(30f, -0.3f, -0.1f), BLUE to v(-10f, 0f, -0.1f)))),
        cin("cn_bleach", "Bleach Bypass", ToneSet(contrast = 0.40f, saturation = -0.45f, highlights = -0.1f,
            blacks = -0.12f, clarity = 0.25f, grain = 0.08f, vignette = -0.25f)),
        cin("cn_blockbuster", "Blockbuster", ToneSet(contrast = 0.28f, saturation = 0.05f, temperature = 0.06f,
            shadowHue = 200f, shadowSat = 0.2f, highlights = -0.25f, clarity = 0.15f, vignette = -0.3f, halation = 0.1f)),
        cin("cn_noir_neon", "Noir Neon", ToneSet(contrast = 0.25f, temperature = -0.15f, tint = 0.10f,
            shadowHue = 260f, shadowSat = 0.2f, highlightHue = 320f, highlightSat = 0.1f, blacks = -0.15f, glow = 0.2f)),
        cin("cn_moody", "Moody Forest", ToneSet(contrast = 0.15f, exposure = -0.15f, saturation = -0.2f,
            hsl = hsl(GREEN to v(25f, -0.35f, -0.2f), YELLOW to v(-15f, -0.2f, -0.1f)), fade = 0.06f, vignette = -0.35f)),
        cin("cn_desert", "Desert Gold", ToneSet(contrast = 0.18f, temperature = 0.28f, saturation = -0.1f,
            highlightHue = 42f, highlightSat = 0.15f, fade = 0.04f, grain = 0.06f)),
    )

    // ------------------------------------------------------------------ BLACK & WHITE
    private fun bw(id: String, name: String, tone: ToneSet) = Look(id, name, Collection.BW, "Black & White", tone.copy(mono = 1f))
    private val blackWhite = listOf(
        bw("bw_silver", "Silver", ToneSet(contrast = 0.15f, whites = 0.08f, curve = c(0f, -0.02f, 0.01f, 0.04f, 0f), clarity = 0.1f)),
        bw("bw_noir", "Hard Noir", ToneSet(contrast = 0.55f, blacks = -0.25f, curve = c(0f, -0.08f, 0f, 0.07f, 0f), vignette = -0.4f, grain = 0.15f)),
        bw("bw_matte", "Soft Matte", ToneSet(contrast = -0.05f, fade = 0.15f, highlights = -0.2f, grain = 0.08f)),
        bw("bw_red", "Red Filter", ToneSet(monoR = 0.85f, monoG = 0.15f, monoB = 0f, contrast = 0.25f, clarity = 0.15f)),
        bw("bw_green", "Green Filter", ToneSet(monoR = 0.2f, monoG = 0.75f, monoB = 0.05f, contrast = 0.15f)),
        bw("bw_highkey", "High Key", ToneSet(exposure = 0.45f, contrast = -0.1f, highlights = -0.2f, shadows = 0.3f, glow = 0.2f)),
    )

    // ------------------------------------------------------------------ FILM
    private fun film(id: String, name: String, tone: ToneSet) = Look(id, name, Collection.FILM, "Film", tone)
    private val filmLooks = listOf(
        film("fm_portrait400", "Portrait 400", ToneSet(contrast = -0.04f, saturation = -0.08f, temperature = 0.06f,
            highlights = -0.25f, shadows = 0.08f, fade = 0.04f, skinProtect = 0.8f, skinWarmth = 0.06f,
            curveB = c(0.02f, 0.01f, 0f, -0.01f, -0.02f), grain = 0.12f,
            hsl = hsl(GREEN to v(10f, -0.15f, 0.05f), BLUE to v(-6f, -0.1f, 0.05f)))),
        film("fm_gold200", "Gold 200", ToneSet(contrast = 0.10f, saturation = 0.08f, temperature = 0.18f,
            curveR = c(0.01f, 0.015f, 0.01f, 0f, -0.01f), curveB = c(0.02f, -0.01f, -0.03f, -0.03f, -0.03f),
            highlights = -0.2f, grain = 0.16f, hsl = hsl(YELLOW to v(-6f, 0.15f, 0f)))),
        film("fm_slide50", "Slide 50", ToneSet(contrast = 0.30f, saturation = 0.25f, blacks = -0.12f,
            hsl = hsl(GREEN to v(-4f, 0.2f, -0.05f), BLUE to v(-6f, 0.2f, -0.12f)), grain = 0.05f, vignette = -0.15f)),
        film("fm_cine800t", "Cine 800T", ToneSet(contrast = 0.12f, temperature = -0.18f, tint = 0.04f,
            highlightHue = 20f, highlightSat = 0.08f, halation = 0.35f, grain = 0.2f, fade = 0.04f)),
        film("fm_pro160", "Pro 160 Cool", ToneSet(contrast = -0.06f, saturation = -0.12f, temperature = -0.06f,
            highlights = -0.2f, shadows = 0.12f, hsl = hsl(GREEN to v(15f, -0.1f, 0.05f)), grain = 0.1f)),
        film("fm_instant", "Instant Fade", ToneSet(contrast = -0.10f, saturation = -0.2f, fade = 0.2f,
            curveB = c(0.06f, 0.02f, -0.01f, -0.03f, -0.05f), curveG = c(0.02f, 0f, 0f, 0f, -0.02f),
            vignette = -0.3f, grain = 0.1f)),
    )

    val all: List<Look> = leica + harcourt + sony + cinematic + blackWhite + filmLooks

    fun byCollection(c: Collection) = all.filter { it.collection == c }
    fun byId(id: String?) = all.firstOrNull { it.id == id }
}
