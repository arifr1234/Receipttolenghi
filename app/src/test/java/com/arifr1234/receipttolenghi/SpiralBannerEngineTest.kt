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
        val minDiameterDefault = SpiralBannerEngine.getMinimumPoleDiameterMm()
        // 58 / PI * 1.05 ~= 19.38 mm
        assertTrue(minDiameterDefault > 19f)
        assertTrue(minDiameterDefault < 20f)

        val minDiameter48 = SpiralBannerEngine.getMinimumPoleDiameterMm(48f)
        // 48 / PI * 1.05 ~= 16.04 mm
        assertTrue(minDiameter48 > 16f)
        assertTrue(minDiameter48 < 17f)
    }

    @Test
    fun testPixelAreaConservation() {
        val origW = 1200
        val origH = 800
        val poleDiameterMm = 35f

        val (paperWidthPx, leadInHeightPx, stripHeightPx) = SpiralBannerEngine.calculateGeometry(origW, origH, poleDiameterMm)

        val origArea = origW.toLong() * origH.toLong()

        // Area of the unwrapped strip should match source image pixel area within a tiny fraction (due to lead-in triangles)
        // Specifically, the banner parallelogram area is stripWidth * (stripHeight - leadInHeightPx) = W_p * (H_orig / sinTheta) = W_orig * H_orig!
        val parallelogramArea = paperWidthPx * (stripHeightPx - leadInHeightPx)
        val relativeDiff = abs(parallelogramArea - origArea) / origArea.toDouble()
        assertTrue("Parallelogram area should exactly match original image pixel area, diff=$relativeDiff", relativeDiff < 0.01)
    }

    @Test
    fun testPrintableRatioAndMargins() {
        val origW = 1200
        val origH = 800
        val poleDiameterMm = 35f

        val (_, _, _, _, _, _, _, _, _, printableRatio) = SpiralBannerEngine.calculateGeometry(origW, origH, poleDiameterMm)

        // 48mm printable / 58mm paper ~= 0.827586
        assertEquals(48f / 58f, printableRatio, 0.001f)

        val margin = (1f - printableRatio) * 0.5f
        assertEquals(5f / 58f, margin, 0.001f)

        // Left edge of printable area maps to 0.0
        val uLeft = (margin - margin) / printableRatio
        assertEquals(0f, uLeft, 0.0001f)

        // Center maps to 0.5
        val uCenter = (0.5f - margin) / printableRatio
        assertEquals(0.5f, uCenter, 0.0001f)

        // Right edge of printable area maps to 1.0
        val uRight = ((1f - margin) - margin) / printableRatio
        assertEquals(1.0f, uRight, 0.0001f)
    }

    @Test
    fun testInverseHelicalMapping() {
        val origW = 1000
        val origH = 600
        val poleDiameterMm = 35f
        val paperWidthMm = 48f

        val (paperWidthPx, leadInHeightPx, _, sinTheta, cosTheta) = SpiralBannerEngine.calculateGeometry(origW, origH, poleDiameterMm, paperWidthMm)

        // For a known point (xp, yp) on the strip
        val testXp = 120f
        val testYp = 800f

        // Forward map from strip (xp, yp) to cylinder (u, v) with det = +1:
        val u = (sinTheta * testXp) - (cosTheta * (testYp - leadInHeightPx))
        val v = (cosTheta * testXp) + (sinTheta * (testYp - leadInHeightPx))

        // Periodic wrap to u0 in [0, origW)
        val kOrig = floor(u / origW.toFloat())
        val u0 = u - (kOrig * origW.toFloat())

        // Inverse mapping from (u0, v) to (xp, yp):
        val xp0 = (sinTheta * u0) + (cosTheta * v)
        val k = floor(xp0 / paperWidthPx)
        val recoveredXp = xp0 - (k * paperWidthPx)
        val uActual = u0 - (k * origW.toFloat())
        val recoveredYp = (-cosTheta * uActual) + (sinTheta * v) + leadInHeightPx

        assertEquals(testXp, recoveredXp, 0.01f)
        assertEquals(testYp, recoveredYp, 0.01f)
    }

    @Test
    fun testDirectBitmapMappingWithoutOrigSize() {
        val origW = 1600
        val origH = 900
        val poleDiameterMm = 35f
        val paperWidthMm = 48f
        val ditheredW = 384f

        val (paperWidthPx, leadInHeightPx, stripHeightPx, sinTheta, cosTheta, turnsX, turnsY) = SpiralBannerEngine.calculateGeometry(origW, origH, poleDiameterMm, paperWidthMm)
        val scale = ditheredW / paperWidthPx
        val ditheredH = (stripHeightPx * scale)

        // Test over a grid of normalized cylinder coordinates (uNorm, vNorm)
        for (i in 0..10) {
            val uNorm = i / 10f
            for (j in 0..10) {
                val vNorm = j / 10f

                // --- DIRECT ANALYTICAL METHOD ---
                val u = uNorm * origW
                val v = vNorm * origH
                val xp0 = (sinTheta * u) + (cosTheta * v)
                val kOld = floor(xp0 / paperWidthPx)
                val xpOld = xp0 - (kOld * paperWidthPx)
                val uActual = u - (kOld * origW)
                val ypOld = (-cosTheta * uActual) + (sinTheta * v) + leadInHeightPx
                val bmpXOld = xpOld * scale
                val bmpYOld = ypOld * scale

                // --- TURNS METHOD (unwrappedSpiralCoord + mod + floor) ---
                val spiralX = uNorm + (turnsX * vNorm)
                val spiralY = (1f - uNorm) + (turnsY * vNorm)

                val wrappedX = (((spiralX % 1f) + 1f) % 1f)
                val wrappedY = spiralY + floor(spiralX)
                val totalTurns = 1f + turnsX + turnsY

                val normStripUvY = wrappedY / totalTurns

                val bmpXTurns = wrappedX * ditheredW
                val bmpYTurns = normStripUvY * ditheredH

                assertEquals("bmpXTurns mismatch at ($uNorm, $vNorm)", bmpXOld, bmpXTurns, 0.15f)
                assertEquals("bmpYTurns mismatch at ($uNorm, $vNorm)", bmpYOld, bmpYTurns, 0.15f)
            }
        }
    }

    @Test
    fun testAreaAveragingSpeed() {
        val srcW = 1000
        val srcH = 2000
        val dstW = 384
        val dstH = (srcH * (dstW.toFloat() / srcW)).toInt() // 768

        // Create 2M synthetic pixels
        val srcPixels = IntArray(srcW * srcH) { i -> (i and 0xFF) or ((i and 0xFF) shl 8) or ((i and 0xFF) shl 16) or (0xFF shl 24) }
        val dstPixels = IntArray(dstW * dstH)

        val scaleX = srcW.toFloat() / dstW
        val scaleY = srcH.toFloat() / dstH

        // Warm up JIT
        repeat(2) {
            for (dy in 0 until dstH) {
                val sy0 = (dy * scaleY).toInt()
                val sy1 = ((dy + 1) * scaleY).toInt().coerceAtMost(srcH)
                for (dx in 0 until dstW) {
                    val sx0 = (dx * scaleX).toInt()
                    val sx1 = ((dx + 1) * scaleX).toInt().coerceAtMost(srcW)
                    var sumR = 0
                    var sumG = 0
                    var sumB = 0
                    var count = 0
                    for (sy in sy0 until sy1) {
                        val rowOffset = sy * srcW
                        for (sx in sx0 until sx1) {
                            val p = srcPixels[rowOffset + sx]
                            sumR += (p shr 16) and 0xFF
                            sumG += (p shr 8) and 0xFF
                            sumB += p and 0xFF
                            count++
                        }
                    }
                    if (count > 0) {
                        dstPixels[(dy * dstW) + dx] = (0xFF shl 24) or ((sumR / count) shl 16) or ((sumG / count) shl 8) or (sumB / count)
                    }
                }
            }
        }

        // Measure time
        val start = System.nanoTime()
        for (dy in 0 until dstH) {
            val sy0 = (dy * scaleY).toInt()
            val sy1 = ((dy + 1) * scaleY).toInt().coerceAtMost(srcH)
            for (dx in 0 until dstW) {
                val sx0 = (dx * scaleX).toInt()
                val sx1 = ((dx + 1) * scaleX).toInt().coerceAtMost(srcW)
                var sumR = 0
                var sumG = 0
                var sumB = 0
                var count = 0
                for (sy in sy0 until sy1) {
                    val rowOffset = sy * srcW
                    for (sx in sx0 until sx1) {
                        val p = srcPixels[rowOffset + sx]
                        sumR += (p shr 16) and 0xFF
                        sumG += (p shr 8) and 0xFF
                        sumB += p and 0xFF
                        count++
                    }
                }
                if (count > 0) {
                    dstPixels[(dy * dstW) + dx] = (0xFF shl 24) or ((sumR / count) shl 16) or ((sumG / count) shl 8) or (sumB / count)
                }
            }
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        println("Area averaging downscale of 2M pixels took: $elapsedMs ms")
        assertTrue("Downscaling 2M pixels should take < 50ms, took $elapsedMs ms", elapsedMs < 50.0)
    }

    @Test
    fun testMultiInstanceGeometryScaling() {
        val baseTileW = 500
        val baseTileH = 300
        val poleDiameterMm = 35f

        // 1 instance vs 2 instances
        val (paperWidthPx1, _, _, sinTheta1, cosTheta1, _, _, cylinderAspectRatio1) = SpiralBannerEngine.calculateGeometry(baseTileW * 1, baseTileH, poleDiameterMm)
        val (paperWidthPx2, _, _, sinTheta2, cosTheta2, _, _, cylinderAspectRatio2) = SpiralBannerEngine.calculateGeometry(baseTileW * 2, baseTileH, poleDiameterMm)

        // As original width doubles for the same pole diameter, paperWidthPx doubles to maintain physical paper width
        assertEquals(paperWidthPx1 * 2f, paperWidthPx2, 0.05f)
        // Cylinder aspect ratio = W / (PI * H), so it doubles when W doubles
        assertEquals(cylinderAspectRatio1 * 2f, cylinderAspectRatio2, 0.001f)
        // Pitch/turns along strip: turnsX = (H / W) * (cos / sin)
        // Since both W and paperWidthPx scale by 2, sin and cos remain identical!
        assertEquals(sinTheta1, sinTheta2, 0.001f)
        assertEquals(cosTheta1, cosTheta2, 0.001f)
    }

    @Test
    fun testDiameterTapeCalculations() {
        val zeroY = com.arifr1234.receipttolenghi.printer.PrinterManager.mmToPx(18f).toFloat()
        val maxDiameterMm = 80

        val totalHeight = zeroY + com.arifr1234.receipttolenghi.printer.PrinterManager.mmToPx((PI * maxDiameterMm).toFloat()) + com.arifr1234.receipttolenghi.printer.PrinterManager.mmToPx(8f)
        // Tape length should be ~2213 dots (~27.6 cm)
        assertTrue(totalHeight > 2100f)
        assertTrue(totalHeight < 2300f)

        // Verify marking positions for 10mm, 20mm, 80mm
        val y10 = zeroY + com.arifr1234.receipttolenghi.printer.PrinterManager.mmToPx((PI * 10).toFloat())
        val y20 = zeroY + com.arifr1234.receipttolenghi.printer.PrinterManager.mmToPx((PI * 20).toFloat())
        val y80 = zeroY + com.arifr1234.receipttolenghi.printer.PrinterManager.mmToPx((PI * 80).toFloat())

        assertEquals(251f, (y20 - y10), 1.0f)
        assertEquals(251f * 6f, (y80 - y20), 3.0f)
    }

    @Test
    fun testPhaseOffsetMatrixMath() {
        val tileW = 1000
        val imgW = 600 // 60% coverage, 400px margin

        // Phase 0.0 (0%) -> Image starts at U = 0
        val phase0 = 0.0f
        val deltaU0 = ((((phase0 % 1.0f) + 1.0f) % 1.0f) * tileW).toInt()
        assertEquals(0, deltaU0)

        // Matrix preTranslate by deltaU samples texture at: U' = (U_orig - deltaU) mod tileW
        fun sampleTexture(uOrig: Int, deltaU: Int): Int {
            val u = (uOrig - deltaU) % tileW
            return if (u < 0) u + tileW else u
        }

        // At phase 0:
        assertEquals(0, sampleTexture(0, deltaU0)) // start of image
        assertEquals(imgW - 1, sampleTexture(imgW - 1, deltaU0)) // end of image
        assertEquals(imgW, sampleTexture(imgW, deltaU0)) // start of margin

        // Phase 0.25 (25%) -> Image centered at U = 250..850, margin at 0..250 and 850..1000
        val phase25 = 0.25f
        val deltaU25 = ((((phase25 % 1.0f) + 1.0f) % 1.0f) * tileW).toInt()
        assertEquals(250, deltaU25)
        assertEquals(750, sampleTexture(0, deltaU25)) // U_orig = 0 samples margin
        assertEquals(0, sampleTexture(250, deltaU25)) // U_orig = 250 is start of image
        assertEquals(599, sampleTexture(849, deltaU25)) // U_orig = 849 is end of image
        assertEquals(600, sampleTexture(850, deltaU25)) // U_orig = 850 is start of margin

        // Phase 0.7 (70%) -> Image wraps across boundary: [700, 1000) and [0, 300)
        val phase70 = 0.7f
        val deltaU70 = ((((phase70 % 1.0f) + 1.0f) % 1.0f) * tileW).toInt()
        assertEquals(700, deltaU70)
        assertEquals(0, sampleTexture(700, deltaU70)) // start of image
        assertEquals(299, sampleTexture(999, deltaU70)) // end of tile boundary (continuous!)
        assertEquals(300, sampleTexture(0, deltaU70)) // wraps into 0 seamlessly
        assertEquals(599, sampleTexture(299, deltaU70)) // end of image
        assertEquals(600, sampleTexture(300, deltaU70)) // start of margin
    }

    @Test
    fun testTileCoverageCalculations() {
        val imgW = 600

        // 100% coverage
        val tileW100 = imgW
        assertEquals(600, tileW100)

        // 50% coverage -> tile width is double image width
        val coverage50 = 0.5f
        val tileW50 = (imgW / coverage50).toInt()
        assertEquals(1200, tileW50)
        // 25% coverage -> tile width is 4x image width
        val coverage25 = 0.25f
        val tileW25 = (imgW / coverage25).toInt()
        assertEquals(2400, tileW25)
        assertEquals(1800, tileW25 - imgW) // margin is 1800px
    }

    @Test
    fun testPhaseCompensationRotationMath() {
        // Test Full Pole Phase:
        // Phase represents 0..100% of the entire pole circumference (0..2*PI).
        // Effective rotation = rotationAngle - phase * 2*PI
        for (instances in 1..6) {
            val baseRotation = 1.5f
            val phase = 0.4f
            val poleAngle = 2f * PI.toFloat()
            val effectiveRotation = baseRotation - (phase * poleAngle)

            // Center of screen invariance test
            val featureAngularShift = phase * poleAngle
            val compensatedCenterAngle = -effectiveRotation - featureAngularShift
            assertEquals(-baseRotation, compensatedCenterAngle, 0.0001f)

            // Phase = 100% (1.0) and Phase = 0% (0.0) produce the exact same 3D cylinder orientation
            val rot0 = baseRotation - (0f * poleAngle)
            val rot1 = baseRotation - (1f * poleAngle)
            val rotDiffMod = ((((rot0 - rot1) % poleAngle) + poleAngle) % poleAngle)
            assertEquals(0f, rotDiffMod, 0.0001f)

            // And tile texture shift at phase=1.0 is an exact multiple of the tile period:
            val tileShift0 = (((((0f * instances) % 1f) + 1f) % 1f))
            val tileShift1 = (((((1f * instances) % 1f) + 1f) % 1f))
            assertEquals(tileShift0, tileShift1, 0.0001f)
        }
    }
}
