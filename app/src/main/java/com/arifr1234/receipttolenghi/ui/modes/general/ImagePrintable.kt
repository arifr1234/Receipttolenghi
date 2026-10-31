package com.arifr1234.receipttolenghi.ui.modes.general


import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arifr1234.receipttolenghi.printer.DitheringAlgorithm
import com.arifr1234.receipttolenghi.printer.PrinterManager
import com.arifr1234.receipttolenghi.ui.components.BrightnessCompensationSelector
import com.arifr1234.receipttolenghi.ui.components.DitheringAlgorithmSelector
import com.arifr1234.receipttolenghi.utils.AppNotifier
import com.arifr1234.receipttolenghi.utils.BrightnessCurve
import com.arifr1234.receipttolenghi.utils.ImageUtils
import com.arifr1234.receipttolenghi.utils.scaleAntiAliased
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

data class ImageState(
    val imageRelativePath: String? = null,
    val align: PrintAlignment = PrintAlignment.CENTER,
    val ditheringAlgorithm: DitheringAlgorithm = DitheringAlgorithm.FLOYD_STEINBERG,
    val size: ImageSize = ImageSize.FULL,
    val brightnessMultiplier: Float = 2.0f,
    val brightnessCurve: BrightnessCurve = BrightnessCurve.CURVE,
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
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            uri?.let { saveBitmap(loadBitmapFromUri(context, it)) }
        }

        val cameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicturePreview(),
        ) { bitmap: Bitmap? ->
            bitmap?.let { saveBitmap(it) }
        }

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val resolvedUri = getResolvedUri(context, state)
            
            var previewDithered by remember { mutableStateOf(value = false) }
            var originalImage by remember { mutableStateOf<Bitmap?>(null) }
            var ditheredImage by remember { mutableStateOf<Bitmap?>(null) }
            var currentPainter by remember { mutableStateOf<Painter?>(null) }

            LaunchedEffect(resolvedUri) {
                currentPainter = null
                if (resolvedUri == null) {
                    originalImage = null
                    return@LaunchedEffect
                }
                withContext(Dispatchers.IO) {
                    AppNotifier.runWithGlobalLog("Failed to load original image") {
                        originalImage = loadBitmapFromUri(context, resolvedUri)
                    }
                }
            }

            LaunchedEffect(
                resolvedUri,
                state.size,
                state.ditheringAlgorithm,
                state.brightnessMultiplier,
                state.brightnessCurve,
            ) {
                if (resolvedUri == null) {
                    ditheredImage = null
                    return@LaunchedEffect
                }
                
                withContext(Dispatchers.IO) {
                    AppNotifier.runWithGlobalLog("Failed to generate dithered preview") {
                        ditheredImage = generateDitheredBitmap(context, resolvedUri, state, PrinterManager.MAX_PRINTER_WIDTH_PX)
                    }
                }
            }

            val maxImageHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.5f).toDp() }

            if (resolvedUri != null) {
                val displayBitmap = if (previewDithered) (ditheredImage ?: originalImage) else (originalImage ?: ditheredImage)
                AsyncImage(
                    model = displayBitmap ?: resolvedUri,
                    contentDescription = if (previewDithered) "Dithered Image Preview" else "Selected Image",
                    placeholder = currentPainter,
                    onSuccess = { (painter) -> currentPainter = painter },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxImageHeight),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var showMenu by remember { mutableStateOf(value = false) }
                Box {
                    Button(onClick = { showMenu = true }) {
                        Text("Select Image")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Gallery") },
                            onClick = {
                                showMenu = false
                                galleryLauncher.launch("image/*")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Camera") },
                            onClick = {
                                showMenu = false
                                cameraLauncher.launch(null)
                            },
                        )
                    }
                }
                if (resolvedUri != null) {
                    FilterChip(
                        selected = previewDithered,
                        onClick = { previewDithered = !previewDithered },
                        label = { Text("Preview") },
                    )
                }
            }
        }
    }

    @Composable
    override fun ConfigUI(state: ImageState, onStateChange: (ImageState) -> Unit) {
        Column {
            DitheringAlgorithmSelector(
                selectedAlgorithm = state.ditheringAlgorithm,
                onAlgorithmSelected = { onStateChange(state.copy(ditheringAlgorithm = it)) },
            )

            Spacer(modifier = Modifier.height(8.dp))

            BrightnessCompensationSelector(
                brightnessMultiplier = state.brightnessMultiplier,
                onMultiplierChange = { onStateChange(state.copy(brightnessMultiplier = it)) },
                brightnessCurve = state.brightnessCurve,
                onCurveChange = { onStateChange(state.copy(brightnessCurve = it)) },
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Size", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageSize.entries.forEach { imageSize ->
                    FilterChip(
                        selected = state.size == imageSize,
                        onClick = { onStateChange(state.copy(size = imageSize)) },
                        label = { Text(imageSize.label) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Alignment", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrintAlignment.entries.forEach { alignment ->
                    FilterChip(
                        selected = state.align == alignment,
                        onClick = { onStateChange(state.copy(align = alignment)) },
                        label = { Text(alignment.label) },
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
        val toneAdjustedBitmap = ImageUtils.applyToneCurve(scaledBitmap, state.brightnessMultiplier, state.brightnessCurve)
        return state.ditheringAlgorithm.applyAlgo(toneAdjustedBitmap)
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap {
        return ImageUtils.loadBitmapFromUri(context, uri)
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

        return bitmap.scaleAntiAliased(finalWidth, finalHeight)
    }
}
