package com.xperience.camera.camera

import android.hardware.camera2.CameraMetadata
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToLong

enum class ShootMode(val label: String, val desc: String) {
    AUTO("AUTO", "Semua tetapan automatik"),
    P("P", "Program Auto — kamera kawal pendedahan"),
    S("S", "Shutter Priority — anda pilih shutter"),
    M("M", "Manual — kawalan penuh"),
    MR("MR", "Memory Recall — panggil tetapan simpanan"),
}

enum class FocusMode(val label: String) { AF_S("AF-S"), AF_C("AF-C"), MF("MF") }

enum class Metering(val label: String, val icon: String) {
    MULTI("Multi", "◉"), CENTER("Center", "◎"), SPOT("Spot", "•"), HIGHLIGHT("Highlight", "☀")
}

enum class WbMode(val label: String, val short: String, val awb: Int, val kelvin: Int = 0) {
    AUTO("Auto", "AWB", CameraMetadata.CONTROL_AWB_MODE_AUTO),
    DAYLIGHT("Daylight", "☀", CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT, 5500),
    CLOUDY("Cloudy", "☁", CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, 6500),
    SHADE("Shade", "⛱", CameraMetadata.CONTROL_AWB_MODE_SHADE, 7500),
    FLUORESCENT("Fluorescent", "FL", CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT, 4000),
    INCANDESCENT("Incandescent", "IN", CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT, 3200),
    FLASH("Flash", "⚡WB", CameraMetadata.CONTROL_AWB_MODE_OFF, 5800),
    KELVIN("Custom Kelvin", "K", CameraMetadata.CONTROL_AWB_MODE_OFF),
}

enum class FileFormat(val label: String) { JPEG("JPEG"), RAW("RAW"), RAW_JPEG("RAW+J") }
enum class Aspect(val label: String, val w: Int, val h: Int) { R4_3("4:3", 3, 4), R16_9("16:9", 9, 16), R1_1("1:1", 1, 1) }
enum class Drive(val label: String) {
    SINGLE("Single"), BURST("Continuous"), BURST_HI("Hi-Speed Burst"), TIMER3("Timer 3s"), TIMER10("Timer 10s"), INTERVAL("Interval")
}
enum class FlashMode(val label: String) { OFF("Off"), AUTO("Auto"), ON("On"), TORCH("Torch") }
enum class Grid(val label: String) { OFF("Off"), THIRDS("3×3"), SQUARE("Square"), DIAGONAL("Diagonal") }
enum class Dro(val label: String) { OFF("Off"), AUTO("DRO Auto"), STRONG("DRO Lv5") }

/** Semua tetapan kamera (disimpan + boleh dimasukkan ke MR1/2/3). */
data class CamSettings(
    val mode: ShootMode = ShootMode.AUTO,
    val recallMode: ShootMode = ShootMode.M,  // mod sebenar bila MR aktif
    val mrSlot: Int = 0,
    val iso: Int = 0,                     // 0 = AUTO
    val shutterNs: Long = 8_000_000L,     // 1/125
    val ev: Float = 0f,
    val wb: WbMode = WbMode.AUTO,
    val kelvin: Int = 5500,
    val focus: FocusMode = FocusMode.AF_C,
    val focusPos: Float = 0f,             // 0 = infiniti, 1 = paling dekat
    val metering: Metering = Metering.MULTI,
    val format: FileFormat = FileFormat.JPEG,
    val aspect: Aspect = Aspect.R4_3,
    val drive: Drive = Drive.SINGLE,
    val flash: FlashMode = FlashMode.OFF,
    val grid: Grid = Grid.THIRDS,
    val histogram: Boolean = true,
    val level: Boolean = true,
    val peaking: Boolean = true,
    val highlightWarn: Boolean = false,
    val shadowWarn: Boolean = false,
    val shutterSound: Boolean = true,
    val faceAf: Boolean = true,
    val lookId: String? = null,
    val lookIntensity: Float = 1f,
    val bokeh: Boolean = false,
    val aperture: Float = 1.8f,
    val bokehStyle: String = "NATURAL",
    val bokehIntensity: Float = 1f,
    val dro: Dro = Dro.OFF,
    val lensId: String = "",              // id kanta pilihan (kosong = utama)
    val zoom: Float = 1f,
    val front: Boolean = false,
    val burstCount: Int = 10,
    val intervalSec: Int = 5,
    val intervalCount: Int = 20,
    val jpegQuality: Int = 95,
    val keepOriginal: Boolean = false,
    val volumeShutter: Boolean = true,
) {
    /** Mod yang sebenarnya berkuat kuasa. */
    val effective: ShootMode get() = if (mode == ShootMode.MR) recallMode else mode

    fun toJson(): JSONObject = JSONObject().apply {
        put("mode", mode.name); put("recallMode", recallMode.name); put("mrSlot", mrSlot)
        put("iso", iso); put("shutterNs", shutterNs); put("ev", ev.toDouble()); put("wb", wb.name); put("kelvin", kelvin)
        put("focus", focus.name); put("focusPos", focusPos.toDouble()); put("metering", metering.name)
        put("format", format.name); put("aspect", aspect.name); put("drive", drive.name); put("flash", flash.name)
        put("grid", grid.name); put("histogram", histogram); put("level", level); put("peaking", peaking)
        put("highlightWarn", highlightWarn); put("shadowWarn", shadowWarn); put("shutterSound", shutterSound)
        put("faceAf", faceAf); put("lookId", lookId ?: ""); put("lookIntensity", lookIntensity.toDouble())
        put("bokeh", bokeh); put("aperture", aperture.toDouble()); put("bokehStyle", bokehStyle)
        put("bokehIntensity", bokehIntensity.toDouble()); put("dro", dro.name); put("lensId", lensId)
        put("zoom", zoom.toDouble()); put("front", front); put("burstCount", burstCount)
        put("intervalSec", intervalSec); put("intervalCount", intervalCount); put("jpegQuality", jpegQuality)
        put("keepOriginal", keepOriginal); put("volumeShutter", volumeShutter)
    }

    companion object {
        private inline fun <reified E : Enum<E>> JSONObject.enum(k: String, d: E): E =
            try { enumValueOf<E>(optString(k, d.name)) } catch (_: Exception) { d }

        fun fromJson(j: JSONObject): CamSettings {
            val d = CamSettings()
            return CamSettings(
                mode = j.enum("mode", d.mode), recallMode = j.enum("recallMode", d.recallMode),
                mrSlot = j.optInt("mrSlot", 0), iso = j.optInt("iso", d.iso),
                shutterNs = j.optLong("shutterNs", d.shutterNs), ev = j.optDouble("ev", 0.0).toFloat(),
                wb = j.enum("wb", d.wb), kelvin = j.optInt("kelvin", d.kelvin), focus = j.enum("focus", d.focus),
                focusPos = j.optDouble("focusPos", 0.0).toFloat(), metering = j.enum("metering", d.metering),
                format = j.enum("format", d.format), aspect = j.enum("aspect", d.aspect), drive = j.enum("drive", d.drive),
                flash = j.enum("flash", d.flash), grid = j.enum("grid", d.grid),
                histogram = j.optBoolean("histogram", d.histogram), level = j.optBoolean("level", d.level),
                peaking = j.optBoolean("peaking", d.peaking), highlightWarn = j.optBoolean("highlightWarn", false),
                shadowWarn = j.optBoolean("shadowWarn", false), shutterSound = j.optBoolean("shutterSound", true),
                faceAf = j.optBoolean("faceAf", true), lookId = j.optString("lookId", "").ifEmpty { null },
                lookIntensity = j.optDouble("lookIntensity", 1.0).toFloat(), bokeh = j.optBoolean("bokeh", false),
                aperture = j.optDouble("aperture", 1.8).toFloat(), bokehStyle = j.optString("bokehStyle", "NATURAL"),
                bokehIntensity = j.optDouble("bokehIntensity", 1.0).toFloat(), dro = j.enum("dro", d.dro),
                lensId = j.optString("lensId", ""), zoom = j.optDouble("zoom", 1.0).toFloat(),
                front = j.optBoolean("front", false), burstCount = j.optInt("burstCount", 10),
                intervalSec = j.optInt("intervalSec", 5), intervalCount = j.optInt("intervalCount", 20),
                jpegQuality = j.optInt("jpegQuality", 95), keepOriginal = j.optBoolean("keepOriginal", false),
                volumeShutter = j.optBoolean("volumeShutter", true),
            )
        }
    }
}

/** Jadual nilai gaya kamera Alpha (1/3 stop). */
object Stops {
    private const val S = 1_000_000_000L

    val SHUTTER: List<Long> = listOf(
        8000, 6400, 5000, 4000, 3200, 2500, 2000, 1600, 1250, 1000, 800, 640, 500, 400, 320, 250, 200, 160, 125,
        100, 80, 60, 50, 40, 30, 25, 20, 15, 13, 10, 8, 6, 5, 4,
    ).map { S / it } + listOf(0.3, 0.4, 0.5, 0.6, 0.8, 1.0, 1.3, 1.6, 2.0, 2.5, 3.2, 4.0, 5.0, 6.0, 8.0, 10.0, 13.0, 15.0, 20.0, 25.0, 30.0)
        .map { (it * S).roundToLong() }

    val ISO: List<Int> = listOf(25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250,
        1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800, 16000, 20000, 25600, 51200)

    val EV: List<Float> = (-9..9).map { it / 3f }

    val KELVIN: List<Int> = (2500..10000 step 100).toList()

    fun shutterLabel(ns: Long): String {
        if (ns <= 0) return "--"
        val sec = ns.toDouble() / S
        return if (sec >= 0.3) {
            val r = (sec * 10).roundToLong() / 10.0
            if (r == r.toLong().toDouble()) "${r.toLong()}\"" else "$r\""
        } else {
            val d = 1.0 / sec
            "1/" + when {
                d >= 100 -> ((d / 10).roundToLong() * 10).let { nearestNominal(it) }
                else -> d.roundToLong()
            }
        }
    }

    private fun nearestNominal(v: Long): Long {
        val nominal = longArrayOf(8000, 6400, 5000, 4000, 3200, 2500, 2000, 1600, 1250, 1000, 800, 640, 500, 400, 320, 250, 200, 160, 125, 100)
        return nominal.minByOrNull { abs(it - v) }?.takeIf { abs(it - v) < it * 0.12 } ?: v
    }

    fun evLabel(ev: Float): String {
        val t = Math.round(ev * 3)
        if (t == 0) return "±0.0"
        val sign = if (t > 0) "+" else "−"
        val a = abs(t)
        val whole = a / 3; val frac = a % 3
        return sign + whole + "." + (if (frac == 0) "0" else if (frac == 1) "3" else "7")
    }

    fun nearest(list: List<Long>, v: Long): Long = list.minByOrNull { abs(Math.log(it.toDouble()) - Math.log(v.toDouble().coerceAtLeast(1.0))) } ?: v
    fun nearestIso(list: List<Int>, v: Int): Int = list.minByOrNull { abs(it - v) } ?: v
}
