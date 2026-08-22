package com.arifr1234.receipttolenghi.ui.modes.general

object PrintableRegistry {
    val allDefinitions = listOf(
        TextPrintableDefinition,
        ImagePrintableDefinition,
        SpacerPrintableDefinition
    )

    @Suppress("UNCHECKED_CAST")
    fun <T : PrintableState> getDefinitionFor(state: T): PrintableDefinition<T> {
        return allDefinitions.first { it.stateClass == state::class } as PrintableDefinition<T>
    }
}
