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
uniform float2 uCylinderSize;
uniform float uRotation;
uniform float uOrigWidth;
uniform float uOrigHeight;
uniform float uPaperWidthPx;
uniform float uA;
uniform float uSinTheta;
uniform float uCosTheta;
uniform float uStripHeightPx;
uniform float uDitheredWidth;
uniform float uDitheredHeight;

const float PI = 3.14159265359;

float safeMod(float x, float m) {
    return x - m * floor(x / m);
}

half4 main(float2 fragCoord) {
    float2 center = uResolution * 0.5;
    float radius = uCylinderSize.x * 0.5;
    float cylTop = center.y - uCylinderSize.y * 0.5;
    float cylBottom = center.y + uCylinderSize.y * 0.5;

    float xRel = fragCoord.x - center.x;
    if (abs(xRel) > radius) {
        return half4(0.0, 0.0, 0.0, 0.0);
    }

    float nx = clamp(xRel / radius, -1.0, 1.0);
    float nz = sqrt(max(0.0, 1.0 - nx * nx));

    float poleTop = cylTop - 20.0;
    float poleBottom = cylBottom + 20.0;
    if (fragCoord.y < poleTop || fragCoord.y > poleBottom) {
        return half4(0.0, 0.0, 0.0, 0.0);
    }

    if (fragCoord.y < cylTop || fragCoord.y > cylBottom) {
        half poleColor = half(0.18 + 0.12 * nz);
        return half4(poleColor, poleColor, poleColor, 1.0);
    }

    float alpha = asin(nx);
    float phi = safeMod(alpha - uRotation, 2.0 * PI);

    float u0 = (phi / (2.0 * PI)) * uOrigWidth;
    float v = ((fragCoord.y - cylTop) / uCylinderSize.y) * uOrigHeight;

    float uPrime = u0 - uPaperWidthPx * uSinTheta;
    float vPrime = v + uA * uSinTheta;

    float xp0 = -uSinTheta * uPrime + uCosTheta * vPrime;
    float safePaperW = max(uPaperWidthPx, 1.0);
    float k = floor(xp0 / safePaperW);
    float xp = xp0 - k * safePaperW;
    float yp = uCosTheta * (uPrime + k * uOrigWidth) + uSinTheta * vPrime;

    if (yp < 0.0 || yp >= uStripHeightPx) {
        half poleColor = half(0.18 + 0.12 * nz);
        return half4(poleColor, poleColor, poleColor, 1.0);
    }

    float scaleX = uDitheredWidth / safePaperW;
    float scaleY = uDitheredHeight / max(uStripHeightPx, 1.0);
    float bmpX = clamp(floor(xp * scaleX) + 0.5, 0.5, max(uDitheredWidth - 0.5, 0.5));
    float bmpY = clamp(floor(yp * scaleY) + 0.5, 0.5, max(uDitheredHeight - 0.5, 0.5));

    half4 ditherColor = uStrip.eval(float2(bmpX, bmpY));
    half lighting = half(0.35 + 0.65 * nz);
    return half4(ditherColor.rgb * lighting, 1.0);
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
        shader.setFloatUniform("uOrigWidth", geometry.originalWidth.toFloat())
        shader.setFloatUniform("uOrigHeight", geometry.originalHeight.toFloat())
        shader.setFloatUniform("uPaperWidthPx", geometry.paperWidthPx)
        shader.setFloatUniform("uA", geometry.a)
        shader.setFloatUniform("uSinTheta", geometry.sinTheta)
        shader.setFloatUniform("uCosTheta", geometry.cosTheta)
        shader.setFloatUniform("uStripHeightPx", geometry.stripHeightPx.toFloat())
        shader.setFloatUniform("uDitheredWidth", ditheredBitmap.width.toFloat())
        shader.setFloatUniform("uDitheredHeight", ditheredBitmap.height.toFloat())

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
            val loadedBitmap = loadBitmapFromUri(context, uri)
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
                                    val maxW = size.width * 0.65f
                                    val maxH = size.height * 0.72f
                                    var cylW = maxH * aspect
                                    var cylH = maxH
                                    if (cylW > maxW) {
                                        cylW = maxW
                                        cylH = maxW / aspect
                                    }

                                    drawIntoCanvas { canvas ->
                                        shaderRenderer.render(
                                            canvas = canvas.nativeCanvas,
                                            ditheredBitmap = currentStrip,
                                            geometry = currentGeometry,
                                            canvasWidth = size.width,
                                            canvasHeight = size.height,
                                            cylWidth = cylW,
                                            cylHeight = cylH,
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

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
}
