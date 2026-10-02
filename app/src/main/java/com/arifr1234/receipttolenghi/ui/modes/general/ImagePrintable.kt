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
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
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
import com.arifr1234.receipttolenghi.utils.AppNotifier

enum class ImageSize(val label: String) {
    ORIGINAL("Original"),
    HALF("Half"),
    FULL("Full")
}

enum class DitheringAlgorithm(val label: String, val applyAlgo: (Bitmap) -> Bitmap) {
    THRESHOLD("Threshold", DitheringEngine::applyThreshold),
    BAYER("Bayer", DitheringEngine::applyBayer),
    ATKINSON("Atkinson", DitheringEngine::applyAtkinson),
    FLOYD_STEINBERG("Floyd-Steinberg", DitheringEngine::applyFloydSteinberg)
}

data class ImageState(
    val imageUri: String? = null,
    val align: PrintAlignment = PrintAlignment.CENTER,
    val ditheringAlgorithm: DitheringAlgorithm = DitheringAlgorithm.FLOYD_STEINBERG,
    val size: ImageSize = ImageSize.FULL
) : PrintableState

object ImagePrintableDefinition : PrintableDefinition<ImageState> {
    override val displayName = "Image"
    override val stateClass = ImageState::class

    override fun createDefaultState() = ImageState()

    @Composable
    override fun EditorUI(state: ImageState, onStateChange: (ImageState) -> Unit) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { contentUri ->
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    AppNotifier.runWithGlobalLog("Failed to import image") {
                        val inputStream = context.contentResolver.openInputStream(contentUri)
                            ?: throw IllegalStateException("Could not open image file")
                        
                        val file = java.io.File(context.filesDir, "img_${UUID.randomUUID()}.png")
                        file.outputStream().use { out -> inputStream.use { it.copyTo(out) } }
                        val localUri = Uri.fromFile(file).toString()
                        
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onStateChange(state.copy(imageUri = localUri))
                        }
                    }
                }
            }
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
    override fun ConfigUI(state: ImageState, onStateChange: (ImageState) -> Unit) {
        Column {
            Text("Alignment", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrintAlignment.entries.forEach { alignment ->
                    FilterChip(
                        selected = state.align == alignment,
                        onClick = { onStateChange(state.copy(align = alignment)) },
                        label = { Text(alignment.label) }
                    )
                }
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
                    DitheringAlgorithm.entries.forEach { algo ->
                        FilterChip(
                            selected = state.ditheringAlgorithm == algo,
                            onClick = { onStateChange(state.copy(ditheringAlgorithm = algo)) },
                            label = { Text(algo.label) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Spacer(modifier = Modifier.height(8.dp))
            Text("Size", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageSize.entries.forEach { imageSize ->
                    FilterChip(
                        selected = state.size == imageSize,
                        onClick = { onStateChange(state.copy(size = imageSize)) },
                        label = { Text(imageSize.label) }
                    )
                }
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
        val ditheredBitmap = state.ditheringAlgorithm.applyAlgo(scaledBitmap)

        // Bypass EscPosPrinterSize limit of 256 height by calling the command directly
        val bytes = EscPosPrinterCommands.bitmapToBytes(ditheredBitmap, false)
        val hexString = PrinterTextParserImg.bytesToHexadecimalString(bytes)
        
        printer.printFormattedText("[${state.align.tag}]<img>$hexString</img>\n", 0f)
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

    private fun scaleBitmapToSize(bitmap: Bitmap, maxWidth: Int, sizeMode: ImageSize): Bitmap {
        val targetWidth = when (sizeMode) {
            ImageSize.FULL -> maxWidth
            ImageSize.HALF -> maxWidth / 2
            ImageSize.ORIGINAL -> bitmap.width.coerceAtMost(maxWidth)
        }

        val targetHeight = (bitmap.height.toFloat() * (targetWidth.toFloat() / bitmap.width.toFloat())).toInt()

        val finalWidth = targetWidth.coerceAtLeast(1)
        val finalHeight = targetHeight.coerceAtLeast(1)

        return bitmap.scale(finalWidth, finalHeight)
    }
}
