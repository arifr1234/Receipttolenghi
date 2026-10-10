package com.arifr1234.receipttolenghi.ui.modes.general


import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import coil.compose.AsyncImage
import com.arifr1234.receipttolenghi.printer.DitheringEngine
import com.arifr1234.receipttolenghi.printer.PrinterManager
import com.arifr1234.receipttolenghi.utils.AppNotifier
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.EscPosPrinterCommands
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

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
    val imageRelativePath: String? = null,
    val align: PrintAlignment = PrintAlignment.CENTER,
    val ditheringAlgorithm: DitheringAlgorithm = DitheringAlgorithm.FLOYD_STEINBERG,
    val size: ImageSize = ImageSize.FULL
) : PrintableState

object ImagePrintableDefinition : PrintableDefinition<ImageState> {
    override val displayName = "Image"
    override val stateClass = ImageState::class

    override fun createDefaultState() = ImageState()

    fun getResolvedUri(context: Context, state: ImageState): Uri? {
        val path = state.imageRelativePath ?: return null
        return Uri.fromFile(File(PlaylistShareManager.getExternalFilesDir(context), path))
    }

    override fun getExternalFiles(state: ImageState): List<String> {
        val path = state.imageRelativePath ?: return emptyList()
        return listOf(path)
    }

    @Composable
    override fun EditorUI(state: ImageState, onStateChange: (ImageState) -> Unit) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

        fun saveBitmap(bitmap: Bitmap) {
            coroutineScope.launch(Dispatchers.IO) {
                AppNotifier.runWithGlobalLog("Failed to import image") {
                    val externalFilesDir = PlaylistShareManager.getExternalFilesDir(context)
                    val relativePath = "img_${UUID.randomUUID()}.png"
                    val file = File(externalFilesDir, relativePath)
                    
                    val scaledBitmap = scaleBitmapToSize(bitmap, PrinterManager.MAX_PRINTER_WIDTH_PX, ImageSize.ORIGINAL)
                    file.outputStream().use { out -> 
                        scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    
                    withContext(Dispatchers.Main) {
                        onStateChange(state.copy(imageRelativePath = relativePath))
                    }
                }
            }
        }

        val galleryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { saveBitmap(loadBitmapFromUri(context, it)) }
        }

        val cameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicturePreview()
        ) { bitmap: Bitmap? ->
            bitmap?.let { saveBitmap(it) }
        }

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val resolvedUri = getResolvedUri(context, state)
            
            var previewDithered by remember { mutableStateOf(false) }
            var ditheredImage by remember { mutableStateOf<Bitmap?>(null) }
            
            LaunchedEffect(resolvedUri, state.size, state.ditheringAlgorithm) {
                ditheredImage = null
                resolvedUri ?: return@LaunchedEffect
                
                withContext(Dispatchers.IO) {
                    AppNotifier.runWithGlobalLog("Failed to generate dithered preview") {
                        ditheredImage = generateDitheredBitmap(context, resolvedUri, state, PrinterManager.MAX_PRINTER_WIDTH_PX)
                    }
                }
            }

            if (resolvedUri != null) {
                AsyncImage(
                    model = (if (previewDithered) ditheredImage else null) ?: resolvedUri,
                    contentDescription = if (previewDithered) "Dithered Image Preview" else "Selected Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { showMenu = true }) {
                        Text("Select Image")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Gallery") },
                            onClick = {
                                showMenu = false
                                galleryLauncher.launch("image/*")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Camera") },
                            onClick = {
                                showMenu = false
                                cameraLauncher.launch(null)
                            }
                        )
                    }
                }
                if (resolvedUri != null) {
                    FilterChip(
                        selected = previewDithered,
                        onClick = { previewDithered = !previewDithered },
                        label = { Text("Preview") }
                    )
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
        val uri = getResolvedUri(context, state) ?: run {
            printer.printFormattedText("[${state.align}]No Image Selected\n", 0f)
            return
        }

        val ditheredBitmap = generateDitheredBitmap(context, uri, state, printer.printerWidthPx)

        // Bypass EscPosPrinterSize limit of 256 height by calling the command directly
        val bytes = EscPosPrinterCommands.bitmapToBytes(ditheredBitmap, false)
        val hexString = PrinterTextParserImg.bytesToHexadecimalString(bytes)
        
        printer.printFormattedText("[${state.align.tag}]<img>$hexString</img>\n", 0f)
    }

    private fun generateDitheredBitmap(context: Context, uri: Uri, state: ImageState, maxWidth: Int): Bitmap {
        val originalBitmap = loadBitmapFromUri(context, uri)
        val scaledBitmap = scaleBitmapToSize(originalBitmap, maxWidth, state.size)
        return state.ditheringAlgorithm.applyAlgo(scaledBitmap)
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
