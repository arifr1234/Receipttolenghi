package com.arifr1234.receipttolenghi.utils

import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AppErrorHandler {
    // The "Radio Tower" for global error messages
    private val _errorMessages = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val errorMessages = _errorMessages.asSharedFlow()

    /**
     * Reports an error globally, logging it and broadcasting it to the UI.
     * Use this directly only when an exception occurs inside a CoroutineExceptionHandler 
     * or when you need to manually report a logical error (no exception thrown).
     */
    fun handleError(e: Throwable, userMessage: String? = null) {
        val finalMessage = userMessage ?: e.message ?: "An unexpected error occurred"
        Log.e("AppError", finalMessage, e)
        _errorMessages.tryEmit(finalMessage)
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
            throw e // Re-throw to allow Kotlin's runCatching to intercept it upstream
        }
    }
}
