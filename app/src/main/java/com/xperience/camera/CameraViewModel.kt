package com.xperience.camera

import android.app.Application
import android.content.ContentUris
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.xperience.camera.camera.CamSettings
import com.xperience.camera.camera.CameraEngine
import com.xperience.camera.camera.Drive
import com.xperience.camera.camera.FileFormat
import com.xperience.camera.camera.ShootMode
import com.xperience.camera.data.Prefs
import com.xperience.camera.process.PhotoProcessor
import com.xperience.camera.process.ProcessOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CameraViewModel(app: Application) : AndroidViewModel(app) {

    val engine = CameraEngine(app)
    private val prefs = Prefs(app)

    private val _s = MutableStateFlow(prefs.load().copy(zoom = 1f))
    val settings: StateFlow<CamSettings> = _s

    val busy = MutableStateFlow(false)          // sedang menangkap
    val processing = MutableStateFlow(0)        // bilangan dalam baris gilir pemprosesan
    val countdown = MutableStateFlow(0)
    val shotsLeft = MutableStateFlow(0)
    val lastUri = MutableStateFlow<Uri?>(null)
    val flashUi = MutableStateFlow(0L)          // denyar skrin semasa tangkap
    val toast = MutableStateFlow<String?>(null)
    val aeLock = MutableStateFlow(false)

    private val sound = MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) }
    private val queue = Channel<Pair<java.io.File, CamSettings>>(Channel.UNLIMITED)
    private var driveJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.Default) {
            for ((file, s) in queue) {
                val uri = PhotoProcessor.processCapture(getApplication(), file, s.processOptions(), s.keepOriginal)
                if (uri != null) lastUri.value = uri
                processing.value = (processing.value - 1).coerceAtLeast(0)
            }
        }
        refreshLast()
    }

    private fun CamSettings.processOptions() = ProcessOptions(lookId, lookIntensity, dro, bokeh, aperture, bokehIntensity, bokehStyle, jpegQuality)

    fun update(f: (CamSettings) -> CamSettings) {
        val n = f(_s.value)
        _s.value = n
        engine.update(n)
        prefs.save(n)
    }

    suspend fun bind(owner: LifecycleOwner, view: PreviewView) = engine.bind(owner, view, _s.value)

    // ------------------------------------------------------------------ Memory Recall
    fun mrSlot(slot: Int): CamSettings? = prefs.mr(slot)

    fun saveMr(slot: Int) { prefs.saveMr(slot, _s.value); toast.value = "Tetapan disimpan ke MR$slot" }

    fun recallMr(slot: Int) {
        val m = prefs.mr(slot) ?: run { toast.value = "MR$slot kosong — simpan dahulu"; return }
        update { m.copy(mode = ShootMode.MR, recallMode = m.mode, mrSlot = slot, front = it.front) }
        toast.value = "MR$slot dipanggil (${m.mode.label})"
    }

    fun toggleAeLock() { aeLock.value = engine.toggleAeLock() }

    // ------------------------------------------------------------------ CAPTURE
    fun shutter() {
        if (driveJob?.isActive == true) { driveJob?.cancel(); shotsLeft.value = 0; countdown.value = 0; busy.value = false; return }
        val s = _s.value
        driveJob = viewModelScope.launch {
            busy.value = true
            try {
                when (s.drive) {
                    Drive.SINGLE -> shootOne()
                    Drive.TIMER3, Drive.TIMER10 -> {
                        val n = if (s.drive == Drive.TIMER3) 3 else 10
                        for (i in n downTo 1) { countdown.value = i; delay(1000) }
                        countdown.value = 0
                        shootOne()
                    }
                    Drive.BURST, Drive.BURST_HI -> {
                        val gap = if (s.drive == Drive.BURST_HI) 0L else 180L
                        for (i in s.burstCount downTo 1) { shotsLeft.value = i; shootOne(quiet = s.drive == Drive.BURST_HI && i != s.burstCount); if (gap > 0) delay(gap) }
                        shotsLeft.value = 0
                    }
                    Drive.INTERVAL -> {
                        for (i in s.intervalCount downTo 1) {
                            shotsLeft.value = i
                            shootOne()
                            if (i > 1) for (c in s.intervalSec downTo 1) { countdown.value = c; delay(1000) }
                            countdown.value = 0
                        }
                        shotsLeft.value = 0
                    }
                }
            } finally {
                busy.value = false
            }
        }
    }

    private suspend fun shootOne(quiet: Boolean = false) {
        val s = _s.value
        val opts = s.processOptions()
        val process = opts.active && !(s.format == FileFormat.RAW && engine.outputIsRawOnly())
        if (s.focus == com.xperience.camera.camera.FocusMode.AF_S && engine.spot == null) {
            engine.focusAt(0.5f, 0.5f, false); delay(350)
        }
        if (s.shutterSound && !quiet) sound.play(MediaActionSound.SHUTTER_CLICK)
        flashUi.value = System.currentTimeMillis()
        val out = engine.capture(process)
        if (out.error != null) { toast.value = out.error; return }
        if (!process && out.uris.isNotEmpty()) lastUri.value = out.uris.first()
        val tmp = out.tempJpeg
        if (tmp != null) {
            processing.value = processing.value + 1
            queue.send(tmp to s)
        }
    }

    fun refreshLast() {
        viewModelScope.launch(Dispatchers.IO) {
            lastUri.value = queryPhotos(getApplication(), 1).firstOrNull()?.uri ?: lastUri.value
        }
    }

    override fun onCleared() { sound.release(); engine.unbind(); super.onCleared() }

    companion object {
        data class Photo(val uri: Uri, val name: String, val date: Long, val mime: String)

        fun queryPhotos(app: Application, limit: Int = 500): List<Photo> {
            val out = mutableListOf<Photo>()
            val proj = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.MIME_TYPE)
            val (sel, args) = if (Build.VERSION.SDK_INT >= 29)
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?" to arrayOf("%DCIM/XPERIENCE%")
            else "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?" to arrayOf("XPR_%")
            try {
                app.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, sel, args,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
                    while (c.moveToNext() && out.size < limit) {
                        val id = c.getLong(0)
                        out += Photo(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                            c.getString(1) ?: "", c.getLong(2), c.getString(3) ?: "image/jpeg")
                    }
                }
            } catch (_: Exception) { }
            return out
        }
    }

    // ------------------------------------------------------------------ EDIT (galeri)
    suspend fun editAndSave(src: Uri, o: ProcessOptions): Uri? = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val r = app.contentResolver
        val bmp = PhotoProcessor.decodeUpright({ r.openInputStream(src)!! }) ?: return@withContext null
        val out = PhotoProcessor.render(bmp, o)
        val name = "XPR_EDIT_" + System.currentTimeMillis()
        PhotoProcessor.save(app, out, name, o.quality, { r.openInputStream(src)!! }, "XPERIENCE Edit").also { if (it != null) lastUri.value = it }
    }
}
