package com.arifr1234.receipttolenghi.ui.modes.general

import androidx.compose.runtime.Composable
import com.dantsu.escposprinter.EscPosPrinter
import kotlin.reflect.KClass

interface PrintableDefinition<T : PrintableState> {
    val name: String
    val stateClass: KClass<T>

    fun createDefaultState(): T

    @Composable
    fun EditorUI(state: T)

    fun print(state: T, printer: EscPosPrinter)
}
