package com.arifr1234.receipttolenghi.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlin.math.max

object ImageUtils {

    /**
     * Scales an image with anti-aliasing.
     * Uses Area Averaging (Box Filtering) for large downscales (> 2.0x) to sample 100% of the pixels
     * and eliminate aliasing/moiré, and smooth bilinear interpolation for scales <= 2.0x and upscaling.
     */
    fun scaleAntiAliased(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val srcW = source.width
        val srcH = source.height
        val dstW = targetWidth.coerceAtLeast(1)
        val dstH = targetHeight.coerceAtLeast(1)

        val scaleX = srcW.toFloat() / dstW
        val scaleY = srcH.toFloat() / dstH

        // When downscaling by <= 2.0x in both dimensions (or upscaling), the 2.0px bilinear
        // kernel covers all source pixels without blind gaps and provides smoother sub-pixel blending.
        if (scaleX <= 2.0f && scaleY <= 2.0f) {
            return source.scale(dstW, dstH)
        }

        val srcPixels = IntArray(srcW * srcH)
        source.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)
        val dstPixels = IntArray(dstW * dstH)

        for (dy in 0 until dstH) {
            val sy0 = (dy * scaleY).toInt()
            val sy1 = max(((dy + 1) * scaleY).toInt(), sy0 + 1).coerceAtMost(srcH)
            val dstRowOffset = dy * dstW

            for (dx in 0 until dstW) {
                val sx0 = (dx * scaleX).toInt()
                val sx1 = max(((dx + 1) * scaleX).toInt(), sx0 + 1).coerceAtMost(srcW)

                var sumA = 0
                var sumR = 0
                var sumG = 0
                var sumB = 0
                var count = 0

                for (sy in sy0 until sy1) {
                    val srcRowOffset = sy * srcW
                    for (sx in sx0 until sx1) {
                        val p = srcPixels[srcRowOffset + sx]
                        sumA += (p ushr 24)
                        sumR += (p shr 16) and 0xFF
                        sumG += (p shr 8) and 0xFF
                        sumB += p and 0xFF
                        count++
                    }
                }

                if (count > 0) {
                    val avgA = sumA / count
                    val avgR = sumR / count
                    val avgG = sumG / count
                    val avgB = sumB / count
                    dstPixels[dstRowOffset + dx] = (avgA shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
                }
            }
        }

        val outBitmap = createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(dstPixels, 0, dstW, 0, 0, dstW, dstH)
        return outBitmap
    }

    /**
     * Loads a software-allocated Bitmap from an image Uri across Android versions.
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load image file", e)
        }
    }
}

/**
 * Convenient extension to scale a Bitmap with anti-aliasing.
 */
fun Bitmap.scaleAntiAliased(targetWidth: Int, targetHeight: Int): Bitmap =
    ImageUtils.scaleAntiAliased(this, targetWidth, targetHeight)
