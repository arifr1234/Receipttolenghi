package com.arifr1234.receipttolenghi.printer

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.arifr1234.receipttolenghi.utils.scaleAntiAliased
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class SpiralGeometry(
    val originalWidth: Int,
    val originalHeight: Int,
    val paperWidthMm: Float,
    val poleDiameterMm: Float,
    val perimeterMm: Float,
    val paperWidthPx: Float,
    val a: Float,
    val b: Float,
    val c: Float,
    val stripHeightPx: Int,
    val sinTheta: Float,
    val cosTheta: Float
)

object SpiralBannerEngine {

    const val MIN_POLE_DIAMETER_RATIO = 1.05f

    fun getMinimumPoleDiameterMm(paperWidthMm: Float = PrinterManager.PRINTER_WIDTH_MM): Float {
        return ((paperWidthMm / PI.toFloat()) * MIN_POLE_DIAMETER_RATIO)
    }

    fun calculateGeometry(
        originalWidth: Int,
        originalHeight: Int,
        poleDiameterMm: Float,
        paperWidthMm: Float = PrinterManager.PRINTER_WIDTH_MM
    ): SpiralGeometry {
        val minDiameter = getMinimumPoleDiameterMm(paperWidthMm)
        val safeDiameter = max(poleDiameterMm, minDiameter)
        val perimeterMm = (PI * safeDiameter).toFloat()

        val paperWidthPx = paperWidthMm * (originalWidth.toFloat() / perimeterMm)
        val clampedPaperWidthPx = paperWidthPx.coerceAtMost(originalWidth.toFloat() * 0.999f)

        val wOrigSq = originalWidth.toFloat().pow(2)
        val wPaperSq = clampedPaperWidthPx.pow(2)
        val a = sqrt(wOrigSq - wPaperSq)
        val b = (clampedPaperWidthPx * originalWidth.toFloat()) / a
        val c = wPaperSq / a
        val stripHeightPx = (a + (a + c) * (originalHeight.toFloat() / b)).roundToInt()

        val sinTheta = clampedPaperWidthPx / originalWidth.toFloat()
        val cosTheta = a / originalWidth.toFloat()

        return SpiralGeometry(
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            paperWidthMm = paperWidthMm,
            poleDiameterMm = safeDiameter,
            perimeterMm = perimeterMm,
            paperWidthPx = clampedPaperWidthPx,
            a = a,
            b = b,
            c = c,
            stripHeightPx = max(stripHeightPx, 1),
            sinTheta = sinTheta,
            cosTheta = cosTheta
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

        // Matrix mapping Canvas coordinates (Xp, Yp) on the strip to Bitmap coordinates (U, V):
        // U = Yp * cosTheta - Xp * sinTheta + Wp * sinTheta
        // V = Yp * sinTheta + Xp * cosTheta - a * sinTheta
        val canvasToBitmap = Matrix()
        val vals = FloatArray(9)
        vals[Matrix.MSCALE_X] = -geometry.sinTheta
        vals[Matrix.MSKEW_X] = geometry.cosTheta
        vals[Matrix.MTRANS_X] = geometry.paperWidthPx * geometry.sinTheta

        vals[Matrix.MSKEW_Y] = geometry.cosTheta
        vals[Matrix.MSCALE_Y] = geometry.sinTheta
        vals[Matrix.MTRANS_Y] = -geometry.a * geometry.sinTheta

        vals[Matrix.MPERSP_0] = 0f
        vals[Matrix.MPERSP_1] = 0f
        vals[Matrix.MPERSP_2] = 1f
        canvasToBitmap.setValues(vals)

        // In Skia/Android, shader.setLocalMatrix maps Shader space -> Canvas space,
        // so we invert canvasToBitmap to get localMatrix:
        val shaderMatrix = Matrix()
        if (canvasToBitmap.invert(shaderMatrix)) {
            shader.setLocalMatrix(shaderMatrix)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.shader = shader
        }

        val bannerPath = android.graphics.Path().apply {
            moveTo(stripWidth.toFloat(), 0f)
            lineTo(0f, geometry.a)
            lineTo(0f, stripHeight.toFloat())
            lineTo(stripWidth.toFloat(), stripHeight.toFloat() - geometry.a)
            close()
        }

        canvas.drawPath(bannerPath, paint)
        return outBitmap
    }

    /**
     * Stage 2: Downscales the high-resolution unwrapped strip to printer resolution
     * and applies Floyd-Steinberg dithering.
     */
    fun processForPrinter(
        unwrappedStrip: Bitmap,
        printerWidthPx: Int = PrinterManager.MAX_PRINTER_WIDTH_PX
    ): Bitmap {
        val targetWidth = max(printerWidthPx, 1)
        val scale = targetWidth.toFloat() / unwrappedStrip.width.toFloat()
        val targetHeight = max((unwrappedStrip.height.toFloat() * scale).roundToInt(), 1)

        val scaledStrip = unwrappedStrip.scaleAntiAliased(targetWidth, targetHeight)
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
