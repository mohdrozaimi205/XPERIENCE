package com.xperience.camera.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import com.xperience.camera.CameraViewModel
import com.xperience.camera.ai.APERTURES
import com.xperience.camera.camera.Dro
import com.xperience.camera.image.Collection
import com.xperience.camera.image.Looks
import com.xperience.camera.process.PhotoProcessor
import com.xperience.camera.process.ProcessOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GalleryScreen(vm: CameraViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val photos by produceState(emptyList<CameraViewModel.Companion.Photo>(), refresh) {
        value = withContext(Dispatchers.IO) { CameraViewModel.queryPhotos(ctx.applicationContext as Application) }
    }
    var open by remember { mutableStateOf<Uri?>(null) }

    if (open != null) {
        BackHandler { open = null; refresh++ }
        Viewer(vm, open!!) { open = null; refresh++ }
        return
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(XC.Bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali", tint = XC.Text) }
            Text("GALERI XPERIENCE", color = XC.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${photos.size}", color = XC.Dim, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
        }
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Belum ada gambar", color = XC.Dim) }
        } else LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(2.dp)) {
            items(photos, key = { it.uri.toString() }) { p ->
                val bmp by produceState<Bitmap?>(null, p.uri) { value = loadThumb(ctx, p.uri, 320) }
                Box(Modifier.aspectRatio(1f).padding(1.dp).background(XC.Panel).clickable { open = p.uri }) {
                    bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    if (p.mime.contains("dng")) Text("RAW", color = XC.Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(XC.Bg).padding(horizontal = 3.dp))
                }
            }
        }
    }
}

private fun exifSummary(ctx: android.content.Context, uri: Uri): List<Pair<String, String>> = try {
    ctx.contentResolver.openInputStream(uri)!!.use { s ->
        val e = ExifInterface(s)
        fun g(t: String) = e.getAttribute(t)
        val exp = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
        val ss = if (exp <= 0) "-" else if (exp >= 0.3) "%.1f\"".format(exp) else "1/${Math.round(1 / exp)}"
        listOfNotNull(
            "Shutter" to ss,
            "F" to (g(ExifInterface.TAG_F_NUMBER) ?: "-"),
            "ISO" to (g(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) ?: "-"),
            "Fokal" to ((g(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)?.let { "${it}mm eq" }) ?: g(ExifInterface.TAG_FOCAL_LENGTH) ?: "-"),
            "EV" to (g(ExifInterface.TAG_EXPOSURE_BIAS_VALUE) ?: "0"),
            "WB" to (if (g(ExifInterface.TAG_WHITE_BALANCE) == "1") "Manual" else "Auto"),
            "Saiz" to "${g(ExifInterface.TAG_PIXEL_X_DIMENSION) ?: g(ExifInterface.TAG_IMAGE_WIDTH) ?: "?"}×${g(ExifInterface.TAG_PIXEL_Y_DIMENSION) ?: g(ExifInterface.TAG_IMAGE_LENGTH) ?: "?"}",
            "Peranti" to "${g(ExifInterface.TAG_MAKE) ?: ""} ${g(ExifInterface.TAG_MODEL) ?: ""}".trim(),
            "Tarikh" to (g(ExifInterface.TAG_DATETIME_ORIGINAL) ?: g(ExifInterface.TAG_DATETIME) ?: "-"),
            g(ExifInterface.TAG_SOFTWARE)?.let { "Perisian" to it },
        )
    }
} catch (_: Exception) { emptyList() }

@Composable
private fun Viewer(vm: CameraViewModel, uri: Uri, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(uri) }
    val bmp by produceState<Bitmap?>(null, current) {
        value = withContext(Dispatchers.IO) { PhotoProcessor.decodeUpright({ ctx.contentResolver.openInputStream(current)!! }, 4_000_000) }
    }
    val exif by produceState(emptyList<Pair<String, String>>(), current) { value = withContext(Dispatchers.IO) { exifSummary(ctx, current) } }
    var edit by remember { mutableStateOf(false) }
    var opts by remember { mutableStateOf(ProcessOptions()) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var working by remember { mutableStateOf(false) }

    fun rerender() {
        val src = bmp ?: return
        scope.launch {
            working = true
            val small = withContext(Dispatchers.Default) {
                val k = (1400f / maxOf(src.width, src.height)).coerceAtMost(1f)
                Bitmap.createScaledBitmap(src, (src.width * k).toInt(), (src.height * k).toInt(), true)
            }
            preview = if (opts.active) PhotoProcessor.render(small, opts) else null
            working = false
        }
    }

    Column(Modifier.fillMaxSize().background(XC.Bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali", tint = XC.Text) }
            Spacer(Modifier.weight(1f))
            Chip(if (edit) "Tutup Edit" else "Edit", edit) { edit = !edit; if (!edit) { preview = null; opts = ProcessOptions() } }
            IconBtn(onClick = {
                val i = Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, current)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(Intent.createChooser(i, "Kongsi"))
            }) { Icon(Icons.Outlined.Share, "Kongsi", tint = XC.Text) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val show = preview ?: bmp
            show?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                ?: CircularProgressIndicator(color = XC.Accent)
            if (working) CircularProgressIndicator(color = XC.Accent, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
        if (!edit) {
            LazyRow(Modifier.fillMaxWidth().background(XC.Panel).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 14.dp)) {
                items(exif) { (k, v) ->
                    Column {
                        Text(k.uppercase(), color = XC.Dim, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(v, color = XC.Text, fontSize = 13.sp, fontFamily = XC.Mono)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().background(XC.Panel2).padding(bottom = 8.dp)) {
                var tab by remember { mutableStateOf(Collection.SONY) }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(12.dp)) {
                    items(listOf(Collection.SONY, Collection.LEICA, Collection.HARCOURT, Collection.FILM, Collection.BW, Collection.CINEMATIC)) { c ->
                        Text(c.title, color = if (c == tab) XC.Accent else XC.Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { tab = c })
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                    item { Chip("OFF", opts.lookId == null) { opts = opts.copy(lookId = null); rerender() } }
                    items(Looks.byCollection(tab), key = { it.id }) { l ->
                        Chip(shortLook(l.id, l.name), opts.lookId == l.id) { opts = opts.copy(lookId = l.id); rerender() }
                    }
                }
                XSlider(opts.lookIntensity, 0f..1.5f, "Intensiti", onChangeFinished = { rerender() }) { opts = opts.copy(lookIntensity = it) }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip("AI Bokeh", opts.bokeh) { opts = opts.copy(bokeh = !opts.bokeh); rerender() }
                    Spacer(Modifier.padding(3.dp))
                    Chip("DRO", opts.dro != Dro.OFF) { opts = opts.copy(dro = if (opts.dro == Dro.OFF) Dro.AUTO else Dro.OFF); rerender() }
                }
                if (opts.bokeh) {
                    ChipRow(APERTURES.map { "f/$it" }, APERTURES.indexOfFirst { it == opts.aperture }.coerceAtLeast(0)) { i ->
                        opts = opts.copy(aperture = APERTURES[i]); rerender()
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    Chip(if (working) "Memproses…" else "Simpan salinan", true, enabled = !working && opts.active) {
                        scope.launch {
                            working = true
                            val u = vm.editAndSave(current, opts)
                            working = false
                            if (u != null) { vm.toast.value = "Disimpan ke DCIM/XPERIENCE"; edit = false; preview = null; opts = ProcessOptions(); current = u }
                        }
                    }
                }
            }
        }
    }
}
