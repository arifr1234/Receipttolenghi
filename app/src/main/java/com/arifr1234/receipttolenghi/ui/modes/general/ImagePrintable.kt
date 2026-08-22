package com.arifr1234.receipttolenghi.ui.modes.general

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arifr1234.receipttolenghi.printer.DitheringEngine
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.EscPosPrinterCommands
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import java.util.UUID
import androidx.core.net.toUri
import androidx.core.graphics.scale

class ImageState(
    imageUri: String? = null,
    align: String = "C",
    ditheringAlgorithm: String = "Floyd-Steinberg",
    size: String = "Full"
) : PrintableState {
    override val id: String = UUID.randomUUID().toString()
    var imageUri by mutableStateOf(imageUri)
    var align by mutableStateOf(align)
    var ditheringAlgorithm by mutableStateOf(ditheringAlgorithm)
    var size by mutableStateOf(size)
}

object ImagePrintableDefinition : PrintableDefinition<ImageState> {
    override val name = "Image"
    override val stateClass = ImageState::class

    override fun createDefaultState() = ImageState()

    @Composable
    override fun EditorUI(state: ImageState) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { state.imageUri = it.toString() }
        }

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.imageUri != null) {
                AsyncImage(
                    model = state.imageUri,
                    contentDescription = "Selected Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clickable { launcher.launch("image/*") },
                    contentScale = ContentScale.Fit
                )
            } else {
                Button(onClick = { launcher.launch("image/*") }) {
                    Text("Select Image")
                }
            }
        }
    }

    @Composable
    override fun ConfigUI(state: ImageState) {
        Column {
            Text("Alignment", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.align == "L", onClick = { state.align = "L" }, label = { Text("Left") })
                FilterChip(selected = state.align == "C", onClick = { state.align = "C" }, label = { Text("Center") })
                FilterChip(selected = state.align == "R", onClick = { state.align = "R" }, label = { Text("Right") })
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Dithering Algorithm", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    FilterChip(
                        selected = state.ditheringAlgorithm == "Threshold",
                        onClick = { state.ditheringAlgorithm = "Threshold" },
                        label = { Text("Threshold") })
                    FilterChip(
                        selected = state.ditheringAlgorithm == "Bayer",
                        onClick = { state.ditheringAlgorithm = "Bayer" },
                        label = { Text("Bayer") })
                    FilterChip(
                        selected = state.ditheringAlgorithm == "Atkinson",
                        onClick = { state.ditheringAlgorithm = "Atkinson" },
                        label = { Text("Atkinson") })
                    FilterChip(
                        selected = state.ditheringAlgorithm == "Floyd-Steinberg",
                        onClick = { state.ditheringAlgorithm = "Floyd-Steinberg" },
                        label = { Text("Floyd-Steinberg") })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Spacer(modifier = Modifier.height(8.dp))
            Text("Size", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.size == "Original", onClick = { state.size = "Original" }, label = { Text("Original") })
                FilterChip(selected = state.size == "Half", onClick = { state.size = "Half" }, label = { Text("Half") })
                FilterChip(selected = state.size == "Full", onClick = { state.size = "Full" }, label = { Text("Full") })
            }
        }
    }

    override fun print(context: Context, state: ImageState, printer: EscPosPrinter) {
        val uriStr = state.imageUri ?: run {
            printer.printFormattedText("[${state.align}]No Image Selected\n", 0f)
            return
        }

        val uri = uriStr.toUri()
        val originalBitmap = loadBitmapFromUri(context, uri)
        
        val maxWidth = printer.printerWidthPx
        val scaledBitmap = scaleBitmapToSize(originalBitmap, maxWidth, state.size)
        val ditheredBitmap = when (state.ditheringAlgorithm) {
            "Threshold" -> DitheringEngine.applyThreshold(scaledBitmap)
            "Bayer" -> DitheringEngine.applyBayer(scaledBitmap)
            "Atkinson" -> DitheringEngine.applyAtkinson(scaledBitmap)
            "Floyd-Steinberg" -> DitheringEngine.applyFloydSteinberg(scaledBitmap)
            else -> DitheringEngine.applyFloydSteinberg(scaledBitmap)
        }

        // Bypass EscPosPrinterSize limit of 256 height by calling the command directly
        val bytes = EscPosPrinterCommands.bitmapToBytes(ditheredBitmap, false)
        val hexString = PrinterTextParserImg.bytesToHexadecimalString(bytes)
        
        printer.printFormattedText("[${state.align}]<img>$hexString</img>\n", 0f)
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load image file", e)
        }
    }

    private fun scaleBitmapToSize(bitmap: Bitmap, maxWidth: Int, sizeMode: String): Bitmap {
        val targetWidth = when (sizeMode) {
            "Full" -> maxWidth
            "Half" -> maxWidth / 2
            "Original" -> bitmap.width.coerceAtMost(maxWidth)
            else -> maxWidth
        }

        val targetHeight = (bitmap.height.toFloat() * (targetWidth.toFloat() / bitmap.width.toFloat())).toInt()

        val finalWidth = targetWidth.coerceAtLeast(1)
        val finalHeight = targetHeight.coerceAtLeast(1)

        return bitmap.scale(finalWidth, finalHeight)
    }
}
