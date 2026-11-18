package com.xperience.camera

import com.xperience.camera.camera.Kelvin
import com.xperience.camera.camera.Stops
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StopsTest {
    @Test fun shutterLabels() {
        assertEquals("1/125", Stops.shutterLabel(8_000_000L))
        assertEquals("1/8000", Stops.shutterLabel(125_000L))
        assertEquals("1/30", Stops.shutterLabel(33_333_333L))
        assertEquals("1\"", Stops.shutterLabel(1_000_000_000L))
        assertEquals("30\"", Stops.shutterLabel(30_000_000_000L))
    }
    @Test fun evLabels() {
        assertEquals("±0.0", Stops.evLabel(0f))
        assertEquals("+0.3", Stops.evLabel(1 / 3f))
        assertEquals("−1.7", Stops.evLabel(-5 / 3f))
    }
    @Test fun kelvinGains() {
        val warm = Kelvin.relativeGains(3200)
        val cool = Kelvin.relativeGains(8000)
        // cahaya tungsten: kurangkan merah, tambah biru
        assertTrue(warm[0] < 1f && warm[1] > 1f)
        assertTrue(cool[0] > 1f && cool[1] < 1f)
        val d = Kelvin.relativeGains(5500)
        assertEquals(1f, d[0], 0.01f); assertEquals(1f, d[1], 0.01f)
    }
}
