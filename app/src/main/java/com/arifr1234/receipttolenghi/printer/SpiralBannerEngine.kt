package com.arifr1234.receipttolenghi.printer

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.createBitmap
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
     */
    fun unwrapToStrip(sourceBitmap: Bitmap, geometry: SpiralGeometry): Bitmap {
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
     * downscales it to printer resolution, and applies dithering.
     */
    fun processForPrinter(
        unwrappedStrip: Bitmap,
        geometry: SpiralGeometry,
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
        return DitheringEngine.applyFloydSteinberg(scaledStrip)
    }

    /**
     * Normalizes/caps a source image to a sensible max dimension before processing
     * to prevent OutOfMemoryError while preserving maximum fidelity.
     */
    fun prepareSourceBitmap(bitmap: Bitmap, maxDimension: Int = 1800): Bitmap {
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
