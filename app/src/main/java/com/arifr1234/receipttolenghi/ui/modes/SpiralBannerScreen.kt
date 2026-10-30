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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arifr1234.receipttolenghi.printer.PrinterManager
import com.arifr1234.receipttolenghi.printer.SpiralBannerEngine
import com.arifr1234.receipttolenghi.printer.SpiralGeometry
import com.arifr1234.receipttolenghi.utils.ImageUtils
import com.dantsu.escposprinter.EscPosPrinterCommands
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val CYLINDER_AGSL_SRC = """
uniform shader uStrip;
uniform float2 uResolution;
uniform float uCylinderWidth;
uniform float uRotation;
uniform float2 uStripSize;
uniform float2 uTurns;

const float PI = 3.14159265359;

bool raycastCylinder(
    float2 fragCoord,
    out float2 cylinderUv,
    out float surfaceLighting
) {
    float cylinderRadius = uCylinderWidth * 0.5;
    float relativeScreenX = fragCoord.x - (uResolution.x * 0.5);

    if (abs(relativeScreenX) > cylinderRadius) {
        return false;
    }

    float surfaceNormalX = relativeScreenX / cylinderRadius;
    float surfaceNormalZ = sqrt(1.0 - surfaceNormalX * surfaceNormalX);
    surfaceLighting = 0.35 + 0.65 * surfaceNormalZ;

    float circumferentialAngle = mod(asin(surfaceNormalX) - uRotation, 2.0 * PI);
    if (circumferentialAngle < 0.0) {
        circumferentialAngle += 2.0 * PI;
    }

    // cylinderUv: [0.0, 1.0) x [0.0, 1.0] - Normalized cylinder surface UV
    cylinderUv = float2(circumferentialAngle / (2.0 * PI), fragCoord.y / uResolution.y);
    return true;
}

float2 mapCylinderToNormalizedStrip(float2 cylinderUv) {
    // unwrappedSpiralCoord: [0.0, +inf) x [0.0, +inf) - Coordinates in spiral units:
    // .x = width across paper strip in paper widths (integer = turn index k, fraction = width across strip)
    // .y = length along paper strip in turn units
    float2 unwrappedSpiralCoord = float2(
        1.0 - cylinderUv.x + uTurns.x * cylinderUv.y,
        cylinderUv.x + uTurns.y * cylinderUv.y
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

    // normalizedStripUv: [0.0, 1.0) x [0.0, 1.0] - Normalized UV on flat paper strip (width [0..1), length [0..1])
    float2 normalizedStripUv = mapCylinderToNormalizedStrip(cylinderUv);

    half4 ditherColor = uStrip.eval(normalizedStripUv * uStripSize);
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
        rotation: Float
    ) {
        val bitmapShader = BitmapShader(ditheredBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setInputShader("uStrip", bitmapShader)
        shader.setFloatUniform("uResolution", canvasWidth, canvasHeight)
        shader.setFloatUniform("uCylinderWidth", cylWidth)
        shader.setFloatUniform("uRotation", rotation)
        shader.setFloatUniform("uStripSize", ditheredBitmap.width.toFloat(), ditheredBitmap.height.toFloat())

        val aspect = geometry.originalHeight.toFloat() / geometry.originalWidth.toFloat()
        val nx = aspect * (geometry.cosTheta / geometry.sinTheta)
        val ny = aspect * (geometry.sinTheta / geometry.cosTheta)
        shader.setFloatUniform("uTurns", nx, ny)

        paint.shader = shader
        canvas.drawRect(0f, 0f, canvasWidth, canvasHeight, paint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpiralBannerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var poleDiameterMm by remember { mutableFloatStateOf(35f) }
    var geometry by remember { mutableStateOf<SpiralGeometry?>(null) }
    var ditheredStrip by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }

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

    LaunchedEffect(sourceBitmap, poleDiameterMm) {
        val bmp = sourceBitmap ?: return@LaunchedEffect
        isProcessing = true
        delay(120) // Debounce rapid slider movement
        withContext(Dispatchers.Default) {
            val geo = SpiralBannerEngine.calculateGeometry(
                originalWidth = bmp.width,
                originalHeight = bmp.height,
                poleDiameterMm = poleDiameterMm
            )
            val unwrapped = SpiralBannerEngine.unwrapToStrip(bmp, geo)
            val dithered = SpiralBannerEngine.processForPrinter(unwrapped)
            geometry = geo
            ditheredStrip = dithered
            isProcessing = false
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
                            val strip = ditheredStrip ?: return@IconButton
                            PrinterManager.printAsync { printer ->
                                val bytes = EscPosPrinterCommands.bitmapToBytes(strip, false)
                                val hexString = PrinterTextParserImg.bytesToHexadecimalString(bytes)
                                printer.printFormattedText("[L]<img>$hexString</img>\n", 0f)
                            }
                        },
                        enabled = ditheredStrip != null && !isProcessing
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
            val currentBitmap = sourceBitmap
            val currentGeometry = geometry
            val currentStrip = ditheredStrip

            if (currentBitmap == null) {
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
                        if (currentStrip != null && currentGeometry != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val shaderRenderer = remember { CylinderShaderRenderer() }
                                androidx.compose.foundation.Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectHorizontalDragGestures { _, dragAmount ->
                                                rotationAngle = (rotationAngle - dragAmount * 0.012f) % (2f * PI.toFloat())
                                            }
                                        }
                                ) {
                                    val aspect = currentGeometry.originalWidth.toFloat() / (PI.toFloat() * currentGeometry.originalHeight.toFloat())
                                    val cylW = (size.height * aspect).coerceAtMost(size.width * 0.9f)

                                    drawIntoCanvas { canvas ->
                                        shaderRenderer.render(
                                            canvas = canvas.nativeCanvas,
                                            ditheredBitmap = currentStrip,
                                            geometry = currentGeometry,
                                            canvasWidth = size.width,
                                            canvasHeight = size.height,
                                            cylWidth = cylW,
                                            rotation = rotationAngle
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

                            // Subtle hint overlay
                            Text(
                                text = "Drag horizontally to spin 360°",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp)
                            )
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

                // Controls Card
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Image: ${currentBitmap.width} × ${currentBitmap.height} px",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { imagePickerLauncher.launch("image/*") }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Change")
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

                            if (currentGeometry != null) {
                                val stripLengthCm = String.format(
                                    "%.1f",
                                    (currentGeometry.stripHeightPx.toFloat() / currentGeometry.paperWidthPx) * (PrinterManager.PRINTER_WIDTH_MM / 10f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Perimeter: ${(PI * poleDiameterMm).roundToInt()} mm",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Strip Length: $stripLengthCm cm",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Physical instruction card
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Wrap the strip edge-to-edge around a cylindrical pole with diameter ${poleDiameterMm.roundToInt()} mm to view the image.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
