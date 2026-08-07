package com.arifr1234.receipttolenghi.ui.modes.general

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import java.util.UUID

class ImageState(
    bitmap: Bitmap? = null
) : PrintableState {
    override val id: String = UUID.randomUUID().toString()
    var bitmap by mutableStateOf(bitmap)
}

object ImagePrintableDefinition : PrintableDefinition<ImageState> {
    override val name = "Image"
    override val stateClass = ImageState::class

    override fun createDefaultState() = ImageState()

    @Composable
    override fun EditorUI(state: ImageState) {
        // Simple placeholder UI for now
        Text("Image Print Placeholder")
    }

    override fun print(state: ImageState, printer: EscPosPrinter) {
        state.bitmap?.let { bmp ->
            val hexString = PrinterTextParserImg.bitmapToHexadecimalString(printer, bmp)
            printer.printFormattedText("[C]<img>$hexString</img>\n", 0f)
        } ?: run {
            // Optional: Print a fallback or throw an error
            printer.printFormattedText("[C]No Image Selected\n", 0f)
        }
    }
}
