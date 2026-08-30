package com.arifr1234.receipttolenghi.ui.modes.general

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dantsu.escposprinter.EscPosPrinter
import java.util.UUID

enum class SpacerStyle(val char: String?) {
    NONE(null),
    DASH("-"),
    EQUAL("="),
    ASTERISK("*"),
    TILDE("~"),
    UNDERSCORE("_"),
    DOT("."),
    HASH("#")
}

data class SpacerState(
    val style: SpacerStyle = SpacerStyle.DASH,
    val lines: Int = 1,
    override val id: String = UUID.randomUUID().toString()
) : PrintableState

object SpacerPrintableDefinition : PrintableDefinition<SpacerState> {
    override val displayName = "Spacer"
    override val stateClass = SpacerState::class

    override fun createDefaultState() = SpacerState()

    @Composable
    override fun EditorUI(state: SpacerState, onStateChange: (SpacerState) -> Unit) {
        Column {
            Text("Border Style", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            var expanded by remember { mutableStateOf(false) }
            
            Box {
                val currentStyle = state.style
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    val text = if (currentStyle.char == null) "No Border" else currentStyle.char.repeat(100)
                    Text(text = text, maxLines = 1, overflow = TextOverflow.Clip)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    SpacerStyle.entries.forEach { style ->
                        DropdownMenuItem(
                            text = {
                                val text = if (style.char == null) "No Border" else style.char.repeat(100)
                                Text(text = text, maxLines = 1, overflow = TextOverflow.Clip)
                            },
                            onClick = { 
                                onStateChange(state.copy(style = style))
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Trailing Blank Lines:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                
                IconButton(onClick = { if (state.lines > 0) onStateChange(state.copy(lines = state.lines - 1)) }) {
                    Text("-", style = MaterialTheme.typography.titleLarge)
                }
                
                Text(
                    text = "${state.lines}", 
                    style = MaterialTheme.typography.bodyLarge, 
                    modifier = Modifier.width(32.dp), 
                    textAlign = TextAlign.Center
                )
                
                IconButton(onClick = { if (state.lines < 10) onStateChange(state.copy(lines = state.lines + 1)) }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }

    override fun print(context: Context, state: SpacerState, printer: EscPosPrinter) {
        val emptyLines = "[L] \n".repeat(state.lines)
        val borderLine = state.style.char?.let { 
            "[C]" + it.repeat(printer.printerNbrCharactersPerLine) + "\n" 
        } ?: ""

        if (borderLine.isNotEmpty() || emptyLines.isNotEmpty()) {
            printer.printFormattedText(borderLine + emptyLines, 0f)
        }
    }
}
