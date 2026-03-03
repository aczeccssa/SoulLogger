package com.lestere.opensource.utils

/**
 * Validates path parameters to prevent path traversal attacks.
 * Allows only alphanumeric characters.
 */
private val ALPHANUMERIC_REGEX = Regex("^[a-zA-Z0-9]+$")

internal fun isValidPathParameter(param: String): Boolean {
    return ALPHANUMERIC_REGEX.matches(param)
}
