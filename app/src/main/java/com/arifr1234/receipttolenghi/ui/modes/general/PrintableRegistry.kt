package com.arifr1234.receipttolenghi.ui.modes.general

enum class PrintableType(val definition: PrintableDefinition<out PrintableState>) {
    TEXT(TextPrintableDefinition),
    RENDERED_TEXT(RenderedTextPrintableDefinition),
    IMAGE(ImagePrintableDefinition),
    SPACER(SpacerPrintableDefinition)
}

object PrintableRegistry {
    fun getTypeFor(state: PrintableState): PrintableType {
        return PrintableType.entries.first { it.definition.stateClass == state::class }
    }

    @Suppress("UNCHECKED_CAST")
    fun getTypedDefinitionFor(type: PrintableType): PrintableDefinition<PrintableState> {
        return type.definition as PrintableDefinition<PrintableState>
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : PrintableState> getTypedDefinitionFor(state: T): PrintableDefinition<T> {
        return getTypeFor(state).definition as PrintableDefinition<T>
    }
}
