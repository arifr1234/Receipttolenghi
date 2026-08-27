package com.arifr1234.receipttolenghi.ui.modes.general

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import android.content.Context
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
    TALL("tall", "Tall", 32.sp, TextGeometricTransform(scaleX = 0.5f)),
    BIG("big", "Big", 32.sp, null)
}

class TextState(
    text: String = "",
    align: PrintAlignment = PrintAlignment.LEFT
) : PrintableState {
    override val id: String = UUID.randomUUID().toString()
    var text by mutableStateOf(text)
    var align by mutableStateOf(align)
    var size by mutableStateOf(TextSize.NORMAL)
    var isBold by mutableStateOf(false)
    var isUnderline by mutableStateOf(false)
}

object TextPrintableDefinition : PrintableDefinition<TextState> {
    override val name = "Text"
    override val stateClass = TextState::class

    override fun createDefaultState() = TextState()

    @Composable
    override fun EditorUI(state: TextState) {
        val fontWeight = if (state.isBold) FontWeight.Bold else FontWeight.Normal
        val textDecoration = if (state.isUnderline) TextDecoration.Underline else TextDecoration.None

        OutlinedTextField(
            value = state.text,
            onValueChange = { state.text = it },
            label = { Text("Enter text to print") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(
                textAlign = state.align.textAlign,
                fontWeight = fontWeight,
                textDecoration = textDecoration,
                fontSize = state.size.fontSize,
                textGeometricTransform = state.size.transform
            )
        )
    }

    @Composable
    override fun ConfigUI(state: TextState) {
        Column {
            Text("Alignment", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrintAlignment.entries.forEach { alignment ->
                    FilterChip(
                        selected = state.align == alignment,
                        onClick = { state.align = alignment },
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
                        onClick = { state.size = textSize },
                        label = { Text(textSize.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bold")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = state.isBold, onCheckedChange = { state.isBold = it })
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text("Underline")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = state.isUnderline, onCheckedChange = { state.isUnderline = it })
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

    override fun clearState(state: TextState) {
        state.text = ""
    }
}
