package com.xperience.camera.image

/**
 * Satu set parameter warna/tona. Digunakan oleh preset (Look) DAN oleh
 * pelaras manual pengguna. Semua nilai 0 = tiada perubahan.
 */
data class ToneSet(
    val exposure: Float = 0f,      // EV, -2..2
    val brightness: Float = 0f,    // -1..1
    val contrast: Float = 0f,      // -1..1
    val saturation: Float = 0f,    // -1..1
    val vibrance: Float = 0f,      // -1..1
    val temperature: Float = 0f,   // -1 (sejuk) .. 1 (panas)
    val tint: Float = 0f,          // -1 (hijau) .. 1 (magenta)
    val highlights: Float = 0f,    // -1..1
    val shadows: Float = 0f,       // -1..1
    val whites: Float = 0f,        // -1..1
    val blacks: Float = 0f,        // -1..1
    val fade: Float = 0f,          // 0..1 (angkat hitam, gaya filem matte)
    /** Lengkung tona induk: nilai y pada x = 0, .25, .5, .75, 1 (offset dari diagonal). */
    val curve: FloatArray = FloatArray(5),
    /** Lengkung per saluran (offset) untuk tona filem. */
    val curveR: FloatArray = FloatArray(5),
    val curveG: FloatArray = FloatArray(5),
    val curveB: FloatArray = FloatArray(5),
    /** HSL 8 jalur: merah, jingga, kuning, hijau, cyan, biru, ungu, magenta. [hue, sat, lum] x 8. */
    val hsl: FloatArray = FloatArray(24),
    /** Split toning: hue 0..360, kekuatan 0..1. */
    val shadowHue: Float = 0f, val shadowSat: Float = 0f,
    val highlightHue: Float = 0f, val highlightSat: Float = 0f,
    val splitBalance: Float = 0f,  // -1..1
    /** Monokrom: 0 = warna, 1 = hitam-putih penuh. Pencampur saluran r/g/b. */
    val mono: Float = 0f,
    val monoR: Float = 0.30f, val monoG: Float = 0.59f, val monoB: Float = 0.11f,
    /** Perlindungan warna kulit (0..1): kekalkan hue kulit semula jadi semasa gred warna. */
    val skinProtect: Float = 0f,
    val skinWarmth: Float = 0f,    // -1..1
    val greenBias: Float = 0f,     // -1..1 hijau realistik (kurang neon)
    // Kesan ruang (spatial)
    val clarity: Float = 0f,       // -1..1
    val texture: Float = 0f,       // -1..1
    val sharpness: Float = 0f,     // 0..1
    val grain: Float = 0f,         // 0..1
    val grainSize: Float = 1f,     // 0.5..3
    val vignette: Float = 0f,      // -1..1 (negatif = gelap di tepi)
    val glow: Float = 0f,          // 0..1 kilauan highlight (Hollywood / Soft Highkey)
    val halation: Float = 0f,      // 0..1 cahaya merah filem di sekitar highlight
) {
    fun isIdentity(): Boolean = this == IDENTITY

    override fun equals(other: Any?): Boolean {
        if (other !is ToneSet) return false
        return exposure == other.exposure && brightness == other.brightness && contrast == other.contrast &&
            saturation == other.saturation && vibrance == other.vibrance && temperature == other.temperature &&
            tint == other.tint && highlights == other.highlights && shadows == other.shadows &&
            whites == other.whites && blacks == other.blacks && fade == other.fade &&
            curve.contentEquals(other.curve) && curveR.contentEquals(other.curveR) &&
            curveG.contentEquals(other.curveG) && curveB.contentEquals(other.curveB) &&
            hsl.contentEquals(other.hsl) && shadowHue == other.shadowHue && shadowSat == other.shadowSat &&
            highlightHue == other.highlightHue && highlightSat == other.highlightSat &&
            splitBalance == other.splitBalance && mono == other.mono && monoR == other.monoR &&
            monoG == other.monoG && monoB == other.monoB && skinProtect == other.skinProtect &&
            skinWarmth == other.skinWarmth && greenBias == other.greenBias && clarity == other.clarity &&
            texture == other.texture && sharpness == other.sharpness && grain == other.grain &&
            grainSize == other.grainSize && vignette == other.vignette && glow == other.glow &&
            halation == other.halation
    }

    override fun hashCode(): Int {
        var h = exposure.hashCode()
        h = 31 * h + contrast.hashCode(); h = 31 * h + saturation.hashCode()
        h = 31 * h + temperature.hashCode(); h = 31 * h + mono.hashCode()
        h = 31 * h + curve.contentHashCode(); h = 31 * h + hsl.contentHashCode()
        h = 31 * h + clarity.hashCode(); h = 31 * h + grain.hashCode(); h = 31 * h + vignette.hashCode()
        return h
    }

    fun hasSpatial(): Boolean = clarity != 0f || texture != 0f || sharpness != 0f || grain != 0f ||
        vignette != 0f || glow != 0f || halation != 0f

    companion object {
        val IDENTITY = ToneSet()
        fun c(a: Float, b: Float, c: Float, d: Float, e: Float) = floatArrayOf(a, b, c, d, e)
    }
}

/** Gaya pencahayaan studio (koleksi Harcourt). */
enum class LightStyle(val label: String) {
    NONE("None"),
    REMBRANDT("Rembrandt"),
    BUTTERFLY("Butterfly"),
    SPLIT("Split"),
    SIDE("Dramatic Side"),
    SOFT_KEY("Soft Key"),
    CINEMATIC("Cinematic"),
}

enum class Collection(val title: String) {
    LEICA("LEICA COLLECTION"),
    HARCOURT("HARCOURT STUDIO"),
    SONY("SONY XPERIA"),
    CINEMATIC("CINEMATIC"),
    BW("BLACK & WHITE"),
    FILM("FILM COLLECTION"),
}

data class Look(
    val id: String,
    val name: String,
    val collection: Collection,
    val group: String,
    val tone: ToneSet,
    val light: LightStyle = LightStyle.NONE,
    val lightStrength: Float = 0f,     // 0..1
    val backgroundDark: Float = 0f,    // 0..1 gelapkan latar (perlu topeng subjek)
    val note: String = "",
)
