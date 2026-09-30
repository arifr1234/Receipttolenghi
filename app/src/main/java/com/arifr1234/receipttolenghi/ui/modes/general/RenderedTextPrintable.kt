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
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.arifr1234.receipttolenghi.R
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
    val fontName: String? = null,
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

        val provider = remember {
            androidx.compose.ui.text.googlefonts.GoogleFont.Provider(
                providerAuthority = "com.google.android.gms.fonts",
                providerPackage = "com.google.android.gms",
                certificates = R.array.com_google_android_gms_fonts_certs
            )
        }
        val fontFamily = remember(state.fontName) {
            if (state.fontName != null) {
                androidx.compose.ui.text.font.FontFamily(
                    androidx.compose.ui.text.googlefonts.Font(
                        googleFont = androidx.compose.ui.text.googlefonts.GoogleFont(state.fontName),
                        fontProvider = provider
                    )
                )
            } else {
                androidx.compose.ui.text.font.FontFamily.Default
            }
        }

        key(state.fontSize) {
            BasicTextField(
                value = state.text,
                onValueChange = { onStateChange(state.copy(text = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = state.align.composeAlign,
                    textDirection = androidx.compose.ui.text.style.TextDirection.Content,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    textDecoration = textDecoration,
                    fontFamily = fontFamily,
                    // Map the raw pixel font size to sp for a rough preview in the UI
                    fontSize = state.fontSize.sp
                ),
            )
        }
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
            var showFontSelector by remember { mutableStateOf(false) }
            val fontListState = androidx.compose.foundation.lazy.rememberLazyListState()
            var fontSearchQuery by remember { mutableStateOf("") }
            var fontSelectedSubsets by remember { mutableStateOf(emptySet<String>()) }
            var fontSelectedTags by remember { mutableStateOf(emptyMap<String, String>()) }
            
            if (showFontSelector) {
                FontSelectorDialog(
                    onDismiss = { showFontSelector = false },
                    onFontSelected = { onStateChange(state.copy(fontName = it)) },
                    listState = fontListState,
                    searchQuery = fontSearchQuery,
                    onSearchQueryChange = { fontSearchQuery = it },
                    selectedSubsets = fontSelectedSubsets,
                    onSelectedSubsetsChange = { fontSelectedSubsets = it },
                    selectedTags = fontSelectedTags,
                    onSelectedTagsChange = { fontSelectedTags = it }
                )
            }

            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            Button(
                onClick = { 
                    focusManager.clearFocus()
                    showFontSelector = true 
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text(state.fontName ?: "Default Font")
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                FilterChip(
                    selected = state.isBold,
                    onClick = { onStateChange(state.copy(isBold = !state.isBold)) },
                    label = { Text("Bold") }
                )
                
                FilterChip(
                    selected = state.isItalic,
                    onClick = { onStateChange(state.copy(isItalic = !state.isItalic)) },
                    label = { Text("Italic") }
                )
                
                FilterChip(
                    selected = state.isUnderline,
                    onClick = { onStateChange(state.copy(isUnderline = !state.isUnderline)) },
                    label = { Text("Underline") }
                )
            }
        }
    }

    override fun print(context: Context, state: RenderedTextState, printer: EscPosPrinter) {
        if (state.text.isEmpty()) return

        val printerWidthPx = printer.printerWidthPx

        var typefaceStyle = Typeface.NORMAL
        if (state.isBold && state.isItalic) typefaceStyle = Typeface.BOLD_ITALIC
        else if (state.isBold) typefaceStyle = Typeface.BOLD
        else if (state.isItalic) typefaceStyle = Typeface.ITALIC

        var baseTypeface = Typeface.DEFAULT
        if (state.fontName != null) {
            val request = androidx.core.provider.FontRequest(
                "com.google.android.gms.fonts",
                "com.google.android.gms",
                state.fontName,
                R.array.com_google_android_gms_fonts_certs
            )
            val result = androidx.core.provider.FontsContractCompat.fetchFonts(context, null, request)
            if (result.statusCode == androidx.core.provider.FontsContractCompat.FontFamilyResult.STATUS_OK) {
                val fonts = result.fonts
                if (fonts.isNotEmpty()) {
                    baseTypeface = androidx.core.provider.FontsContractCompat.buildTypeface(context, null, fonts) ?: Typeface.DEFAULT
                }
            } else {
                com.arifr1234.receipttolenghi.utils.AppNotifier.handleError(
                    IllegalStateException("Failed to fetch font '${state.fontName}' for printing. Status code: ${result.statusCode}")
                )
            }
        }

        val textPaint = TextPaint().apply {
            isAntiAlias = false
            color = Color.BLACK
            textSize = state.fontSize
            typeface = Typeface.create(baseTypeface, typefaceStyle)
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

    override fun getClipboardText(state: RenderedTextState): String? {
        return state.text.takeIf { it.isNotBlank() }
    }
}
