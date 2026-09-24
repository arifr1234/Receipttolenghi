package com.arifr1234.receipttolenghi.ui.modes.general

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.EscPosPrinterCommands
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import java.util.UUID
import androidx.core.graphics.createBitmap
import androidx.compose.ui.text.style.TextAlign

enum class RenderedTextAlignment(val label: String, val composeAlign: TextAlign, val staticAlign: Layout.Alignment) {
    START("Start", TextAlign.Start, Layout.Alignment.ALIGN_NORMAL),
    CENTER("Center", TextAlign.Center, Layout.Alignment.ALIGN_CENTER),
    END("End", TextAlign.End, Layout.Alignment.ALIGN_OPPOSITE)
}

data class RenderedTextState(
    val text: String = "",
    val align: RenderedTextAlignment = RenderedTextAlignment.START,
    val fontSize: Float = 32f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    override val id: String = UUID.randomUUID().toString()
) : PrintableState

object RenderedTextPrintableDefinition : PrintableDefinition<RenderedTextState> {
    override val displayName = "Rendered Text"
    override val stateClass = RenderedTextState::class

    override fun createDefaultState() = RenderedTextState()

    @Composable
    override fun EditorUI(state: RenderedTextState, onStateChange: (RenderedTextState) -> Unit) {
        val fontWeight = if (state.isBold) FontWeight.Bold else FontWeight.Normal
        val fontStyle = if (state.isItalic) FontStyle.Italic else FontStyle.Normal
        val textDecoration = if (state.isUnderline) TextDecoration.Underline else TextDecoration.None

        BasicTextField(
            value = state.text,
            onValueChange = { onStateChange(state.copy(text = it)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = state.align.composeAlign,
                textDirection = androidx.compose.ui.text.style.TextDirection.Content,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                textDecoration = textDecoration,
                // Map the raw pixel font size to sp for a rough preview in the UI
                fontSize = state.fontSize.sp
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    propagateMinConstraints = true
                ) {
                    innerTextField()
                }
            }
        )
    }

    @Composable
    override fun ConfigUI(state: RenderedTextState, onStateChange: (RenderedTextState) -> Unit) {
        Column {
            Text("Alignment", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RenderedTextAlignment.entries.forEach { alignment ->
                    FilterChip(
                        selected = state.align == alignment,
                        onClick = { onStateChange(state.copy(align = alignment)) },
                        label = { Text(alignment.label) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Font Size: ${state.fontSize.toInt()}px", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = state.fontSize,
                onValueChange = { onStateChange(state.copy(fontSize = it)) },
                valueRange = 16f..128f,
                steps = (128 - 16) / 4 - 1
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bold", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(checked = state.isBold, onCheckedChange = { onStateChange(state.copy(isBold = it)) })
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Italic", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(checked = state.isItalic, onCheckedChange = { onStateChange(state.copy(isItalic = it)) })
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Underline", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(checked = state.isUnderline, onCheckedChange = { onStateChange(state.copy(isUnderline = it)) })
                }
            }
        }
    }

    override fun print(context: Context, state: RenderedTextState, printer: EscPosPrinter) {
        if (state.text.isBlank()) return

        val printerWidthPx = printer.printerWidthPx

        var typefaceStyle = Typeface.NORMAL
        if (state.isBold && state.isItalic) typefaceStyle = Typeface.BOLD_ITALIC
        else if (state.isBold) typefaceStyle = Typeface.BOLD
        else if (state.isItalic) typefaceStyle = Typeface.ITALIC

        val textPaint = TextPaint().apply {
            isAntiAlias = false
            color = Color.BLACK
            textSize = state.fontSize
            typeface = Typeface.create(Typeface.DEFAULT, typefaceStyle)
            isUnderlineText = state.isUnderline
        }

        val alignment = state.align.staticAlign

        val staticLayout = StaticLayout.Builder.obtain(state.text, 0, state.text.length, textPaint, printerWidthPx)
            .setAlignment(alignment)
            .build()

        // Height might be 0 if text is empty, but we checked isBlank above
        val height = staticLayout.height.coerceAtLeast(1)

        val bitmap = createBitmap(printerWidthPx, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        staticLayout.draw(canvas)

        // Bypass EscPosPrinterSize limit of 256 height by calling the command directly
        val bytes = EscPosPrinterCommands.bitmapToBytes(bitmap, false)
        val hexString = PrinterTextParserImg.bytesToHexadecimalString(bytes)
        
        printer.printFormattedText("[L]<img>$hexString</img>\n", 0f)
    }

    override fun clearState(state: RenderedTextState): RenderedTextState {
        return state.copy(text = "")
    }
}
