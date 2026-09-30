package com.arifr1234.receipttolenghi.utils

import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.SnackbarDuration

data class AppMessage(
    override val message: String,
    val isError: Boolean = false,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short
) : SnackbarVisuals

object AppNotifier {
    // The "Radio Tower" for global messages
    private val _messages = MutableSharedFlow<AppMessage>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages = _messages.asSharedFlow()

    /**
     * Reports an error globally, logging it and broadcasting it to the UI.
     */
    fun handleError(e: Throwable, userMessage: String? = null) {
        val finalMessage = userMessage ?: e.message ?: "An unexpected error occurred"
        Log.e("AppError", finalMessage, e)
        _messages.tryEmit(AppMessage(finalMessage, isError = true))
    }

    /**
     * Shows a generic global snackbar message (e.g. for success notifications).
     */
    fun showMessage(message: String) {
        _messages.tryEmit(AppMessage(message, isError = false))
    }

    /**
     * Interceptor Wrapper: 
     * Executes the block. If it fails, logs/broadcasts the error globally, then re-throws it.
     */
    inline fun <T> runWithGlobalLog(userMessage: String? = null, block: () -> T): T {
        try {
            return block()
        } catch (e: Exception) {
            handleError(e, userMessage)
            throw e
        }
    }
}
