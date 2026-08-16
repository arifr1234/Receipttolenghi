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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dantsu.escposprinter.EscPosPrinter
import java.util.UUID

class TextState(
    text: String = "",
    align: String = "L"
) : PrintableState {
    override val id: String = UUID.randomUUID().toString()
    var text by mutableStateOf(text)
    var align by mutableStateOf(align)
    var size by mutableStateOf("normal")
    var isBold by mutableStateOf(false)
    var isUnderline by mutableStateOf(false)
}

object TextPrintableDefinition : PrintableDefinition<TextState> {
    override val name = "Text"
    override val stateClass = TextState::class

    override fun createDefaultState() = TextState()

    @Composable
    override fun EditorUI(state: TextState) {
        val textAlign = when (state.align) {
            "C" -> TextAlign.Center
            "R" -> TextAlign.Right
            else -> TextAlign.Left
        }
        val fontWeight = if (state.isBold) FontWeight.Bold else FontWeight.Normal
        val textDecoration = if (state.isUnderline) TextDecoration.Underline else TextDecoration.None
        val fontSize = when (state.size) {
            "wide" -> 16.sp
            "tall" -> 32.sp
            "big" -> 32.sp
            else -> 16.sp
        }
        val textGeometricTransform = when (state.size) {
            "wide" -> TextGeometricTransform(scaleX = 2f)
            "tall" -> TextGeometricTransform(scaleX = 0.5f)
            else -> null
        }

        OutlinedTextField(
            value = state.text,
            onValueChange = { state.text = it },
            label = { Text("Enter text to print") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(
                textAlign = textAlign,
                fontWeight = fontWeight,
                textDecoration = textDecoration,
                fontSize = fontSize,
                textGeometricTransform = textGeometricTransform
            )
        )
    }

    @Composable
    override fun ConfigUI(state: TextState) {
        Column {
            Text("Alignment", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.align == "L", onClick = { state.align = "L" }, label = { Text("Left") })
                FilterChip(selected = state.align == "C", onClick = { state.align = "C" }, label = { Text("Center") })
                FilterChip(selected = state.align == "R", onClick = { state.align = "R" }, label = { Text("Right") })
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text("Size", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.size == "normal", onClick = { state.size = "normal" }, label = { Text("Normal") })
                FilterChip(selected = state.size == "wide", onClick = { state.size = "wide" }, label = { Text("Wide") })
                FilterChip(selected = state.size == "tall", onClick = { state.size = "tall" }, label = { Text("Tall") })
                FilterChip(selected = state.size == "big", onClick = { state.size = "big" }, label = { Text("Big") })
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

        val formatted = "[${state.align}]<font size='${state.size}'>$boldStart$ulStart${state.text}$ulEnd$boldEnd</font>\n"
        printer.printFormattedText(formatted, 0f)
    }
}
