package com.lestere.opensource.utils

/**
 * Validates path parameters to prevent path traversal attacks.
 * Allows only alphanumeric characters.
 */
internal fun isValidPathParameter(param: String): Boolean {
    val regex = Regex("^[a-zA-Z0-9]+$")
    return regex.matches(param)
}
