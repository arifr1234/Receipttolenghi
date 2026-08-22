package com.arifr1234.receipttolenghi.ui.modes.general

import android.content.Context
import androidx.compose.runtime.Composable
import com.dantsu.escposprinter.EscPosPrinter
import androidx.compose.ui.text.style.TextAlign
import kotlin.reflect.KClass

enum class PrintAlignment(val tag: String, val label: String, val textAlign: TextAlign) {
    LEFT("L", "Left", TextAlign.Left),
    CENTER("C", "Center", TextAlign.Center),
    RIGHT("R", "Right", TextAlign.Right)
}

interface PrintableDefinition<T : PrintableState> {
    val name: String
    val stateClass: KClass<T>

    fun createDefaultState(): T

    @Composable
    fun EditorUI(state: T)

    @Composable
    fun ConfigUI(state: T) {}

    fun print(context: Context, state: T, printer: EscPosPrinter)
}
