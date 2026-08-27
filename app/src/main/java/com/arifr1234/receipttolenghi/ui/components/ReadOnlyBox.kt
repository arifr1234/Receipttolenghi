package com.arifr1234.receipttolenghi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A wrapper that visually dims its content and intercepts all touch events
 * if [isReadOnly] is true, effectively making the entire subtree read-only.
 */
@Composable
fun ReadOnlyBox(
    isReadOnly: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.alpha(if (isReadOnly) 0.6f else 1f)
    ) {
        content()
        if (isReadOnly) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                    .changes
                                    .forEach { it.consume() }
                            }
                        }
                    }
            )
        }
    }
}
