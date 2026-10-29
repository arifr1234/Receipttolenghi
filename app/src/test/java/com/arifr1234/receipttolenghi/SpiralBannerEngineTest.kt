package com.arifr1234.receipttolenghi

import com.arifr1234.receipttolenghi.printer.SpiralBannerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor

class SpiralBannerEngineTest {

    @Test
    fun testMinimumPoleDiameter() {
        val minDiameter = SpiralBannerEngine.getMinimumPoleDiameterMm(48f)
        // 48 / PI * 1.05 ~= 16.04 mm
        assertTrue(minDiameter > 16f)
        assertTrue(minDiameter < 17f)
    }

    @Test
    fun testPixelAreaConservation() {
        val origW = 1200
        val origH = 800
        val poleDiameterMm = 35f
        val paperWidthMm = 48f

        val geo = SpiralBannerEngine.calculateGeometry(origW, origH, poleDiameterMm, paperWidthMm)

        val origArea = origW.toLong() * origH.toLong()
        val stripArea = geo.paperWidthPx.toLong() * geo.stripHeightPx.toLong()

        // Area of the unwrapped strip should match source image pixel area within a tiny fraction (due to lead-in triangles)
        // Specifically, the banner parallelogram area is stripWidth * (stripHeight - a) = W_p * (H_orig / sinTheta) = W_orig * H_orig!
        val parallelogramArea = geo.paperWidthPx * (geo.stripHeightPx - geo.a)
        val relativeDiff = abs(parallelogramArea - origArea) / origArea.toDouble()
        assertTrue("Parallelogram area should exactly match original image pixel area, diff=$relativeDiff", relativeDiff < 0.01)
    }

    @Test
    fun testInverseHelicalMapping() {
        val origW = 1000
        val origH = 600
        val poleDiameterMm = 35f
        val paperWidthMm = 48f

        val geo = SpiralBannerEngine.calculateGeometry(origW, origH, poleDiameterMm, paperWidthMm)

        // For a known point (xp, yp) on the strip
        val testXp = 120f
        val testYp = 800f

        // Forward map from strip (xp, yp) to cylinder (u, v):
        // U = Yp * cosTheta - Xp * sinTheta + Wp * sinTheta
        // V = Yp * sinTheta + Xp * cosTheta - a * sinTheta
        val u = testYp * geo.cosTheta - testXp * geo.sinTheta + geo.paperWidthPx * geo.sinTheta
        val v = testYp * geo.sinTheta + testXp * geo.cosTheta - geo.a * geo.sinTheta

        // Periodic wrap to u0 in [0, origW)
        val kOrig = floor(u / origW.toFloat())
        val u0 = u - kOrig * origW.toFloat()

        // Closed-form inverse mapping from (u0, v) to (xp, yp):
        val uPrime = u0 - geo.paperWidthPx * geo.sinTheta
        val vPrime = v + geo.a * geo.sinTheta

        val xp0 = -geo.sinTheta * uPrime + geo.cosTheta * vPrime
        val k = floor(xp0 / geo.paperWidthPx)
        val recoveredXp = xp0 - k * geo.paperWidthPx
        val recoveredYp = geo.cosTheta * (uPrime + k * origW.toFloat()) + geo.sinTheta * vPrime

        assertEquals(testXp, recoveredXp, 0.01f)
        assertEquals(testYp, recoveredYp, 0.01f)
    }
}
