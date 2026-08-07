package com.arifr1234.receipttolenghi.ui.modes.general

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dantsu.escposprinter.EscPosPrinter
import java.util.UUID

class TextState(
    text: String = "",
    align: String = "L"
) : PrintableState {
    override val id: String = UUID.randomUUID().toString()
    var text by mutableStateOf(text)
    var align by mutableStateOf(align)
}

object TextPrintableDefinition : PrintableDefinition<TextState> {
    override val name = "Text"
    override val stateClass = TextState::class

    override fun createDefaultState() = TextState()

    @Composable
    override fun EditorUI(state: TextState) {
        OutlinedTextField(
            value = state.text,
            onValueChange = { state.text = it },
            label = { Text("Enter text to print") },
            modifier = Modifier.fillMaxWidth()
        )
    }

    override fun print(state: TextState, printer: EscPosPrinter) {
        printer.printFormattedText("[${state.align}]${state.text}\n", 0f)
    }
}
