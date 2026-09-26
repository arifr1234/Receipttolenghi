package com.arifr1234.receipttolenghi.ui.modes.general

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.font.FontWeight
import android.content.Context
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.dantsu.escposprinter.EscPosPrinter
import java.util.UUID

enum class TextSize(val tag: String, val label: String, val fontSize: TextUnit, val transform: TextGeometricTransform?) {
    NORMAL("normal", "Normal", 16.sp, null),
    WIDE("wide", "Wide", 16.sp, TextGeometricTransform(scaleX = 2f)),
    TALLke("tall", "Tall", 32.sp, TextGeometricTransform(scaleX = 0.5f)),
    BIG("big", "Big", 32.sp, null)
}

data class TextState(
    val text: String = "",
    val align: PrintAlignment = PrintAlignment.LEFT,
    val size: TextSize = TextSize.NORMAL,
    val isBold: Boolean = false,
    val isUnderline: Boolean = false,
    override val id: String = UUID.randomUUID().toString()
) : PrintableState

object TextPrintableDefinition : PrintableDefinition<TextState> {
    override val displayName = "Text"
    override val stateClass = TextState::class

    override fun createDefaultState() = TextState()

    @Composable
    override fun EditorUI(state: TextState, onStateChange: (TextState) -> Unit) {
        val fontWeight = if (state.isBold) FontWeight.Bold else FontWeight.Normal
        val textDecoration = if (state.isUnderline) TextDecoration.Underline else TextDecoration.None

        Column {
            key(state.size) {
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
                        textAlign = state.align.textAlign,
                        textDirection = androidx.compose.ui.text.style.TextDirection.Content,
                        fontWeight = fontWeight,
                        textDecoration = textDecoration,
                        fontSize = state.size.fontSize,
                        textGeometricTransform = state.size.transform
                    ),
                )
            }
            
            val hasUnsupportedChars = state.text.any { it.code > 127 }
            if (hasUnsupportedChars) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Special characters may not print correctly. Use 'Rendered Text' instead.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    @Composable
    override fun ConfigUI(state: TextState, onStateChange: (TextState) -> Unit) {
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
            Text("Size", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextSize.entries.forEach { textSize ->
                    FilterChip(
                        selected = state.size == textSize,
                        onClick = { onStateChange(state.copy(size = textSize)) },
                        label = { Text(textSize.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.isBold,
                    onClick = { onStateChange(state.copy(isBold = !state.isBold)) },
                    label = { Text("Bold") }
                )
                
                FilterChip(
                    selected = state.isUnderline,
                    onClick = { onStateChange(state.copy(isUnderline = !state.isUnderline)) },
                    label = { Text("Underline") }
                )
            }
        }
    }

    override fun print(context: Context, state: TextState, printer: EscPosPrinter) {
        val boldStart = if (state.isBold) "<b>" else ""
        val boldEnd = if (state.isBold) "</b>" else ""
        val ulStart = if (state.isUnderline) "<u>" else ""
        val ulEnd = if (state.isUnderline) "</u>" else ""

        val formatted = "[${state.align.tag}]<font size='${state.size.tag}'>$boldStart$ulStart${state.text}$ulEnd$boldEnd</font>\n"
        printer.printFormattedText(formatted, 0f)
    }

    override fun clearState(state: TextState): TextState {
        return state.copy(text = "")
    }

    override fun getClipboardText(state: TextState): String? {
        return state.text.takeIf { it.isNotBlank() }
    }
}
