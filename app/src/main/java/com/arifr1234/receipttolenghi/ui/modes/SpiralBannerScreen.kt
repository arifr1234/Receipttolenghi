package com.arifr1234.receipttolenghi.ui.modes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arifr1234.receipttolenghi.printer.DitheringAlgorithm
import com.arifr1234.receipttolenghi.printer.PrinterManager
import com.arifr1234.receipttolenghi.printer.SpiralBannerEngine
import com.arifr1234.receipttolenghi.printer.SpiralGeometry
import com.arifr1234.receipttolenghi.ui.components.BrightnessCompensationSelector
import com.arifr1234.receipttolenghi.ui.components.DitheringAlgorithmSelector
import com.arifr1234.receipttolenghi.utils.BrightnessCurve
import com.arifr1234.receipttolenghi.utils.ImageUtils
import com.dantsu.escposprinter.EscPosPrinterCommands
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

enum class BannerConfigCategory(val label: String) {
    BASIC("Basic"),
    QUALITY("Quality"),
    MODIFY("Modify")
}

private const val CYLINDER_AGSL_SRC = """
uniform shader uStrip;
uniform float2 uResolution;
uniform float2 uCylinderSize;
uniform float uRotation;
uniform float2 uStripSize;
uniform float2 uTurns;
uniform float uPrintableRatio;

const float PI = 3.14159265359;

bool raycastCylinder(
    float2 fragCoord,
    out float2 cylinderUv,
    out float surfaceLighting
) {
    float cylinderRadius = uCylinderSize.x * 0.5;
    float relativeScreenX = fragCoord.x - (uResolution.x * 0.5);

    if (abs(relativeScreenX) > cylinderRadius) {
        return false;
    }

    float cylinderHalfHeight = uCylinderSize.y * 0.5;
    float relativeScreenY = fragCoord.y - (uResolution.y * 0.5);

    if (abs(relativeScreenY) > cylinderHalfHeight) {
        return false;
    }

    float surfaceNormalX = clamp(relativeScreenX / cylinderRadius, -1.0, 1.0);
    float surfaceNormalZ = sqrt(max(0.0, 1.0 - surfaceNormalX * surfaceNormalX));
    surfaceLighting = 0.35 + 0.65 * surfaceNormalZ;

    float circumferentialAngle = mod(asin(surfaceNormalX) - uRotation, 2.0 * PI);
    if (circumferentialAngle < 0.0) {
        circumferentialAngle += 2.0 * PI;
    }

    // cylinderUv: [0.0, 1.0) x [0.0, 1.0] - Normalized cylinder surface UV
    cylinderUv = float2(
        circumferentialAngle / (2.0 * PI),
        (relativeScreenY + cylinderHalfHeight) / uCylinderSize.y
    );
    return true;
}

float2 mapCylinderToNormalizedStrip(float2 cylinderUv) {
    // unwrappedSpiralCoord: [0.0, +inf) x [0.0, +inf) - Coordinates in spiral units:
    // .x = width across paper strip in paper widths (integer = turn index k, fraction = width across strip)
    // .y = length along paper strip in turn units
    float2 unwrappedSpiralCoord = float2(
        cylinderUv.x + uTurns.x * cylinderUv.y,
        1.0 - cylinderUv.x + uTurns.y * cylinderUv.y
    );

    // wrappedStripCoord: [0.0, 1.0) x [0.0, totalTurns] - Position on physical strip (width [0..1), length in turns)
    float helixTurnIndex = floor(unwrappedSpiralCoord.x);
    float2 wrappedStripCoord = float2(mod(unwrappedSpiralCoord.x, 1.0), unwrappedSpiralCoord.y + helixTurnIndex);

    float totalTurns = 1.0 + uTurns.x + uTurns.y;
    // normalizedStripUv: [0.0, 1.0) x [0.0, 1.0] - Normalized UV across the paper strip (width [0..1), length [0..1])
    float2 normalizedStripUv = float2(wrappedStripCoord.x, wrappedStripCoord.y / totalTurns);
    return normalizedStripUv;
}

half4 main(float2 fragCoord) {
    float2 cylinderUv;
    float surfaceLighting;

    if (!raycastCylinder(fragCoord, cylinderUv, surfaceLighting)) {
        return half4(0.0);
    }

    // normalizedStripUv: [0.0, 1.0) x [0.0, 1.0] - Normalized UV on flat physical paper strip (width [0..1), length [0..1])
    float2 normalizedStripUv = mapCylinderToNormalizedStrip(cylinderUv);

    // Physical paper margins where the thermal print head cannot reach
    float margin = (1.0 - uPrintableRatio) * 0.5;
    if (normalizedStripUv.x < margin || normalizedStripUv.x > (1.0 - margin)) {
        return half4(half3(surfaceLighting), 1.0);
    }

    // Inside printable area: map horizontally from [margin, 1.0 - margin] to [0.0, 1.0]
    float printableU = (normalizedStripUv.x - margin) / uPrintableRatio;
    float2 stripPixelCoord = float2(printableU, normalizedStripUv.y) * uStripSize;
    float2 clampedStripCoord = clamp(stripPixelCoord, float2(0.0), uStripSize - 1.0);

    half4 ditherColor = uStrip.eval(clampedStripCoord);
    return half4(ditherColor.rgb * half(surfaceLighting), 1.0);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class CylinderShaderRenderer {
    private val shader = RuntimeShader(CYLINDER_AGSL_SRC)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun render(
        canvas: Canvas,
        ditheredBitmap: Bitmap,
        geometry: SpiralGeometry,
        canvasWidth: Float,
        canvasHeight: Float,
        cylWidth: Float,
        cylHeight: Float,
        rotation: Float
    ) {
        val bitmapShader = BitmapShader(ditheredBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setInputShader("uStrip", bitmapShader)
        shader.setFloatUniform("uResolution", canvasWidth, canvasHeight)
        shader.setFloatUniform("uCylinderSize", cylWidth, cylHeight)
        shader.setFloatUniform("uRotation", rotation)
        shader.setFloatUniform("uStripSize", ditheredBitmap.width.toFloat(), ditheredBitmap.height.toFloat())
        shader.setFloatUniform("uTurns", geometry.turnsX, geometry.turnsY)
        shader.setFloatUniform("uPrintableRatio", geometry.printableRatio)

        paint.shader = shader
        canvas.drawRect(0f, 0f, canvasWidth, canvasHeight, paint)
    }
}

private data class SpiralBannerData(
    val strip: Bitmap,
    val geometry: SpiralGeometry,
    val phase: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpiralBannerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var poleDiameterMm by remember { mutableFloatStateOf(35f) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    // Configuration states
    var selectedCategory by remember { mutableStateOf(BannerConfigCategory.BASIC) }
    var ditheringAlgorithm by remember { mutableStateOf(DitheringAlgorithm.FLOYD_STEINBERG) }
    var brightnessMultiplier by remember { mutableFloatStateOf(2.0f) }
    var brightnessCurve by remember { mutableStateOf(BrightnessCurve.CURVE) }
    var poleCoverageFraction by remember { mutableFloatStateOf(1.0f) }
    var imagePhase by remember { mutableFloatStateOf(0f) }
    var instances by remember { mutableIntStateOf(1) }

    val minDiameter = remember {
        ceil(SpiralBannerEngine.getMinimumPoleDiameterMm()).coerceAtLeast(17f)
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val loadedBitmap = ImageUtils.loadBitmapFromUri(context, uri)
            sourceBitmap = SpiralBannerEngine.prepareSourceBitmap(loadedBitmap)
        }
    }

    // Processing pipeline states
    var tileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var unwrappedData by remember { mutableStateOf<SpiralBannerData?>(null) }
    var ditheredData by remember { mutableStateOf<SpiralBannerData?>(null) }
    var isUnwrapping by remember { mutableStateOf(false) }
    var isDithering by remember { mutableStateOf(false) }
    val isProcessing = isUnwrapping || isDithering

    // Stage 1: Tile Bitmap (only re-renders when sourceBitmap or poleCoverageFraction changes)
    LaunchedEffect(sourceBitmap, poleCoverageFraction) {
        val bmp = sourceBitmap ?: run {
            tileBitmap = null
            ditheredData = null
            return@LaunchedEffect
        }
        if (tileBitmap != null) {
            delay(80.milliseconds)
        }
        withContext(Dispatchers.Default) {
            tileBitmap = SpiralBannerEngine.prepareTileBitmap(bmp, poleCoverageFraction)
        }
    }

    // Stage 2: Helical Geometry (instant math, only re-evaluates when tile dimensions, instances, or poleDiameterMm change)
    val geometry = remember(tileBitmap, instances, poleDiameterMm) {
        val tile = tileBitmap ?: return@remember null
        SpiralBannerEngine.calculateGeometry(
            originalWidth = tile.width * instances,
            originalHeight = tile.height,
            poleDiameterMm = poleDiameterMm
        )
    }

    // Stage 3: Unwrapped Strip (only re-runs when tileBitmap, geometry, imagePhase, or instances change)
    LaunchedEffect(tileBitmap, geometry, imagePhase, instances) {
        val tile = tileBitmap
        val geo = geometry
        if (tile == null || geo == null) {
            unwrappedData = null
            isUnwrapping = false
            return@LaunchedEffect
        }
        isUnwrapping = true
        if (unwrappedData != null) {
            delay(50.milliseconds)
        }
        val currentPhase = imagePhase
        withContext(Dispatchers.Default) {
            val unwrapped = SpiralBannerEngine.unwrapToStrip(tile, geo, phase = currentPhase * instances)
            unwrappedData = SpiralBannerData(
                strip = unwrapped,
                geometry = geo,
                phase = currentPhase
            )
            isUnwrapping = false
        }
    }

    // Stage 4: Dithered Strip & Preview Pairing (only post-processes when unwrappedData, brightness, or dithering changes)
    LaunchedEffect(
        unwrappedData,
        brightnessMultiplier,
        brightnessCurve,
        ditheringAlgorithm
    ) {
        val currentUnwrapped = unwrappedData ?: run {
            if (sourceBitmap == null) {
                ditheredData = null
            }
            isDithering = false
            return@LaunchedEffect
        }
        isDithering = true
        if (ditheredData != null) {
            delay(50.milliseconds)
        }
        withContext(Dispatchers.Default) {
            val dithered = SpiralBannerEngine.processForPrinter(
                unwrappedStrip = currentUnwrapped.strip,
                geometry = currentUnwrapped.geometry,
                brightnessMultiplier = brightnessMultiplier,
                brightnessCurve = brightnessCurve,
                ditheringAlgorithm = ditheringAlgorithm
            )
            ditheredData = SpiralBannerData(
                strip = dithered,
                geometry = currentUnwrapped.geometry,
                phase = currentUnwrapped.phase
            )
            isDithering = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Spiral Banner") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val strip = ditheredData?.strip ?: return@IconButton
                            PrinterManager.printAsync { printer ->
                                val bytes = EscPosPrinterCommands.bitmapToBytes(strip, false)
                                val hexString = PrinterTextParserImg.bytesToHexadecimalString(bytes)
                                printer.printFormattedText("[L]<img>$hexString</img>\n", 0f)
                            }
                        },
                        enabled = ditheredData != null && !isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print Spiral Banner"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (sourceBitmap == null) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Create a Spiral Banner",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Transform an image into a continuous helical strip of thermal paper. When wound edge-to-edge around a cylinder, the original image is revealed!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") }
                        ) {
                            Text("Select Image")
                        }
                        PrintDiameterTapeButton()
                    }
                }
            } else {
                // 3D Rotatable Cylinder Preview Card
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val currentDithered = ditheredData
                        if (currentDithered != null) {
                            val currentStrip = currentDithered.strip
                            val currentGeometry = currentDithered.geometry
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val shaderRenderer = remember { CylinderShaderRenderer() }
                                androidx.compose.foundation.Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectHorizontalDragGestures { _, dragAmount ->
                                                rotationAngle += dragAmount * 0.012f
                                            }
                                        }
                                ) {
                                    val maxAllowedWidth = size.width * 0.9f
                                    val idealWidth = size.height * currentGeometry.cylinderAspectRatio

                                    val (cylW, cylH) = if (idealWidth > maxAllowedWidth) {
                                        // When the pole is wider than the viewing canvas, shrink its height
                                        // so that the true aspect ratio is preserved without squishing the image.
                                        val w = maxAllowedWidth
                                        val h = kotlin.math.max(w / currentGeometry.cylinderAspectRatio, 1f)
                                        Pair(w, h)
                                    } else {
                                        // Normal case: pole spans the full canvas height
                                        Pair(idealWidth, size.height)
                                    }

                                    drawIntoCanvas { canvas ->
                                        val effectiveRotation = rotationAngle - (currentDithered.phase * (2.0f * PI.toFloat()))
                                        shaderRenderer.render(
                                            canvas = canvas.nativeCanvas,
                                            ditheredBitmap = currentStrip,
                                            geometry = currentGeometry,
                                            canvasWidth = size.width,
                                            canvasHeight = size.height,
                                            cylWidth = cylW,
                                            cylHeight = cylH,
                                            rotation = effectiveRotation
                                        )
                                    }
                                }
                            } else {
                                // Fallback for API < 33
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "3D cylinder shader requires Android 13+",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(36.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }
                }

                // Categorized Controls Card
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PrimaryTabRow(
                            selectedTabIndex = selectedCategory.ordinal,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Color.Transparent,
                            divider = {}
                        ) {
                            BannerConfigCategory.entries.forEach { category ->
                                Tab(
                                    selected = selectedCategory == category,
                                    onClick = { selectedCategory = category },
                                    text = { Text(category.label) }
                                )
                            }
                        }

                        when (selectedCategory) {
                            BannerConfigCategory.BASIC -> {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        OutlinedButton(
                                            onClick = { imagePickerLauncher.launch("image/*") }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.size(6.dp))
                                            Text("Change Image")
                                        }
                                    }

                                    // Pole Diameter Slider
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Pole Diameter",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "${poleDiameterMm.roundToInt()} mm",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Slider(
                                            value = poleDiameterMm,
                                            onValueChange = { poleDiameterMm = it },
                                            valueRange = minDiameter..80f,
                                            steps = (80 - minDiameter.roundToInt() - 1).coerceAtLeast(0)
                                        )
                                    }

                                    // Print Diameter Measuring Tape Button
                                    PrintDiameterTapeButton(modifier = Modifier.fillMaxWidth())
                                }
                            }

                            BannerConfigCategory.QUALITY -> {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    DitheringAlgorithmSelector(
                                        selectedAlgorithm = ditheringAlgorithm,
                                        onAlgorithmSelected = { ditheringAlgorithm = it }
                                    )

                                    BrightnessCompensationSelector(
                                        brightnessMultiplier = brightnessMultiplier,
                                        onMultiplierChange = { brightnessMultiplier = it },
                                        brightnessCurve = brightnessCurve,
                                        onCurveChange = { brightnessCurve = it }
                                    )
                                }
                            }

                            BannerConfigCategory.MODIFY -> {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Pole Coverage",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "${(poleCoverageFraction * 100).roundToInt()}%",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Slider(
                                            value = poleCoverageFraction,
                                            onValueChange = { poleCoverageFraction = (it * 10f).roundToInt() / 10f },
                                            valueRange = 0.1f..1.0f,
                                            steps = 8
                                        )
                                        Text(
                                            text = if (poleCoverageFraction >= 0.99f) {
                                                "Image wraps continuously around the entire pole."
                                            } else {
                                                "Image covers ${(poleCoverageFraction * 100).roundToInt()}% of the circumference."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Phase Slider
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Phase",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "${(imagePhase * 100).roundToInt()}%",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Slider(
                                            value = imagePhase,
                                            onValueChange = { imagePhase = (it * 100f).roundToInt() / 100f },
                                            valueRange = 0f..1f
                                        )
                                        Text(
                                            text = "Shifts the image position and adjusts where margins appear.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Instances",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "$instances",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Slider(
                                            value = instances.toFloat(),
                                            onValueChange = { instances = it.roundToInt() },
                                            valueRange = 1f..6f,
                                            steps = 4
                                        )
                                        Text(
                                            text = if (instances == 1) {
                                                "Single image around the pole."
                                            } else {
                                                "Repeats $instances times around the pole (e.g. visible from opposite sides)."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Persistent Indicators: Perimeter and Strip Length
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Perimeter: ${(PI * poleDiameterMm).roundToInt()} mm",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val stripLengthText = geometry?.let {
                                "${String.format(Locale.US, "%.1f", it.estimatedLengthCm)} cm"
                            } ?: "..."
                            Text(
                                text = "Strip Length: $stripLengthText",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrintDiameterTapeButton(
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = {
            val tapeBitmap = SpiralBannerEngine.generateDiameterTape()
            PrinterManager.printAsync { printer ->
                val bytes = EscPosPrinterCommands.bitmapToBytes(tapeBitmap, false)
                val hexString = PrinterTextParserImg.bytesToHexadecimalString(bytes)
                printer.printFormattedText("[L]<img>$hexString</img>\n", 0f)
            }
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Straighten,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text("Print Diameter Measuring Tape")
    }
}
