package com.xperience.camera.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.util.Size
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.xperience.camera.CameraViewModel
import com.xperience.camera.ai.APERTURES
import com.xperience.camera.camera.Aspect
import com.xperience.camera.camera.Drive
import com.xperience.camera.camera.Dro
import com.xperience.camera.camera.FileFormat
import com.xperience.camera.camera.FlashMode
import com.xperience.camera.camera.FocusMode
import com.xperience.camera.camera.Grid
import com.xperience.camera.camera.Metering
import com.xperience.camera.camera.ShootMode
import com.xperience.camera.camera.Stops
import com.xperience.camera.camera.WbMode
import com.xperience.camera.image.Collection
import com.xperience.camera.image.Looks
import com.xperience.camera.process.PreviewLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class Param(val title: String) {
    SS("SHUTTER"), F("APERTURE"), ISO("ISO"), EV("EV"), WB("WHITE BALANCE"), FOCUS("FOCUS"),
    METER("METERING"), LOOK("CREATIVE LOOK"), DRIVE("DRIVE")
}

@Composable
fun CameraScreen(vm: CameraViewModel, onSettings: () -> Unit, onGallery: () -> Unit) {
    val s by vm.settings.collectAsState()
    val caps by vm.engine.caps.collectAsState()
    val live by vm.engine.live.collectAsState()
    val stats by vm.engine.stats.collectAsState()
    val lenses by vm.engine.lenses.collectAsState()
    val busy by vm.busy.collectAsState()
    val processing by vm.processing.collectAsState()
    val countdown by vm.countdown.collectAsState()
    val shotsLeft by vm.shotsLeft.collectAsState()
    val lastUri by vm.lastUri.collectAsState()
    val toast by vm.toast.collectAsState()
    val err by vm.engine.error.collectAsState()
    val aeLock by vm.aeLock.collectAsState()
    val flashTs by vm.flashUi.collectAsState()

    var active by remember { mutableStateOf(Param.SS) }
    var focusPt by remember { mutableStateOf<Offset?>(null) }
    var magnify by remember { mutableStateOf(false) }
    var showMr by remember { mutableStateOf(false) }
    var blink by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(ctx).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }
    LaunchedEffect(s.aspect, s.format, s.front, s.lensId.substringBefore('@'), s.jpegQuality) { vm.bind(owner, previewView) }
    LaunchedEffect(s.lookId, s.lookIntensity) {
        if (Build.VERSION.SDK_INT >= 31) {
            val m = PreviewLook.matrix(s.lookId, s.lookIntensity)
            previewView.setRenderEffect(m?.let { RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(it)) })
        }
    }
    LaunchedEffect(flashTs) { if (flashTs > 0) { blink = true; delay(90); blink = false } }
    LaunchedEffect(toast) { if (toast != null) { delay(2600); vm.toast.value = null } }
    LaunchedEffect(err) { if (err != null) { delay(3500); vm.engine.clearError() } }
    LaunchedEffect(s.focus) { if (s.focus != FocusMode.MF) magnify = false }

    val eff = s.effective
    val manualOk = caps.manualSensor

    Column(Modifier.fillMaxSize().background(XC.Bg).systemBarsPadding()) {
        // ================= TOP BAR
        Row(
            Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Tetapan", tint = XC.Text) }
            val flashIcon = when (s.flash) {
                FlashMode.OFF -> Icons.Outlined.FlashOff; FlashMode.AUTO -> Icons.Outlined.FlashAuto
                FlashMode.ON -> Icons.Outlined.FlashOn; FlashMode.TORCH -> Icons.Outlined.FlashlightOn
            }
            IconBtn(enabled = caps.flash && !s.front, onClick = {
                vm.update { it.copy(flash = FlashMode.entries[(it.flash.ordinal + 1) % FlashMode.entries.size]) }
            }) { Icon(flashIcon, "Kilat", tint = if (caps.flash && !s.front) (if (s.flash != FlashMode.OFF) XC.Accent else XC.Text) else XC.Off) }
            TopTag(s.format.label, s.format != FileFormat.JPEG) {
                val opts = if (caps.raw && caps.supportsRawFormat) FileFormat.entries else listOf(FileFormat.JPEG)
                vm.update { it.copy(format = opts[(opts.indexOf(it.format) + 1) % opts.size]) }
                if (!(caps.raw && caps.supportsRawFormat)) vm.toast.value = "RAW (DNG) tidak disokong oleh kamera ini"
            }
            TopTag(s.aspect.label, false) { vm.update { it.copy(aspect = Aspect.entries[(it.aspect.ordinal + 1) % Aspect.entries.size]) } }
            TopTag(if (s.dro == Dro.OFF) "DRO" else s.dro.label.removePrefix("DRO "), s.dro != Dro.OFF) {
                vm.update { it.copy(dro = Dro.entries[(it.dro.ordinal + 1) % Dro.entries.size]) }
            }
            TopTag(gridIcon(s.grid), s.grid != Grid.OFF) { vm.update { it.copy(grid = Grid.entries[(it.grid.ordinal + 1) % Grid.entries.size]) } }
            Spacer(Modifier.weight(1f))
            if (processing > 0) Text("⟳$processing", color = XC.Accent, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
            if (s.mode == ShootMode.MR) Text("MR${s.mrSlot}", color = XC.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
            Battery()
        }

        // ================= VIEWFINDER
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.aspectRatio(s.aspect.w / s.aspect.h.toFloat()).clipToBounds().background(Color(0xFF050505)),
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val k = if (magnify) 3f else 1f
                        scaleX = k; scaleY = k
                    },
                )
                GridOverlay(s.grid)
                AnalysisOverlay(stats, s.peaking && (s.focus == FocusMode.MF || magnify), s.highlightWarn, s.shadowWarn, s.front)
                if (s.level) LevelOverlay()
                focusPt?.let { FocusBox(it.x, it.y, live.afLocked) }
                if (s.metering == Metering.SPOT && focusPt == null) Crosshair()
                // gerak isyarat: ketik = fokus/meter, tekan lama = reset, cubit = zum
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(s.focus) {
                            detectTapGestures(
                                onTap = { o ->
                                    val nx = o.x / size.width; val ny = o.y / size.height
                                    focusPt = Offset(nx, ny)
                                    vm.engine.focusAt(nx, ny, false)
                                },
                                onLongPress = { focusPt = null; vm.engine.cancelFocus() },
                                onDoubleTap = { if (s.focus == FocusMode.MF) magnify = !magnify },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                if (zoom != 1f) vm.update { it.copy(zoom = (it.zoom * zoom).coerceIn(1f, 10f)) }
                            }
                        }
                )
                if (s.histogram) Histogram(stats, Modifier.align(Alignment.TopStart).padding(8.dp))
                // tag kiri atas: look + bokeh
                Column(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalAlignment = Alignment.End) {
                    Looks.byId(s.lookId)?.let { Tag(it.name) }
                    if (s.bokeh) Tag("AI BOKEH f/${fmtF(s.aperture)}")
                    if (aeLock) Tag("AEL", XC.Accent)
                    if (live.wbCalibrating) Tag("WB kalibrasi…")
                    if (s.zoom > 1.01f) Tag("×%.1f".format(vm.engine.zoomRatio(s)))
                    if (magnify) Tag("MAGNIFIER ×3", XC.Accent)
                }
                if (eff != ShootMode.AUTO) ExposureScale(live.meterEv, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
                if (countdown > 0) Text("$countdown", color = Color.White, fontSize = 88.sp, fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.Center))
                if (shotsLeft > 0) Tag("● $shotsLeft", XC.Red, Modifier.align(Alignment.BottomStart).padding(8.dp))
                if (blink) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)))
                (toast ?: err)?.let { msg ->
                    Text(msg, color = Color.White, fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp)).padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
        }

        // ================= INFO STRIP (gaya Alpha)
        Row(
            Modifier.fillMaxWidth().background(XC.Panel).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.padding(end = 4.dp).border(1.5.dp, XC.Text, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                    .clickable { showMr = s.mode == ShootMode.MR },
            ) { Text(if (s.mode == ShootMode.MR) "MR" else eff.label, color = XC.Text, fontSize = 16.sp, fontWeight = FontWeight.Black) }
            val ssText = if (eff == ShootMode.S || eff == ShootMode.M) Stops.shutterLabel(s.shutterNs) else Stops.shutterLabel(live.shutterNs)
            Info("SS", ssText, active == Param.SS, eff == ShootMode.S || eff == ShootMode.M) { active = Param.SS }
            val fText = if (s.bokeh) "F" + fmtF(s.aperture) else if (live.aperture > 0) "F" + fmtF(live.aperture) else "F--"
            Info(if (s.bokeh) "AI" else "F", fText, active == Param.F, true) { active = Param.F }
            val isoText = if (s.iso == 0) "A${if (live.iso > 0) live.iso else ""}" else "${s.iso}"
            Info("ISO", isoText, active == Param.ISO, eff != ShootMode.AUTO) { active = Param.ISO }
            val evText = if (eff == ShootMode.M && s.iso != 0) Stops.evLabel(live.meterEv) else Stops.evLabel(s.ev)
            Info(if (eff == ShootMode.M && s.iso != 0) "M.M." else "EV", evText, active == Param.EV, eff != ShootMode.AUTO) { active = Param.EV }
            Info("WB", if (s.wb == WbMode.KELVIN) "${s.kelvin}K" else s.wb.short, active == Param.WB, eff != ShootMode.AUTO) { active = Param.WB }
            Info("FOCUS", s.focus.label, active == Param.FOCUS, true) { active = Param.FOCUS }
        }
        Row(Modifier.fillMaxWidth().background(XC.Panel).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Info("METER", s.metering.icon + " " + s.metering.label, active == Param.METER, eff != ShootMode.AUTO) { active = Param.METER }
            Info("LOOK", Looks.byId(s.lookId)?.let { shortLook(it.id, it.name) } ?: "OFF", active == Param.LOOK, true) { active = Param.LOOK }
            Info("DRIVE", driveShort(s.drive), active == Param.DRIVE, true) { active = Param.DRIVE }
        }

        // ================= PANEL KAWALAN
        Box(Modifier.fillMaxWidth().height(112.dp).background(XC.Panel2)) {
            ControlPanel(active, vm, onMagnify = { magnify = it }, magnify = magnify)
        }

        // ================= MOD + KANTA
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            for (m in ShootMode.entries) {
                val enabled = manualOk || m == ShootMode.AUTO || m == ShootMode.P || m == ShootMode.MR
                val sel = s.mode == m
                Text(
                    m.label, color = if (!enabled) XC.Off else if (sel) XC.Accent else XC.Dim,
                    fontSize = 14.sp, fontWeight = if (sel) FontWeight.Black else FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(enabled = enabled) {
                        if (m == ShootMode.MR) showMr = true
                        else {
                            vm.update { it.copy(mode = m) }
                            active = when (m) { ShootMode.S, ShootMode.M -> Param.SS; ShootMode.P -> Param.EV; else -> Param.LOOK }
                        }
                    }.padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
        }
        if (!s.front && lenses.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().height(34.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                contentPadding = PaddingValues(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(lenses, key = { it.key }) { l ->
                    val sel = s.lensId == l.key || (s.lensId.isEmpty() && !l.physical && l.zoom == 1f)
                    Box(
                        Modifier.clip(CircleShape).border(1.dp, if (sel) XC.Accent else XC.Line, CircleShape)
                            .background(if (sel) XC.Accent.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { vm.update { it.copy(lensId = l.key, zoom = 1f) } }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) { Text("${l.eqMm}mm", color = if (sel) XC.Accent else XC.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        // ================= BAWAH: galeri / AEL / shutter / AF-ON / tukar kamera
        Row(
            Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Thumb(lastUri, onGallery)
            RoundBtn("AEL", aeLock) { vm.toggleAeLock() }
            ShutterButton(busy || countdown > 0 || shotsLeft > 0) { vm.shutter() }
            RoundBtn("AF-ON", live.afLocked) {
                val p = focusPt ?: Offset(0.5f, 0.5f)
                focusPt = p
                vm.engine.focusAt(p.x, p.y, true)
            }
            IconBtn(onClick = { vm.update { it.copy(front = !it.front, lensId = "", zoom = 1f) } }) {
                Icon(Icons.Outlined.Cameraswitch, "Tukar kamera", tint = XC.Text, modifier = Modifier.size(28.dp))
            }
        }
    }

    if (showMr) MrDialog(vm) { showMr = false }
}

// ------------------------------------------------------------------ PANEL
@Composable
private fun ControlPanel(p: Param, vm: CameraViewModel, magnify: Boolean, onMagnify: (Boolean) -> Unit) {
    val s by vm.settings.collectAsState()
    val caps by vm.engine.caps.collectAsState()
    val eff = s.effective
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(p.title, color = XC.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Spacer(Modifier.weight(1f))
            Text(hint(p, s, caps.manualSensor, caps.physicalAperture, caps.apertures.firstOrNull()), color = XC.Dim, fontSize = 10.sp, maxLines = 1)
        }
        when (p) {
            Param.SS -> {
                val list = caps.shutters
                val en = (eff == ShootMode.S || eff == ShootMode.M) && caps.manualSensor
                val idx = list.indexOf(Stops.nearest(list, s.shutterNs)).coerceAtLeast(0)
                ValueDial(list.map { Stops.shutterLabel(it) }, idx, en) { i -> vm.update { it.copy(shutterNs = list[i]) } }
            }
            Param.ISO -> {
                val isos = caps.isos
                val labels = listOf("AUTO") + isos.map { "$it" }
                val en = eff != ShootMode.AUTO && caps.manualSensor
                val idx = if (s.iso == 0) 0 else isos.indexOf(Stops.nearestIso(isos, s.iso)) + 1
                ValueDial(labels, idx, en) { i -> vm.update { it.copy(iso = if (i == 0) 0 else isos[i - 1]) } }
            }
            Param.EV -> {
                val idx = Stops.EV.indexOfFirst { kotlin.math.abs(it - s.ev) < 0.05f }.coerceAtLeast(9)
                ValueDial(Stops.EV.map { Stops.evLabel(it) }, idx, eff != ShootMode.AUTO) { i -> vm.update { it.copy(ev = Stops.EV[i]) } }
            }
            Param.F -> {
                Row(Modifier.padding(start = 12.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip(if (s.bokeh) "AI BOKEH: ON" else "AI BOKEH: OFF", s.bokeh) { vm.update { it.copy(bokeh = !it.bokeh) } }
                    Spacer(Modifier.width(6.dp))
                    for (st in listOf("NATURAL", "CIRCULAR", "CREAMY")) {
                        Chip(st.lowercase().replaceFirstChar { it.uppercase() }, s.bokehStyle == st, Modifier.padding(end = 4.dp)) {
                            vm.update { it.copy(bokehStyle = st, bokeh = true) }
                        }
                    }
                }
                val idx = APERTURES.indexOfFirst { kotlin.math.abs(it - s.aperture) < 0.05f }.coerceAtLeast(0)
                ValueDial(APERTURES.map { "f/" + fmtF(it) }, idx, true) { i -> vm.update { it.copy(aperture = APERTURES[i], bokeh = true) } }
            }
            Param.WB -> {
                val modes = WbMode.entries
                ChipRow(modes.map { it.short }, modes.indexOf(s.wb), eff != ShootMode.AUTO) { i -> vm.update { it.copy(wb = modes[i]) } }
                if (s.wb == WbMode.KELVIN) {
                    val ks = Stops.KELVIN
                    Box(Modifier.offset(y = (-14).dp)) {
                        ValueDial(ks.map { "${it}K" }, ks.indexOf(Stops.nearestIso(ks, s.kelvin)), eff != ShootMode.AUTO) { i -> vm.update { it.copy(kelvin = ks[i]) } }
                    }
                }
            }
            Param.FOCUS -> {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (f in FocusMode.entries) {
                        val en = f != FocusMode.MF || caps.minFocus > 0f
                        Chip(f.label, s.focus == f, Modifier.padding(end = 6.dp), en) { vm.update { it.copy(focus = f) } }
                    }
                    Chip("Peaking", s.peaking, Modifier.padding(end = 6.dp)) { vm.update { it.copy(peaking = !it.peaking) } }
                    if (s.focus == FocusMode.MF) Chip("×3", magnify) { onMagnify(!magnify) }
                }
                if (s.focus == FocusMode.MF) {
                    val cm = if (caps.minFocus > 0 && s.focusPos > 0.001f) "%.0fcm".format(100f / (s.focusPos * caps.minFocus)) else "∞"
                    XSlider(s.focusPos, 0f..1f, "∞ ⟷ dekat  $cm") { v -> vm.update { it.copy(focusPos = v) } }
                } else {
                    Text("Ketik pada skrin untuk fokus • AF-ON kunci fokus • tekan lama untuk reset",
                        color = XC.Dim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            Param.METER -> {
                val ms = Metering.entries
                ChipRow(ms.map { it.icon + " " + it.label }, ms.indexOf(s.metering), eff != ShootMode.AUTO) { i -> vm.update { it.copy(metering = ms[i]) } }
            }
            Param.LOOK -> LookPanel(vm)
            Param.DRIVE -> {
                val ds = Drive.entries
                ChipRow(ds.map { it.label }, ds.indexOf(s.drive)) { i -> vm.update { it.copy(drive = ds[i]) } }
                Text(when (s.drive) {
                    Drive.BURST, Drive.BURST_HI -> "${s.burstCount} keping setiap tekan (ubah dalam Tetapan)"
                    Drive.INTERVAL -> "${s.intervalCount} keping, setiap ${s.intervalSec}s — tekan shutter untuk henti"
                    else -> ""
                }, color = XC.Dim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}

private val LOOK_TABS = listOf(
    "XPERIA" to Collection.SONY, "LEICA" to Collection.LEICA, "HARCOURT" to Collection.HARCOURT,
    "FILM" to Collection.FILM, "B&W" to Collection.BW, "CINE" to Collection.CINEMATIC,
)

@Composable
private fun LookPanel(vm: CameraViewModel) {
    val s by vm.settings.collectAsState()
    val cur = Looks.byId(s.lookId)
    var tab by remember { mutableStateOf(LOOK_TABS.indexOfFirst { it.second == cur?.collection }.coerceAtLeast(0)) }
    LazyRow(
        Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        items(LOOK_TABS.size) { i ->
            Text(LOOK_TABS[i].first, color = if (i == tab) XC.Text else XC.Dim, fontSize = 11.sp,
                fontWeight = if (i == tab) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clickable { tab = i })
        }
    }
    val looks = Looks.byCollection(LOOK_TABS[tab].second)
    LazyRow(
        Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        item { Chip("OFF", s.lookId == null) { vm.update { it.copy(lookId = null) } } }
        items(looks, key = { it.id }) { l -> Chip(shortLook(l.id, l.name), s.lookId == l.id) { vm.update { it.copy(lookId = l.id) } } }
    }
    if (s.lookId != null) XSlider(s.lookIntensity, 0f..1.5f, "Intensiti ${(s.lookIntensity * 100).toInt()}%") { v -> vm.update { it.copy(lookIntensity = v) } }
}

private fun hint(p: Param, s: com.xperience.camera.camera.CamSettings, manual: Boolean, physAp: Boolean, fixedAp: Float?): String {
    val eff = s.effective
    if (!manual && (p == Param.SS || p == Param.ISO)) return "Telefon tidak sokong manual sensor"
    return when (p) {
        Param.SS -> if (eff == ShootMode.S || eff == ShootMode.M) "Leret dail untuk pilih" else "AUTO dalam mod ${eff.label} — guna S atau M"
        Param.ISO -> if (eff == ShootMode.AUTO) "AUTO" else if (eff == ShootMode.S || (eff == ShootMode.M && s.iso == 0)) "ISO AUTO laras ikut meter" else ""
        Param.EV -> if (eff == ShootMode.M && s.iso != 0) "M.M. menunjukkan sisihan meter" else "Pampasan pendedahan"
        Param.F -> if (physAp) "Bukaan fizikal tersedia" else "Kanta tetap f/${fixedAp?.let { fmtF(it) } ?: "-"} • f-stop disimulasi AI"
        Param.WB -> if (s.wb == WbMode.KELVIN) "2500K – 10000K" else ""
        Param.LOOK -> "Pratonton anggaran • gambar diproses penuh"
        else -> ""
    }
}

private fun fmtF(f: Float): String = if (f >= 10f) "%.0f".format(f) else "%.1f".format(f)
private fun gridIcon(g: Grid) = when (g) { Grid.OFF -> "#"; Grid.THIRDS -> "#3"; Grid.SQUARE -> "#□"; Grid.DIAGONAL -> "#╳" }
private fun driveShort(d: Drive) = when (d) {
    Drive.SINGLE -> "▢ S"; Drive.BURST -> "▣ Lo"; Drive.BURST_HI -> "▣ Hi"; Drive.TIMER3 -> "⏱3"; Drive.TIMER10 -> "⏱10"; Drive.INTERVAL -> "⧗ INT"
}
fun shortLook(id: String, name: String): String = if (id.startsWith("cl_")) name else name.removePrefix("Leica ").removePrefix("Xperia ")

// ------------------------------------------------------------------ KOMPONEN KECIL
@Composable
private fun Info(label: String, value: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(3.dp))
            .background(if (selected) XC.Accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = if (selected) XC.Accent else XC.Dim, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(value, color = if (!enabled) XC.Dim else if (selected) XC.Accent else XC.Text, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, fontFamily = XC.Mono, maxLines = 1)
    }
}

@Composable
private fun Tag(t: String, color: Color = Color.White, modifier: Modifier = Modifier) {
    Text(t, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.padding(bottom = 3.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable
private fun TopTag(t: String, on: Boolean, onClick: () -> Unit) {
    Text(t, color = if (on) XC.Accent else XC.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(3.dp)).clickable(onClick = onClick)
            .border(1.dp, if (on) XC.Accent else XC.Line, RoundedCornerShape(3.dp)).padding(horizontal = 7.dp, vertical = 3.dp))
}

@Composable
fun IconBtn(enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun RoundBtn(t: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(50.dp).clip(CircleShape).border(1.5.dp, if (on) XC.Accent else XC.Line, CircleShape)
            .background(if (on) XC.Accent.copy(alpha = 0.15f) else XC.Panel).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, color = if (on) XC.Accent else XC.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ShutterButton(running: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(78.dp).clip(CircleShape).border(3.dp, Color.White, CircleShape).padding(7.dp)
            .clip(CircleShape).background(if (running) XC.Red else Color.White).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (running) Box(Modifier.size(22.dp).background(Color.White, RoundedCornerShape(3.dp)))
    }
}

@Composable
private fun Thumb(uri: Uri?, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri) { value = loadThumb(ctx, uri, 160) }
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, XC.Line, RoundedCornerShape(6.dp))
            .background(XC.Panel).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            ?: Text("▦", color = XC.Dim, fontSize = 18.sp)
    }
}

suspend fun loadThumb(ctx: Context, uri: Uri?, px: Int): Bitmap? = withContext(Dispatchers.IO) {
    if (uri == null) return@withContext null
    try {
        if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.loadThumbnail(uri, Size(px, px), null)
        else ctx.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 })
        }
    } catch (_: Exception) { null }
}

@Composable
private fun Battery() {
    val ctx = LocalContext.current
    val pct by produceState(0) {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        while (true) { value = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY); delay(60_000) }
    }
    if (pct > 0) Text("▮ $pct%", color = if (pct <= 15) XC.Red else XC.Text, fontSize = 12.sp, fontFamily = XC.Mono)
}

@Composable
private fun MrDialog(vm: CameraViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = XC.Panel2,
        title = { Text("MEMORY RECALL", color = XC.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                for (i in 1..3) {
                    val m = vm.mrSlot(i)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("MR$i", color = XC.Text, fontWeight = FontWeight.Bold)
                            Text(m?.let { "${it.mode.label} • ${Stops.shutterLabel(it.shutterNs)} • ISO ${if (it.iso == 0) "AUTO" else it.iso} • ${it.wb.short}" } ?: "Kosong",
                                color = XC.Dim, fontSize = 11.sp)
                        }
                        Chip("Panggil", false, enabled = m != null) { vm.recallMr(i); onDismiss() }
                        Spacer(Modifier.width(6.dp))
                        Chip("Simpan", false) { vm.saveMr(i); onDismiss() }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup", color = XC.Text) } },
    )
}
