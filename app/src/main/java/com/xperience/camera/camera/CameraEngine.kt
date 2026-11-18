package com.xperience.camera.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.RggbChannelVector
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Range
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Satu pilihan kanta yang dipaparkan (16mm / 24mm / 48mm ...). */
data class LensOption(
    val key: String,          // "<cameraId>@<zoom>"
    val cameraId: String,
    val zoom: Float,
    val eqMm: Int,
    val physical: Boolean,    // true = sensor fizikal berasingan
    val note: String,
)

/** Keupayaan sebenar perkakasan kamera aktif (dari Camera2). */
data class Caps(
    val cameraId: String = "",
    val hwLevel: String = "-",
    val manualSensor: Boolean = false,
    val manualPost: Boolean = false,
    val raw: Boolean = false,
    val isoRange: IntRange = 100..3200,
    val shutterRange: LongRange = 100_000L..250_000_000L,
    val minFocus: Float = 0f,           // diopter; 0 = fokus tetap
    val apertures: FloatArray = floatArrayOf(),
    val focalMm: Float = 0f,
    val eqMm: Int = 24,
    val flash: Boolean = false,
    val faceDetect: Boolean = false,
    val evStep: Float = 1 / 3f,
    val evRange: IntRange = -6..6,
    val zoomMin: Float = 1f,
    val zoomMax: Float = 1f,
    val sensorMp: Float = 0f,
    val awbModes: IntArray = intArrayOf(),
    val supportsRawFormat: Boolean = false,
) {
    val shutters: List<Long> get() = Stops.SHUTTER.filter { it in shutterRange }
    val isos: List<Int> get() = Stops.ISO.filter { it in isoRange }
    val physicalAperture: Boolean get() = apertures.size > 1
}

/** Nilai langsung dari sensor (dibaca setiap bingkai). */
data class LiveInfo(
    val shutterNs: Long = 0,
    val iso: Int = 0,
    val aperture: Float = 0f,
    val focalMm: Float = 0f,
    val focusDiopter: Float = 0f,
    val aeConverged: Boolean = false,
    val afLocked: Boolean = false,
    val meterEv: Float = 0f,       // M.M. — sisihan meter (stop)
    val wbCalibrating: Boolean = false,
)

@OptIn(ExperimentalCamera2Interop::class)
@SuppressLint("UnsafeOptInUsageError", "RestrictedApi")
class CameraEngine(private val ctx: Context) {

    private val analysisExec = Executors.newSingleThreadExecutor()
    private val mainExec = ContextCompat.getMainExecutor(ctx)
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var previewView: PreviewView? = null
    private var bound: CamSettings? = null
    var settings: CamSettings = CamSettings()
        private set

    private val _caps = MutableStateFlow(Caps())
    val caps: StateFlow<Caps> = _caps
    private val _lenses = MutableStateFlow<List<LensOption>>(emptyList())
    val lenses: StateFlow<List<LensOption>> = _lenses
    private val _live = MutableStateFlow(LiveInfo())
    val live: StateFlow<LiveInfo> = _live
    private val _stats = MutableStateFlow<FrameStats?>(null)
    val stats: StateFlow<FrameStats?> = _stats
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Keadaan gelung pendedahan (Auto ISO / Program / Shutter priority perisian)
    @Volatile private var loopShutter = 8_000_000L
    @Volatile private var loopIso = 200
    @Volatile var aeLock = false
        private set
    @Volatile private var lastAuto: Pair<Long, Int>? = null
    @Volatile var spot: Pair<Float, Float>? = null

    // Kalibrasi imbangan putih Kelvin (gandaan AWB-Daylight sensor + matriks warna)
    private var wbCal: Pair<RggbChannelVector, ColorSpaceTransform>? = null
    private var wbCalCamera = ""
    private var wbCalFrames = 0
    private var lastResultTs = 0L

    // ------------------------------------------------------------------ BIND
    suspend fun ensureProvider(): ProcessCameraProvider {
        provider?.let { return it }
        val p = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
            val f = ProcessCameraProvider.getInstance(ctx)
            f.addListener({ cont.resume(f.get()) }, mainExec)
        }
        provider = p
        buildLensList(p)
        return p
    }

    private fun eqFocal(ch: CameraCharacteristics): Int {
        val f = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return 24
        val s = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return 24
        val diag = sqrt(s.width * s.width + s.height * s.height)
        return if (diag > 0) (f * 43.27f / diag).roundToInt() else 24
    }

    private fun infoOf(i: CameraInfo) = Camera2CameraInfo.from(i)

    private fun buildLensList(p: ProcessCameraProvider) {
        val backs = p.availableCameraInfos.filter {
            infoOf(it).getCameraCharacteristic(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
        }
        if (backs.isEmpty()) { _lenses.value = emptyList(); return }
        val defaultInfo = try {
            CameraSelector.DEFAULT_BACK_CAMERA.filter(p.availableCameraInfos).firstOrNull()
        } catch (_: Exception) { null } ?: backs.first()
        val mainId = infoOf(defaultInfo).cameraId
        val mainCh = backs.first { infoOf(it).cameraId == mainId }
        val mainEq = eqFocal(cameraChars(mainCh))
        val zs = defaultInfo.zoomState.value
        val zMin = zs?.minZoomRatio ?: 1f; val zMax = zs?.maxZoomRatio ?: 1f
        val out = mutableListOf<LensOption>()
        // Hentian gaya Xperia (16 / 24 / 48 / 70 / 85mm) dicapai melalui kamera logik utama.
        val targets = listOf(16, 24, 35, 48, 70, 85, 105)
        if (zMin < 0.95f && mainEq * zMin < 15.5f) out += LensOption("$mainId@$zMin", mainId, zMin, (mainEq * zMin).roundToInt(), false, "Ultra Wide")
        for (t in targets) {
            val z = t / mainEq.toFloat()
            if (z >= zMin - 0.02f && z <= zMax + 0.01f) {
                val zz = z.coerceIn(zMin, zMax)
                val note = when {
                    t == 24 || kotlin.math.abs(zz - 1f) < 0.03f -> "Main"
                    zz < 1f -> "Ultra Wide"
                    else -> "Zoom ×%.1f".format(zz)
                }
                out += LensOption("$mainId@$zz", mainId, if (kotlin.math.abs(zz - 1f) < 0.03f) 1f else zz, t, false, note)
            }
        }
        // Sensor fizikal lain yang didedahkan oleh pengeluar
        for (b in backs) {
            val id = infoOf(b).cameraId
            if (id == mainId) continue
            val eq = eqFocal(cameraChars(b))
            out += LensOption("$id@1.0", id, 1f, eq, true, "Sensor $id")
        }
        _lenses.value = out.distinctBy { it.eqMm to it.physical }.sortedBy { it.eqMm }
    }

    private fun cameraChars(i: CameraInfo): CameraCharacteristics {
        // CameraCharacteristics penuh melalui CameraManager (lebih lengkap dari interop)
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        return cm.getCameraCharacteristics(infoOf(i).cameraId)
    }

    private fun selectorFor(s: CamSettings): CameraSelector {
        val p = provider
        if (s.front) return CameraSelector.DEFAULT_FRONT_CAMERA
        val id = s.lensId.substringBefore('@')
        if (id.isNotEmpty() && p != null && p.availableCameraInfos.any { infoOf(it).cameraId == id }) {
            return CameraSelector.Builder().addCameraFilter { list -> list.filter { infoOf(it).cameraId == id } }.build()
        }
        return CameraSelector.DEFAULT_BACK_CAMERA
    }

    private fun needsRebind(a: CamSettings?, b: CamSettings): Boolean =
        a == null || a.aspect != b.aspect || a.format != b.format || a.front != b.front ||
            a.lensId.substringBefore('@') != b.lensId.substringBefore('@') || a.jpegQuality != b.jpegQuality

    suspend fun bind(owner: LifecycleOwner, view: PreviewView, s: CamSettings) {
        settings = s
        val sameView = view === previewView
        previewView = view
        val p = ensureProvider()
        if (!needsRebind(bound, s) && camera != null && sameView) { applyAll(); return }
        p.unbindAll()
        camera = null
        val selector = selectorFor(s)
        val info = try { selector.filter(p.availableCameraInfos).firstOrNull() } catch (_: Exception) { null }
        if (info == null) { _error.value = "Kamera tidak ditemui"; return }

        val ratio = if (s.aspect == Aspect.R16_9) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY

        val previewB = Preview.Builder().setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
        Camera2Interop.Extender(previewB).setSessionCaptureCallback(resultCallback)
        val preview = previewB.build().also { it.setSurfaceProvider(view.surfaceProvider) }

        val fmtCaps = try { ImageCapture.getImageCaptureCapabilities(info).supportedOutputFormats } catch (_: Throwable) { emptySet() }
        val rawOk = ImageCapture.OUTPUT_FORMAT_RAW in fmtCaps
        val rawJpegOk = ImageCapture.OUTPUT_FORMAT_RAW_JPEG in fmtCaps
        val outFmt = when (s.format) {
            FileFormat.RAW -> if (rawOk) ImageCapture.OUTPUT_FORMAT_RAW else ImageCapture.OUTPUT_FORMAT_JPEG
            FileFormat.RAW_JPEG -> if (rawJpegOk) ImageCapture.OUTPUT_FORMAT_RAW_JPEG else ImageCapture.OUTPUT_FORMAT_JPEG
            FileFormat.JPEG -> ImageCapture.OUTPUT_FORMAT_JPEG
        }
        if (s.format != FileFormat.JPEG && outFmt == ImageCapture.OUTPUT_FORMAT_JPEG)
            _error.value = "RAW tidak disokong kamera ini — disimpan sebagai JPEG"

        val capB = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(s.jpegQuality.coerceIn(60, 100))
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio)
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build())
        if (outFmt != ImageCapture.OUTPUT_FORMAT_JPEG) capB.setOutputFormat(outFmt)
        Camera2Interop.Extender(capB).setSessionCaptureCallback(resultCallback)
        val capture = capB.build()

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio)
                .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
            .build()
        analysis.setAnalyzer(analysisExec, FrameAnalyzer({ settings }, { spot }) { st -> onStats(st) })

        val vp = ViewPort.Builder(Rational(s.aspect.w, s.aspect.h), view.display?.rotation ?: Surface.ROTATION_0)
            .setScaleType(ViewPort.FILL_CENTER).build()
        val group = UseCaseGroup.Builder().setViewPort(vp).addUseCase(preview).addUseCase(capture).addUseCase(analysis).build()

        var usedCapture = capture
        try {
            camera = p.bindToLifecycle(owner, selector, group)
        } catch (e: Exception) {
            // Sesetengah peranti tidak sokong gabungan RAW + analisis — cuba semula dengan JPEG
            try {
                p.unbindAll()
                val b2 = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(s.jpegQuality.coerceIn(60, 100))
                Camera2Interop.Extender(b2).setSessionCaptureCallback(resultCallback)
                val cap2 = b2.build()
                val g2 = UseCaseGroup.Builder().setViewPort(vp).addUseCase(preview).addUseCase(cap2).addUseCase(analysis).build()
                camera = p.bindToLifecycle(owner, selector, g2)
                usedCapture = cap2
                _error.value = "Format ${s.format.label} tidak boleh digabung pada peranti ini — JPEG digunakan"
            } catch (e2: Exception) {
                _error.value = "Gagal buka kamera: ${e2.message}"
                return
            }
        }
        imageCapture = usedCapture
        bound = s
        readCaps(info, rawOk || rawJpegOk)
        applyAll()
    }

    fun unbind() { provider?.unbindAll(); camera = null; bound = null }

    private fun readCaps(info: CameraInfo, rawFormat: Boolean) {
        val ch = cameraChars(info)
        val capsArr = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val level = when (ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL 3"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "?"
        }
        val iso = ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exp = ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val px = ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val ev = info.exposureState
        val zs = info.zoomState.value
        val maxExp = min(exp?.upper ?: 250_000_000L, 30_000_000_000L)
        _caps.value = Caps(
            cameraId = infoOf(info).cameraId,
            hwLevel = level,
            manualSensor = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in capsArr,
            manualPost = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING in capsArr,
            raw = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW in capsArr,
            isoRange = if (iso != null) iso.lower..iso.upper else 100..3200,
            shutterRange = if (exp != null) exp.lower..maxExp else 100_000L..250_000_000L,
            minFocus = ch.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f,
            apertures = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES) ?: floatArrayOf(),
            focalMm = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0f,
            eqMm = eqFocal(ch),
            flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
            faceDetect = (ch.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT) ?: 0) > 0,
            evStep = ev.exposureCompensationStep.toFloat().takeIf { it > 0f } ?: (1 / 3f),
            evRange = ev.exposureCompensationRange.lower..ev.exposureCompensationRange.upper,
            zoomMin = zs?.minZoomRatio ?: 1f, zoomMax = zs?.maxZoomRatio ?: 1f,
            sensorMp = if (px != null) px.width * px.height / 1_000_000f else 0f,
            awbModes = ch.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf(),
            supportsRawFormat = rawFormat,
        )
    }

    // ------------------------------------------------------------------ SETTINGS
    fun update(s: CamSettings) {
        val old = settings
        settings = s
        if (old.effective != s.effective || old.iso != s.iso || old.shutterNs != s.shutterNs) seedLoop(old, s)
        if (camera != null && !needsRebind(bound, s)) applyAll()
    }

    /** Bila beralih ke mod manual, mula dari nilai auto semasa (tiada lompatan kecerahan). */
    private fun seedLoop(old: CamSettings, s: CamSettings) {
        val la = lastAuto ?: return
        if (s.iso == 0 && s.effective == ShootMode.S) {
            loopIso = (la.first.toDouble() * la.second / s.shutterNs).roundToInt().coerceIn(caps.value.isoRange)
        }
        if (s.effective == ShootMode.P && s.iso != 0) {
            loopShutter = (la.first.toDouble() * la.second / s.iso).toLong().coerceIn(caps.value.shutterRange)
        }
        if (s.effective == ShootMode.M && s.iso == 0) {
            loopIso = (la.first.toDouble() * la.second / s.shutterNs).roundToInt().coerceIn(caps.value.isoRange)
        }
    }

    /** Mod yang guna AE perkakasan (native). */
    private fun nativeAe(s: CamSettings): Boolean {
        val c = caps.value
        if (!c.manualSensor) return true
        return when (s.effective) {
            ShootMode.AUTO -> true
            ShootMode.P -> s.iso == 0
            else -> false
        }
    }

    /** Nilai shutter/ISO manual yang akan dihantar ke sensor. */
    private fun manualValues(s: CamSettings): Pair<Long, Int> {
        val c = caps.value
        return when (s.effective) {
            ShootMode.S -> s.shutterNs.coerceIn(c.shutterRange) to (if (s.iso == 0) loopIso else s.iso).coerceIn(c.isoRange)
            ShootMode.M -> s.shutterNs.coerceIn(c.shutterRange) to (if (s.iso == 0) loopIso else s.iso).coerceIn(c.isoRange)
            ShootMode.P -> loopShutter.coerceIn(c.shutterRange) to s.iso.coerceIn(c.isoRange)
            else -> loopShutter to loopIso
        }
    }

    private fun applyAll() {
        val cam = camera ?: return
        val s = settings
        val c = caps.value
        val b = CaptureRequestOptions.Builder()
        val native = nativeAe(s)
        if (!native) {
            val (t, iso) = manualValues(s)
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, t)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, max(t, 33_333_333L))
        } else {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, aeLock)
            if (s.effective != ShootMode.AUTO) {
                // AE region ikut mod meter (spot/center) — Multi guna lalai perkakasan
                aeRegion(s)?.let { b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(it)) }
            }
        }
        // Fokus
        if (s.focus == FocusMode.MF && c.minFocus > 0f) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, s.focusPos.coerceIn(0f, 1f) * c.minFocus)
        }
        if (s.faceAf && c.faceDetect && s.focus != FocusMode.MF && s.effective == ShootMode.AUTO) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY)
            b.setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE)
        }
        if (c.faceDetect) b.setCaptureRequestOption(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE)
        // Imbangan putih
        applyWb(b, s, c)
        // Bukaan fizikal (jika kanta sokong)
        if (c.physicalAperture) {
            val a = c.apertures.minByOrNull { kotlin.math.abs(it - s.aperture) }
            if (a != null && s.effective == ShootMode.M) b.setCaptureRequestOption(CaptureRequest.LENS_APERTURE, a)
        }
        Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(b.build())

        // EV (AE perkakasan sahaja)
        if (native && s.effective != ShootMode.AUTO) {
            val idx = (s.ev / c.evStep).roundToInt().coerceIn(c.evRange)
            cam.cameraControl.setExposureCompensationIndex(idx)
        } else cam.cameraControl.setExposureCompensationIndex(0)
        // Zum
        cam.cameraControl.setZoomRatio(if (s.front) 1f else zoomRatio(s))
        // Kilat
        cam.cameraControl.enableTorch(s.flash == FlashMode.TORCH && c.flash)
        imageCapture?.flashMode = when {
            !native -> ImageCapture.FLASH_MODE_OFF
            s.flash == FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            s.flash == FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    /** Nisbah zum = kanta pilihan × cubit (pinch). */
    fun zoomRatio(s: CamSettings = settings): Float {
        val c = caps.value
        val base = s.lensId.substringAfter('@', "").toFloatOrNull() ?: 1f
        return (base * s.zoom).coerceIn(c.zoomMin, max(c.zoomMin, c.zoomMax))
    }

    private fun aeRegion(s: CamSettings): MeteringRectangle? {
        if (s.metering != Metering.SPOT && s.metering != Metering.CENTER) return null
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val act = try { cm.getCameraCharacteristics(caps.value.cameraId).get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) } catch (_: Exception) { null } ?: return null
        // titik paparan (potret) -> koordinat sensor (landskap, putaran 90)
        val (vx, vy) = spot ?: (0.5f to 0.5f)
        val sx = vy; val sy = 1f - vx
        val half = if (s.metering == Metering.SPOT) 0.04f else 0.18f
        val l = ((sx - half).coerceIn(0f, 1f) * act.width()).toInt()
        val t = ((sy - half).coerceIn(0f, 1f) * act.height()).toInt()
        val r = ((sx + half).coerceIn(0f, 1f) * act.width()).toInt()
        val btm = ((sy + half).coerceIn(0f, 1f) * act.height()).toInt()
        return MeteringRectangle(act.left + l, act.top + t, max(1, r - l), max(1, btm - t), MeteringRectangle.METERING_WEIGHT_MAX - 1)
    }

    private fun applyWb(b: CaptureRequestOptions.Builder, s: CamSettings, c: Caps) {
        val k = when (s.wb) { WbMode.KELVIN -> s.kelvin; WbMode.FLASH -> s.wb.kelvin; else -> 0 }
        if (k == 0) {
            val mode = if (s.wb.awb in c.awbModes || s.wb == WbMode.AUTO) s.wb.awb else CameraMetadata.CONTROL_AWB_MODE_AUTO
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, mode)
            return
        }
        if (!c.manualPost) {
            // tiada kawalan gandaan warna: guna pratetap terdekat
            val near = listOf(WbMode.INCANDESCENT, WbMode.FLUORESCENT, WbMode.DAYLIGHT, WbMode.CLOUDY, WbMode.SHADE)
                .filter { it.awb in c.awbModes }.minByOrNull { kotlin.math.abs(it.kelvin - k) }
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, near?.awb ?: CameraMetadata.CONTROL_AWB_MODE_AUTO)
            return
        }
        val cal = wbCal.takeIf { wbCalCamera == c.cameraId }
        if (cal == null) {
            // langkah 1: kalibrasi — biar sensor kira gandaan Daylight 5500K
            wbCalFrames = 0
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT)
            _live.value = _live.value.copy(wbCalibrating = true)
            return
        }
        val rel = Kelvin.relativeGains(k)
        val g = cal.first
        val gains = RggbChannelVector(g.red * rel[0], g.greenEven, g.greenOdd, g.blue * rel[1])
        b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
        b.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
        b.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, gains)
        b.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_TRANSFORM, cal.second)
    }

    // ------------------------------------------------------------------ RESULTS
    private val resultCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val t = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0
            val aeMode = result.get(CaptureResult.CONTROL_AE_MODE)
            if (aeMode != null && aeMode != CameraMetadata.CONTROL_AE_MODE_OFF && t > 0 && iso > 0) lastAuto = t to iso
            // kalibrasi WB Kelvin
            if (result.get(CaptureResult.CONTROL_AWB_MODE) == CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT &&
                (settings.wb == WbMode.KELVIN || settings.wb == WbMode.FLASH) && wbCal == null
            ) {
                wbCalFrames++
                val g = result.get(CaptureResult.COLOR_CORRECTION_GAINS)
                val m = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)
                if (wbCalFrames > 8 && g != null && m != null) {
                    wbCal = g to m; wbCalCamera = caps.value.cameraId
                    mainExec.execute { applyAll(); _live.value = _live.value.copy(wbCalibrating = false) }
                }
            }
            val now = System.currentTimeMillis()
            if (now - lastResultTs < 180) return
            lastResultTs = now
            val ae = result.get(CaptureResult.CONTROL_AE_STATE)
            val af = result.get(CaptureResult.CONTROL_AF_STATE)
            _live.value = _live.value.copy(
                shutterNs = t, iso = iso,
                aperture = result.get(CaptureResult.LENS_APERTURE) ?: _live.value.aperture,
                focalMm = result.get(CaptureResult.LENS_FOCAL_LENGTH) ?: 0f,
                focusDiopter = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f,
                aeConverged = ae == CaptureResult.CONTROL_AE_STATE_CONVERGED || ae == CaptureResult.CONTROL_AE_STATE_LOCKED,
                afLocked = af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
            )
        }
    }

    // ------------------------------------------------------------------ EXPOSURE LOOP
    private var loopTick = 0

    private fun onStats(st: FrameStats) {
        _stats.value = st
        val s = settings
        val c = caps.value
        val target = 118f
        val measured = st.metered.coerceAtLeast(1.5f)
        // sisihan meter dalam stop (anggaran gamma ~2.2)
        val dev = (2.2 * ln((measured / target).toDouble()) / ln(2.0)).toFloat() - s.ev
        _live.value = _live.value.copy(meterEv = dev)
        if (nativeAe(s) || aeLock) return
        val autoIso = (s.effective == ShootMode.S || s.effective == ShootMode.M) && s.iso == 0
        val autoShutter = s.effective == ShootMode.P && s.iso != 0
        if (!autoIso && !autoShutter) return
        // pembetulan: k = 2^(-dev), dilembapkan
        val k = 2.0.pow((-dev * 0.55).toDouble().coerceIn(-2.0, 2.0)).toFloat()
        if (kotlin.math.abs(k - 1f) < 0.04f) return
        if (autoIso) {
            val n = (loopIso * k).roundToInt().coerceIn(c.isoRange)
            if (n == loopIso) return
            loopIso = n
        } else {
            val maxT = min(c.shutterRange.last, 125_000_000L)   // P: had 1/8s pegang tangan
            val n = (loopShutter * k).toLong().coerceIn(c.shutterRange.first, maxT)
            if (n == loopShutter) return
            loopShutter = n
        }
        if (++loopTick % 1 == 0) mainExec.execute { applyAll() }
    }

    fun toggleAeLock(): Boolean { aeLock = !aeLock; applyAll(); return aeLock }

    /** Nilai pendedahan yang dipaparkan pada jalur maklumat. */
    fun displayedExposure(): Pair<Long, Int> {
        val s = settings
        if (nativeAe(s)) return _live.value.shutterNs to _live.value.iso
        return manualValues(s)
    }

    // ------------------------------------------------------------------ FOCUS
    fun focusAt(nx: Float, ny: Float, lock: Boolean) {
        val cam = camera ?: return
        val v = previewView ?: return
        spot = nx to ny
        if (settings.focus == FocusMode.MF) { applyAll(); return }
        val pt = v.meteringPointFactory.createPoint(nx * v.width, ny * v.height)
        var flags = FocusMeteringAction.FLAG_AF
        if (nativeAe(settings)) flags = flags or FocusMeteringAction.FLAG_AE
        val b = FocusMeteringAction.Builder(pt, flags)
        if (lock || settings.focus == FocusMode.AF_S) b.disableAutoCancel() else b.setAutoCancelDuration(4, TimeUnit.SECONDS)
        cam.cameraControl.startFocusAndMetering(b.build())
        applyAll()
    }

    fun cancelFocus() { camera?.cameraControl?.cancelFocusAndMetering(); spot = null; applyAll() }

    // ------------------------------------------------------------------ CAPTURE
    private fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    private fun mediaValues(name: String, mime: String): ContentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/XPERIENCE")
    }

    /**
     * Ambil gambar. Jika [processJpeg] true, JPEG ditulis ke fail sementara untuk diproses
     * (Creative Look / DRO / AI Bokeh) — [onJpeg] menerima fail itu. Jika tidak, terus ke galeri.
     */
    suspend fun capture(processJpeg: Boolean): CaptureOutcome = suspendCancellableCoroutine { cont ->
        val ic = imageCapture ?: run { cont.resume(CaptureOutcome(error = "Kamera belum sedia")); return@suspendCancellableCoroutine }
        val name = "XPR_" + stamp()
        val resolver = ctx.contentResolver
        val jpegOpts = if (processJpeg) {
            val f = File(ctx.cacheDir, "$name.jpg")
            ImageCapture.OutputFileOptions.Builder(f).build() to f
        } else {
            ImageCapture.OutputFileOptions.Builder(resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                mediaValues("$name.jpg", "image/jpeg")).build() to null
        }
        val rawOpts = ImageCapture.OutputFileOptions.Builder(resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            mediaValues("$name.dng", "image/x-adobe-dng")).build()

        val fmt = ic.outputFormat
        var expected = if (fmt == ImageCapture.OUTPUT_FORMAT_RAW_JPEG) 2 else 1
        val uris = mutableListOf<Uri>()
        var tmp: File? = null
        val cb = object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                output.savedUri?.let { u -> if (u.scheme == "file") tmp = File(u.path!!) else uris += u }
                if (--expected <= 0 && cont.isActive) cont.resume(CaptureOutcome(uris = uris, tempJpeg = tmp ?: jpegOpts.second?.takeIf { it.exists() }))
            }
            override fun onError(exception: ImageCaptureException) {
                if (cont.isActive) cont.resume(CaptureOutcome(uris = uris, error = exception.message ?: "Ralat tangkapan"))
            }
        }
        try {
            when (fmt) {
                ImageCapture.OUTPUT_FORMAT_RAW_JPEG -> ic.takePicture(rawOpts, jpegOpts.first, analysisExec, cb)
                ImageCapture.OUTPUT_FORMAT_RAW -> ic.takePicture(rawOpts, analysisExec, cb)
                else -> ic.takePicture(jpegOpts.first, analysisExec, cb)
            }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(CaptureOutcome(error = e.message ?: "Ralat"))
        }
    }

    fun outputIsRawOnly(): Boolean = imageCapture?.outputFormat == ImageCapture.OUTPUT_FORMAT_RAW

    /** Laporan keserasian untuk skrin About. */
    fun compatReport(): String {
        val c = caps.value
        fun yn(b: Boolean) = if (b) "✔ Ya" else "✘ Tidak"
        return buildString {
            appendLine("Kamera aktif: ${c.cameraId}  (${Build.MANUFACTURER} ${Build.MODEL})")
            appendLine("Tahap Camera2: ${c.hwLevel}")
            appendLine("Sensor: %.1f MP, kanta %.2fmm (≈%dmm 35mm)".format(c.sensorMp, c.focalMm, c.eqMm))
            appendLine("Manual sensor (ISO/Shutter): ${yn(c.manualSensor)}")
            appendLine("Manual post-processing (Kelvin WB): ${yn(c.manualPost)}")
            appendLine("RAW (DNG): ${yn(c.raw && c.supportsRawFormat)}")
            appendLine("ISO: ${c.isoRange.first} – ${c.isoRange.last}")
            appendLine("Shutter: ${Stops.shutterLabel(c.shutterRange.first)} – ${Stops.shutterLabel(c.shutterRange.last)}")
            appendLine("Manual focus: ${if (c.minFocus > 0) "✔ Ya (≈${"%.1f".format(100f / c.minFocus)}cm min)" else "✘ Fokus tetap"}")
            appendLine("Bukaan fizikal: ${if (c.physicalAperture) "✔ " + c.apertures.joinToString { "f/$it" } else "✘ Tetap f/${c.apertures.firstOrNull() ?: "-"} → f-stop disimulasi AI"}")
            appendLine("Kilat: ${yn(c.flash)}   Pengesanan muka: ${yn(c.faceDetect)}")
            appendLine("Zum: ×${"%.2f".format(c.zoomMin)} – ×${"%.1f".format(c.zoomMax)}")
            appendLine("Kanta: " + lenses.value.joinToString { "${it.eqMm}mm" + if (it.physical) "(fizikal)" else "" })
        }
    }

    fun clearError() { _error.value = null }
}

data class CaptureOutcome(val uris: List<Uri> = emptyList(), val tempJpeg: File? = null, val error: String? = null)
