package com.arifr1234.receipttolenghi.printer

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap

object DitheringEngine {

    private fun getLuminance(pixel: Int): Int {
        // Rec. 709 Human eye weighted luminance (modern sRGB)
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return (0.2126 * r + 0.7152 * g + 0.0722 * b).toInt()
    }

    fun applyThreshold(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val lum = getLuminance(pixels[i])
            val newColor = if (lum < 128) Color.BLACK else Color.WHITE
            pixels[i] = newColor
        }

        val outBitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return outBitmap
    }

    // Standard Bayer 4x4 matrix (0-15) mapped to 15-240 threshold space for performance
    private val bayerThresholdMatrix = arrayOf(
        intArrayOf(0, 8, 2, 10),
        intArrayOf(12, 4, 14, 6),
        intArrayOf(3, 11, 1, 9),
        intArrayOf(15, 7, 13, 5)
    ).map { row ->
        row.map { value -> (value * 15) + 15 }.toIntArray()
    }.toTypedArray()

    fun applyBayer(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val lum = getLuminance(pixels[y * width + x])
                val threshold = bayerThresholdMatrix[y % 4][x % 4]
                val newColor = if (lum < threshold) Color.BLACK else Color.WHITE
                pixels[y * width + x] = newColor
            }
        }

        val outBitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return outBitmap
    }

    fun applyFloydSteinberg(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Extract luminance to a mutable Float array to store errors
        val lumArray = FloatArray(width * height)
        for (i in pixels.indices) {
            lumArray[i] = getLuminance(pixels[i]).toFloat()
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val oldPixel = lumArray[idx]
                val newPixel = if (oldPixel < 128f) 0f else 255f
                pixels[idx] = if (newPixel == 0f) Color.BLACK else Color.WHITE
                
                val quantError = oldPixel - newPixel

                if (x + 1 < width) lumArray[y * width + x + 1] += quantError * 7 / 16
                if (y + 1 < height) {
                    if (x - 1 >= 0) lumArray[(y + 1) * width + x - 1] += quantError * 3 / 16
                    lumArray[(y + 1) * width + x] += quantError * 5 / 16
                    if (x + 1 < width) lumArray[(y + 1) * width + x + 1] += quantError * 1 / 16
                }
            }
        }

        val outBitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return outBitmap
    }

    fun applyAtkinson(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val lumArray = FloatArray(width * height)
        for (i in pixels.indices) {
            lumArray[i] = getLuminance(pixels[i]).toFloat()
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val oldPixel = lumArray[idx]
                val newPixel = if (oldPixel < 128f) 0f else 255f
                pixels[idx] = if (newPixel == 0f) Color.BLACK else Color.WHITE
                
                val quantError = oldPixel - newPixel
                val portion = quantError / 8f

                if (x + 1 < width) lumArray[y * width + x + 1] += portion
                if (x + 2 < width) lumArray[y * width + x + 2] += portion
                
                if (y + 1 < height) {
                    if (x - 1 >= 0) lumArray[(y + 1) * width + x - 1] += portion
                    lumArray[(y + 1) * width + x] += portion
                    if (x + 1 < width) lumArray[(y + 1) * width + x + 1] += portion
                }
                
                if (y + 2 < height) {
                    lumArray[(y + 2) * width + x] += portion
                }
            }
        }

        val outBitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return outBitmap
    }
}
