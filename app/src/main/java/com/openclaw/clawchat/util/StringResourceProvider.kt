package com.openclaw.clawchat.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * String resource provider for ViewModel-level classes
 *
 * This singleton provides access to string resources without requiring
 * Composable context. Useful for error messages in non-UI classes.
 */
@Singleton
class StringResourceProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Get a string resource by ID
     */
    fun getString(resId: Int): String = context.getString(resId)

    /**
     * Get a formatted string resource
     */
    fun getString(resId: Int, formatArgs: Any?): String = context.getString(resId, formatArgs)

    /**
     * Get a formatted string resource with multiple args
     */
    fun getString(resId: Int, vararg formatArgs: Any): String = context.getString(resId, *formatArgs)
}