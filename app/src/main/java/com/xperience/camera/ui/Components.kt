package com.xperience.camera.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Palet antara muka XPERIENCE (gelap profesional, aksen jingga). */
object XC {
    val Bg = Color(0xFF000000)
    val Panel = Color(0xFF0D0D0F)
    val Panel2 = Color(0xFF17181B)
    val Line = Color(0xFF2A2B2F)
    val Text = Color(0xFFEDEDED)
    val Dim = Color(0xFF8C8D92)
    val Off = Color(0xFF4A4B50)
    val Accent = Color(0xFFFF7A00)
    val Green = Color(0xFF39D98A)
    val Red = Color(0xFFFF3B47)
    val Mono = FontFamily.Monospace
}

val ValueStyle = TextStyle(color = XC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = when { !enabled -> XC.Off; selected -> XC.Accent; else -> XC.Text }
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, if (selected) XC.Accent else XC.Line, RoundedCornerShape(4.dp))
            .background(if (selected) XC.Accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = c, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}

/**
 * Dail mendatar gaya Alpha: tatal / leret, nilai di tengah dipilih.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ValueDial(labels: List<String>, selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    if (labels.isEmpty()) return
    val itemW = 62.dp
    val state = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(state)
    var userScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(selected, labels.size) {
        if (!state.isScrollInProgress) state.scrollToItem(selected.coerceIn(0, labels.size - 1))
    }
    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) { userScrolled = true; return@LaunchedEffect }
        if (!userScrolled) return@LaunchedEffect
        userScrolled = false
        val li = state.layoutInfo
        val center = (li.viewportStartOffset + li.viewportEndOffset) / 2
        val best = li.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) } ?: return@LaunchedEffect
        if (best.index != selected) onSelect(best.index)
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(54.dp)) {
        val pad = (maxWidth - itemW) / 2
        LazyRow(
            state = state,
            flingBehavior = fling,
            userScrollEnabled = enabled,
            contentPadding = PaddingValues(horizontal = pad),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(labels) { i, l ->
                val sel = i == selected
                Column(
                    Modifier.width(itemW).clickable(enabled = enabled) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.width(1.dp).height(if (i % 3 == 0) 10.dp else 6.dp).background(if (sel) XC.Accent else XC.Off))
                    Text(
                        l, color = if (!enabled) XC.Off else if (sel) XC.Accent else XC.Dim,
                        fontSize = if (sel) 16.sp else 13.sp, fontFamily = XC.Mono,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        // penanda tengah
        Box(Modifier.align(Alignment.TopCenter).width(2.dp).height(14.dp).background(XC.Accent))
    }
}

@Composable
fun ChipRow(options: List<String>, selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.fillMaxWidth().height(54.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(options) { i, o -> Chip(o, i == selected, enabled = enabled) { onSelect(i) } }
    }
}

@Composable
fun XSlider(value: Float, range: ClosedFloatingPointRange<Float>, label: String, modifier: Modifier = Modifier,
            onChangeFinished: (() -> Unit)? = null, onChange: (Float) -> Unit) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = XC.Dim, fontSize = 11.sp, modifier = Modifier.width(78.dp), maxLines = 1)
        Slider(
            value = value, onValueChange = onChange, valueRange = range, onValueChangeFinished = onChangeFinished,
            modifier = Modifier.weight(1f).height(32.dp),
            colors = SliderDefaults.colors(thumbColor = XC.Accent, activeTrackColor = XC.Accent, inactiveTrackColor = XC.Line),
        )
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(t.uppercase(), color = XC.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp))
}
