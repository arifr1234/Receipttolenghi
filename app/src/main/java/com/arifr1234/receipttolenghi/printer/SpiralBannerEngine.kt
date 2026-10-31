package com.arifr1234.receipttolenghi.printer

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import com.arifr1234.receipttolenghi.utils.BrightnessCurve
import com.arifr1234.receipttolenghi.utils.applyToneCurve
import com.arifr1234.receipttolenghi.utils.scaleAntiAliased
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class SpiralGeometry(
    val paperWidthPx: Float,
    val leadInHeightPx: Float,
    val stripHeightPx: Int,
    val sinTheta: Float,
    val cosTheta: Float,
    val turnsX: Float,
    val turnsY: Float,
    val cylinderAspectRatio: Float,
    val estimatedLengthCm: Float,
    val printableRatio: Float
)

object SpiralBannerEngine {

    const val MIN_POLE_DIAMETER_RATIO = 1.05f

    fun getMinimumPoleDiameterMm(paperWidthMm: Float = PrinterManager.PAPER_WIDTH_MM): Float {
        return ((paperWidthMm / PI.toFloat()) * MIN_POLE_DIAMETER_RATIO)
    }

    fun calculateGeometry(
        originalWidth: Int,
        originalHeight: Int,
        poleDiameterMm: Float,
        paperWidthMm: Float = PrinterManager.PAPER_WIDTH_MM,
        printableWidthMm: Float = PrinterManager.PRINTABLE_WIDTH_MM
    ): SpiralGeometry {
        val minDiameter = getMinimumPoleDiameterMm(paperWidthMm)
        val safeDiameter = max(poleDiameterMm, minDiameter)
        val perimeterMm = (PI * safeDiameter).toFloat()

        val paperWidthPx = (paperWidthMm * (originalWidth.toFloat() / perimeterMm))
            .coerceAtMost(originalWidth.toFloat() * 0.999f)

        val leadInHeightPx = sqrt(originalWidth.toFloat().pow(2) - paperWidthPx.pow(2))
        val sinTheta = paperWidthPx / originalWidth.toFloat()
        val cosTheta = leadInHeightPx / originalWidth.toFloat()

        val stripHeightPx = max((leadInHeightPx + originalHeight.toFloat() / sinTheta).roundToInt(), 1)

        val imageAspect = originalHeight.toFloat() / originalWidth.toFloat()
        val turnsX = imageAspect * (cosTheta / sinTheta)
        val turnsY = imageAspect * (sinTheta / cosTheta)

        val cylinderAspectRatio = originalWidth.toFloat() / (PI.toFloat() * originalHeight.toFloat())
        val estimatedLengthCm = (stripHeightPx.toFloat() / paperWidthPx) * (paperWidthMm / 10f)
        val printableRatio = (printableWidthMm / paperWidthMm).coerceIn(0.1f, 1.0f)

        return SpiralGeometry(
            paperWidthPx = paperWidthPx,
            leadInHeightPx = leadInHeightPx,
            stripHeightPx = stripHeightPx,
            sinTheta = sinTheta,
            cosTheta = cosTheta,
            turnsX = turnsX,
            turnsY = turnsY,
            cylinderAspectRatio = cylinderAspectRatio,
            estimatedLengthCm = estimatedLengthCm,
            printableRatio = printableRatio
        )
    }

    /**
     * Stage 1: Unwraps the source image into the continuous helical paper strip.
     * Uses BitmapShader with TileMode.REPEAT in the horizontal direction.
     * Phase offset is handled directly by translating the shader matrix.
     */
    fun unwrapToStrip(
        sourceBitmap: Bitmap,
        geometry: SpiralGeometry,
        phase: Float = 0f
    ): Bitmap {
        val stripWidth = max(geometry.paperWidthPx.roundToInt(), 1)
        val stripHeight = max(geometry.stripHeightPx, 1)

        val outBitmap = createBitmap(stripWidth, stripHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)
        canvas.drawColor(Color.WHITE)

        val shader = BitmapShader(sourceBitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)

        // Maps Bitmap coordinates (U, V) -> Strip coordinates (Xp, Yp):
        // Xp =  sinTheta * U + cosTheta * V
        // Yp = -cosTheta * U + sinTheta * V + leadInHeightPx
        // det = sinTheta^2 + cosTheta^2 = +1.0 (pure rotation without mirroring)
        val shaderMatrix = Matrix().apply {
            setValues(floatArrayOf(
                geometry.sinTheta, geometry.cosTheta, 0f,
                -geometry.cosTheta, geometry.sinTheta, geometry.leadInHeightPx,
                0f, 0f, 1f
            ))
            val phaseOffsetPx = ((phase % 1.0f + 1.0f) % 1.0f) * sourceBitmap.width
            if (phaseOffsetPx != 0f) {
                preTranslate(phaseOffsetPx, 0f)
            }
        }
        shader.setLocalMatrix(shaderMatrix)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.shader = shader
        }

        val bannerPath = android.graphics.Path().apply {
            moveTo(stripWidth.toFloat(), 0f)
            lineTo(0f, geometry.leadInHeightPx)
            lineTo(0f, stripHeight.toFloat())
            lineTo(stripWidth.toFloat(), stripHeight.toFloat() - geometry.leadInHeightPx)
            close()
        }

        canvas.drawPath(bannerPath, paint)
        return outBitmap
    }

    /**
     * Stage 2: Crops the centered printable section of the unwrapped strip,
     * downscales it to printer resolution, applies brightness tone curve, and dithers.
     */
    fun processForPrinter(
        unwrappedStrip: Bitmap,
        geometry: SpiralGeometry,
        brightnessMultiplier: Float = 2.0f,
        brightnessCurve: BrightnessCurve = BrightnessCurve.CURVE,
        ditheringAlgorithm: DitheringAlgorithm = DitheringAlgorithm.FLOYD_STEINBERG,
        printerWidthPx: Int = PrinterManager.MAX_PRINTER_WIDTH_PX
    ): Bitmap {
        val targetWidth = max(printerWidthPx, 1)

        // Extract centered printable section
        val marginPx = ((unwrappedStrip.width * (1f - geometry.printableRatio)) * 0.5f).roundToInt()
        val printableWidthPx = (unwrappedStrip.width - 2 * marginPx).coerceAtLeast(1)
        val printableStrip = if (marginPx > 0 && printableWidthPx < unwrappedStrip.width) {
            Bitmap.createBitmap(unwrappedStrip, marginPx, 0, printableWidthPx, unwrappedStrip.height)
        } else {
            unwrappedStrip
        }

        val scale = targetWidth.toFloat() / printableStrip.width.toFloat()
        val targetHeight = max((printableStrip.height.toFloat() * scale).roundToInt(), 1)

        val scaledStrip = printableStrip.scaleAntiAliased(targetWidth, targetHeight)
        val toneAdjustedStrip = if (brightnessMultiplier != 1.0f) {
            scaledStrip.applyToneCurve(brightnessMultiplier, brightnessCurve)
        } else {
            scaledStrip
        }
        return ditheringAlgorithm.applyAlgo(toneAdjustedStrip)
    }

    /**
     * Prepares a slot bitmap for fractional pole coverage.
     * When coverageFraction < 1.0, the source image is padded with white columns
     * so it occupies the requested fraction of the pole turn.
     * Phase offset is handled directly by shaderMatrix in unwrapToStrip.
     */
    fun prepareSlotBitmap(
        sourceBitmap: Bitmap,
        coverageFraction: Float,
        maxDimension: Int = 1000
    ): Bitmap {
        val clampedCoverage = coverageFraction.coerceIn(0.1f, 1.0f)
        if (clampedCoverage >= 0.999f) {
            return prepareSourceBitmap(sourceBitmap, maxDimension)
        }

        val maxImgW = (maxDimension * clampedCoverage).roundToInt().coerceAtLeast(10)
        val baseBitmap = prepareSourceBitmap(sourceBitmap, maxImgW)
        val slotW = max((baseBitmap.width / clampedCoverage).roundToInt(), baseBitmap.width + 1)
        val slotBitmap = createBitmap(slotW, baseBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(slotBitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(baseBitmap, 0f, 0f, null)
        return slotBitmap
    }

    /**
     * Generates a calibrated diameter measuring tape (pi-tape) up to maxDiameterMm.
     * Each 1mm of diameter corresponds to pi * 7.992 dots of tape length.
     * Wrapping this tape around a cylindrical pole allows direct reading of diameter.
     */
    fun generateDiameterTape(
        maxDiameterMm: Int = 80,
        printerWidthPx: Int = PrinterManager.MAX_PRINTER_WIDTH_PX
    ): Bitmap {
        val width = max(printerWidthPx, 1)
        val zeroY = PrinterManager.mmToPx(18f).toFloat()
        val totalHeight = zeroY + PrinterManager.mmToPx((PI * maxDiameterMm).toFloat()) + PrinterManager.mmToPx(8f)

        val bitmap = createBitmap(width, totalHeight.roundToInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        val boldLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 28f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }

        val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        // Header instructions
        canvas.drawText("DIAMETER TAPE (mm)", width / 2f, 35f, titlePaint)
        canvas.drawText("Cut/fold at 0 line. Wrap around pole.", width / 2f, 65f, subTextPaint)
        canvas.drawText("Read diameter where 0 overlaps tape.", width / 2f, 92f, subTextPaint)
        canvas.drawText("▼ ALIGN 0 LINE HERE ▼", width / 2f, 122f, titlePaint)

        // Baseline on left and right
        canvas.drawLine(0f, zeroY, 0f, totalHeight - 40f, boldLinePaint)
        canvas.drawLine(width - 1f, zeroY, width - 1f, totalHeight - 40f, boldLinePaint)

        // Zero reference line
        canvas.drawLine(0f, zeroY, width.toFloat(), zeroY, boldLinePaint)
        canvas.drawText("0 mm", 20f, zeroY - 8f, textPaint)

        // Ticks
        for (d in 1..maxDiameterMm) {
            val y = zeroY + PrinterManager.mmToPx((PI * d).toFloat())
            when {
                d % 10 == 0 -> {
                    // Major tick
                    canvas.drawLine(0f, y, 75f, y, boldLinePaint)
                    canvas.drawLine(width - 75f, y, width.toFloat(), y, boldLinePaint)
                    val label = "$d mm"
                    canvas.drawText(label, 85f, y + 10f, textPaint)
                }
                d % 5 == 0 -> {
                    // Medium tick
                    canvas.drawLine(0f, y, 50f, y, linePaint)
                    canvas.drawLine(width - 50f, y, width.toFloat(), y, linePaint)
                }
                else -> {
                    // Minor tick
                    canvas.drawLine(0f, y, 28f, y, linePaint)
                    canvas.drawLine(width - 28f, y, width.toFloat(), y, linePaint)
                }
            }
        }

        canvas.drawText("▲ MAX $maxDiameterMm mm ▲", width / 2f, totalHeight - 15f, titlePaint)

        return DitheringEngine.applyThreshold(bitmap)
    }

    /**
     * Normalizes/caps a source image to a sensible max dimension before processing
     * to prevent OutOfMemoryError while preserving maximum fidelity.
     */
    fun prepareSourceBitmap(bitmap: Bitmap, maxDimension: Int = 1000): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxDim = max(width, height)
        if (maxDim <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / maxDim.toFloat()
        val newW = max((width * scale).roundToInt(), 1)
        val newH = max((height * scale).roundToInt(), 1)
        return bitmap.scaleAntiAliased(newW, newH)
    }
}
