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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import java.util.UUID
import androidx.core.net.toUri

class ImageState(
    imageUri: String? = null,
    align: String = "C",
    isDithered: Boolean = true
) : PrintableState {
    override val id: String = UUID.randomUUID().toString()
    var imageUri by mutableStateOf(imageUri)
    var align by mutableStateOf(align)
    var isDithered by mutableStateOf(isDithered)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dithering (Grayscale simulation)")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = state.isDithered, onCheckedChange = { state.isDithered = it })
            }
        }
    }

    override fun print(context: Context, state: ImageState, printer: EscPosPrinter) {
        val uriStr = state.imageUri ?: run {
            printer.printFormattedText("[${state.align}]No Image Selected\n", 0f)
            return
        }

        val uri = uriStr.toUri()
        val bitmap: Bitmap = try {
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

        // Convert to HEX using the library's built-in dithering (gradient parameter)
        val hexString = PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap, state.isDithered)
        printer.printFormattedText("[${state.align}]<img>$hexString</img>\n", 0f)
    }
}
