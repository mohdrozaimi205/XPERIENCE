package com.xperience.camera.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xperience.camera.camera.FrameStats
import com.xperience.camera.camera.Grid
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ln
import kotlin.math.max

@Composable
fun GridOverlay(grid: Grid) {
    if (grid == Grid.OFF) return
    Canvas(Modifier.fillMaxSize()) {
        val c = Color.White.copy(alpha = 0.35f)
        val w = size.width; val h = size.height
        when (grid) {
            Grid.THIRDS -> for (i in 1..2) {
                drawLine(c, Offset(w * i / 3, 0f), Offset(w * i / 3, h), 1.2f)
                drawLine(c, Offset(0f, h * i / 3), Offset(w, h * i / 3), 1.2f)
            }
            Grid.SQUARE -> {
                val step = w / 6
                var x = step; while (x < w) { drawLine(c, Offset(x, 0f), Offset(x, h), 1f); x += step }
                var y = step; while (y < h) { drawLine(c, Offset(0f, y), Offset(w, y), 1f); y += step }
            }
            Grid.DIAGONAL -> {
                for (i in 1..2) {
                    drawLine(c, Offset(w * i / 3, 0f), Offset(w * i / 3, h), 1.2f)
                    drawLine(c, Offset(0f, h * i / 3), Offset(w, h * i / 3), 1.2f)
                }
                drawLine(c, Offset(0f, 0f), Offset(w, h), 1f); drawLine(c, Offset(w, 0f), Offset(0f, h), 1f)
            }
            else -> {}
        }
    }
}

/** Focus peaking + amaran sorotan/bayang. */
@Composable
fun AnalysisOverlay(st: FrameStats?, peaking: Boolean, hi: Boolean, lo: Boolean, mirror: Boolean) {
    if (st == null || (!peaking && !hi && !lo)) return
    Canvas(Modifier.fillMaxSize()) {
        val cw = size.width / st.gw; val ch = size.height / st.gh
        val peakC = Color(0xFFFF2D55); val hiC = Color(0xCCFF00C8); val loC = Color(0xAA2E6BFF)
        for (y in 0 until st.gh) for (x in 0 until st.gw) {
            val i = y * st.gw + x
            val dx = if (mirror) st.gw - 1 - x else x
            val o = Offset(dx * cw, y * ch)
            if (hi && st.clipHigh[i]) {
                // corak zebra
                if ((x + y) % 2 == 0) drawRect(hiC, o, Size(cw, ch))
            } else if (lo && st.clipLow[i]) {
                if ((x + y) % 2 == 0) drawRect(loC, o, Size(cw, ch))
            }
            if (peaking && st.peak[i]) drawRect(peakC, Offset(o.x + cw * 0.25f, o.y + ch * 0.25f), Size(cw * 0.5f, ch * 0.5f))
        }
    }
}

@Composable
fun Histogram(st: FrameStats?, modifier: Modifier = Modifier) {
    if (st == null) return
    Canvas(modifier.size(110.dp, 54.dp).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(3.dp))) {
        val h = st.hist
        var mx = 1.0
        for (v in h) mx = max(mx, ln(1.0 + v))
        val bw = size.width / h.size
        val path = Path().apply {
            moveTo(0f, size.height)
            for (i in h.indices) {
                val y = size.height - (ln(1.0 + h[i]) / mx * (size.height - 4)).toFloat()
                lineTo(i * bw + bw / 2, y)
            }
            lineTo(size.width, size.height); close()
        }
        drawPath(path, Color.White.copy(alpha = 0.75f))
        drawLine(Color.White.copy(alpha = 0.2f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height))
        if (h.last() > h.sum() * 0.01) drawRect(XC.Red, Offset(size.width - 3, 0f), Size(3f, size.height))
        if (h.first() > h.sum() * 0.02) drawRect(Color(0xFF2E6BFF), Offset(0f, 0f), Size(3f, size.height))
    }
}

/** Penunjuk aras elektronik (akselerometer). */
@Composable
fun LevelOverlay() {
    val ctx = LocalContext.current
    var roll by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val r = Math.toDegrees(atan2(e.values[0].toDouble(), e.values[1].toDouble())).toFloat()
                roll = roll * 0.8f + r * 0.2f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (acc != null) sm.registerListener(l, acc, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }
    Canvas(Modifier.fillMaxSize()) {
        // dalam potret, landskap (±90°) juga dikesan
        var a = roll
        if (abs(a) > 45f) a = if (a > 0) a - 90f else a + 90f
        val ok = abs(a) < 1f
        val c = if (ok) XC.Green else Color.White.copy(alpha = 0.8f)
        val cx = size.width / 2; val cy = size.height / 2
        val len = size.width * 0.18f
        drawLine(Color.White.copy(alpha = 0.4f), Offset(cx - len * 1.6f, cy), Offset(cx - len * 1.15f, cy), 2f)
        drawLine(Color.White.copy(alpha = 0.4f), Offset(cx + len * 1.15f, cy), Offset(cx + len * 1.6f, cy), 2f)
        rotate(-a, Offset(cx, cy)) { drawLine(c, Offset(cx - len, cy), Offset(cx + len, cy), if (ok) 3f else 2f) }
    }
}

@Composable
fun FocusBox(nx: Float, ny: Float, locked: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val s = size.minDimension * 0.16f
        val x = nx * size.width; val y = ny * size.height
        val c = if (locked) XC.Green else Color.White
        val k = s * 0.28f
        val l = x - s / 2; val t = y - s / 2; val r = x + s / 2; val b = y + s / 2
        for ((p, q) in listOf(
            Offset(l, t) to Offset(l + k, t), Offset(l, t) to Offset(l, t + k),
            Offset(r, t) to Offset(r - k, t), Offset(r, t) to Offset(r, t + k),
            Offset(l, b) to Offset(l + k, b), Offset(l, b) to Offset(l, b - k),
            Offset(r, b) to Offset(r - k, b), Offset(r, b) to Offset(r, b - k),
        )) drawLine(c, p, q, 3f)
    }
}

/** Skala meter pendedahan −3..+3 (gaya M.M. Alpha). */
@Composable
fun ExposureScale(ev: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(22.dp).padding(horizontal = 40.dp)) {
        val w = size.width; val cy = size.height * 0.4f
        for (i in -9..9) {
            val x = w / 2 + i * w / 18f
            val h = if (i % 3 == 0) 9f else 5f
            drawLine(Color.White.copy(alpha = if (i == 0) 0.9f else 0.55f), Offset(x, cy - h), Offset(x, cy), 1.6f)
        }
        val v = ev.coerceIn(-3.2f, 3.2f)
        val x = w / 2 + v * w / 6f
        val c = if (abs(ev) > 3f) XC.Red else XC.Accent
        val p = Path().apply { moveTo(x, cy + 2); lineTo(x - 6, cy + 11); lineTo(x + 6, cy + 11); close() }
        drawPath(p, c)
    }
}

@Composable
fun Crosshair() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(Color.White.copy(alpha = 0.5f), size.minDimension * 0.06f, style = Stroke(1.5f))
    }
}

@Composable
fun Spacer4() = Box(Modifier.width(4.dp))
