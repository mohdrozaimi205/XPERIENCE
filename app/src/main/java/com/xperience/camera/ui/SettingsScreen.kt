package com.xperience.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.camera.CameraViewModel
import com.xperience.camera.ai.APERTURES
import com.xperience.camera.ai.BokehStyle
import com.xperience.camera.camera.Aspect
import com.xperience.camera.camera.CamSettings
import com.xperience.camera.camera.Drive
import com.xperience.camera.camera.Dro
import com.xperience.camera.camera.FileFormat
import com.xperience.camera.camera.FocusMode
import com.xperience.camera.camera.Grid
import com.xperience.camera.camera.Metering
import com.xperience.camera.camera.WbMode

@Composable
fun SettingsScreen(vm: CameraViewModel, version: String, onCheckUpdate: () -> Unit, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val caps by vm.engine.caps.collectAsState()
    fun u(f: (CamSettings) -> CamSettings) = vm.update(f)

    Column(Modifier.fillMaxSize().background(XC.Bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali", tint = XC.Text) }
            Text("MENU", color = XC.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
            SectionTitle("1 · Shooting Settings")
            Choice("Format fail", FileFormat.entries.map { it.label }, s.format.ordinal,
                note = if (caps.raw && caps.supportsRawFormat) "RAW = DNG 16-bit dari sensor" else "RAW tidak disokong kamera ini") { i ->
                u { it.copy(format = FileFormat.entries[i]) }
            }
            Choice("Nisbah aspek", Aspect.entries.map { it.label }, s.aspect.ordinal) { i -> u { it.copy(aspect = Aspect.entries[i]) } }
            Choice("Drive mode", Drive.entries.map { it.label }, s.drive.ordinal) { i -> u { it.copy(drive = Drive.entries[i]) } }
            Choice("Bilangan burst", listOf(5, 10, 20, 30).map { "$it" }, listOf(5, 10, 20, 30).indexOf(s.burstCount).coerceAtLeast(0)) { i ->
                u { it.copy(burstCount = listOf(5, 10, 20, 30)[i]) }
            }
            Choice("Interval (saat)", listOf(2, 5, 10, 30, 60).map { "${it}s" }, listOf(2, 5, 10, 30, 60).indexOf(s.intervalSec).coerceAtLeast(0)) { i ->
                u { it.copy(intervalSec = listOf(2, 5, 10, 30, 60)[i]) }
            }
            Choice("Bilangan interval", listOf(10, 20, 50, 100).map { "$it" }, listOf(10, 20, 50, 100).indexOf(s.intervalCount).coerceAtLeast(0)) { i ->
                u { it.copy(intervalCount = listOf(10, 20, 50, 100)[i]) }
            }

            SectionTitle("2 · Exposure Settings")
            Choice("Mod meter", Metering.entries.map { it.label }, s.metering.ordinal) { i -> u { it.copy(metering = Metering.entries[i]) } }
            Toggle("Histogram", s.histogram) { v -> u { it.copy(histogram = v) } }
            Toggle("Amaran sorotan (zebra)", s.highlightWarn) { v -> u { it.copy(highlightWarn = v) } }
            Toggle("Amaran bayang", s.shadowWarn) { v -> u { it.copy(shadowWarn = v) } }
            Choice("DRO (Dynamic Range Optimizer)", Dro.entries.map { it.label }, s.dro.ordinal) { i -> u { it.copy(dro = Dro.entries[i]) } }

            SectionTitle("3 · Focus Settings")
            Choice("Mod fokus", FocusMode.entries.map { it.label }, s.focus.ordinal) { i -> u { it.copy(focus = FocusMode.entries[i]) } }
            Toggle("Focus peaking (MF)", s.peaking) { v -> u { it.copy(peaking = v) } }
            Toggle("Face priority AF (AUTO)", s.faceAf, enabled = caps.faceDetect) { v -> u { it.copy(faceAf = v) } }

            SectionTitle("4 · Image Quality")
            Choice("Kualiti JPEG", listOf(80, 90, 95, 100).map { "$it" }, listOf(80, 90, 95, 100).indexOf(s.jpegQuality).coerceAtLeast(2)) { i ->
                u { it.copy(jpegQuality = listOf(80, 90, 95, 100)[i]) }
            }
            Toggle("Simpan juga gambar asal (tanpa Look)", s.keepOriginal) { v -> u { it.copy(keepOriginal = v) } }
            Note("Gambar dengan Creative Look / DRO / AI Bokeh diproses dalam peranti pada resolusi sehingga ~12MP.")

            SectionTitle("5 · Colour & White Balance")
            Choice("White balance", WbMode.entries.map { it.label }, s.wb.ordinal) { i -> u { it.copy(wb = WbMode.entries[i]) } }
            Note(if (caps.manualPost) "Custom Kelvin guna gandaan warna sensor (2500K–10000K)." else "Kelvin dianggarkan ke pratetap terdekat (kamera tiada kawalan gandaan warna).")

            SectionTitle("6 · AI Photography")
            Toggle("AI Bokeh (depth map)", s.bokeh) { v -> u { it.copy(bokeh = v) } }
            Choice("Bukaan simulasi", APERTURES.map { "f/$it" }, APERTURES.indexOfFirst { it == s.aperture }.coerceAtLeast(0)) { i ->
                u { it.copy(aperture = APERTURES[i]) }
            }
            Choice("Gaya bokeh", BokehStyle.entries.map { it.label }, BokehStyle.entries.indexOfFirst { it.name == s.bokehStyle }.coerceAtLeast(0)) { i ->
                u { it.copy(bokehStyle = BokehStyle.entries[i].name) }
            }
            XSlider(s.bokehIntensity, 0f..1.5f, "Blur ${(s.bokehIntensity * 100).toInt()}%") { v -> u { it.copy(bokehIntensity = v) } }
            Note("Pemisahan subjek, rambut & tepi dibuat oleh AI dalam peranti (ML Kit). Gambar tidak dihantar ke internet.")

            SectionTitle("7 · Display Settings")
            Choice("Garis grid", Grid.entries.map { it.label }, s.grid.ordinal) { i -> u { it.copy(grid = Grid.entries[i]) } }
            Toggle("Aras elektronik", s.level) { v -> u { it.copy(level = v) } }

            SectionTitle("8 · Storage")
            Note("Gambar disimpan di DCIM/XPERIENCE (JPEG & DNG).")

            SectionTitle("9 · Custom Buttons")
            Toggle("Butang volume = shutter", s.volumeShutter) { v -> u { it.copy(volumeShutter = v) } }
            Toggle("Bunyi shutter", s.shutterSound) { v -> u { it.copy(shutterSound = v) } }

            SectionTitle("10 · Memory Recall")
            for (i in 1..3) {
                val m = vm.mrSlot(i)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MR$i", color = XC.Text, fontSize = 14.sp)
                        Text(m?.let { "${it.mode.label} • ISO ${if (it.iso == 0) "AUTO" else it.iso} • ${it.wb.label}" } ?: "Kosong", color = XC.Dim, fontSize = 11.sp)
                    }
                    Chip("Simpan tetapan semasa", false) { vm.saveMr(i) }
                }
            }

            SectionTitle("11 · About XPERIENCE")
            Note("XPERIENCE v$version — kamera profesional Android diilhamkan oleh pengalaman Photo Pro. Bukan produk Sony; tiada logo atau tanda dagangan Sony digunakan.")
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { Chip("Semak kemas kini", false, onClick = onCheckUpdate) }
            Text("LAPORAN KESERASIAN KAMERA", color = XC.Dim, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
            Text(vm.engine.compatReport(), color = XC.Text, fontSize = 12.sp, fontFamily = XC.Mono, lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!value) }.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (enabled) XC.Text else XC.Off, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(value, onChange, enabled = enabled, colors = SwitchDefaults.colors(
            checkedThumbColor = XC.Bg, checkedTrackColor = XC.Accent, uncheckedThumbColor = XC.Dim, uncheckedTrackColor = XC.Panel2))
    }
}

@Composable
private fun Choice(label: String, options: List<String>, selected: Int, note: String? = null, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = XC.Text, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
        if (note != null) Text(note, color = XC.Dim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(2.dp))
        ChipRow(options, selected, onSelect = onSelect)
    }
}

@Composable
private fun Note(t: String) {
    Text(t, color = XC.Dim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).width(380.dp))
}
