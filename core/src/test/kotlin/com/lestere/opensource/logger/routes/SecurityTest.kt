package com.lestere.opensource.logger.routes

import com.lestere.opensource.utils.isValidPathParameter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SecurityTest {

    @Test
    fun `isValidPathParameter accepts alphanumeric strings`() {
        assertTrue(isValidPathParameter("1726000000000"))
        assertTrue(isValidPathParameter("abcdef123456"))
        assertTrue(isValidPathParameter("A1b2C3d4"))
    }

    @Test
    fun `isValidPathParameter rejects path traversal attempts`() {
        assertFalse(isValidPathParameter("../etc/passwd"))
        assertFalse(isValidPathParameter(".."))
        assertFalse(isValidPathParameter("./"))
        assertFalse(isValidPathParameter("timestamp/../secret"))
    }

    @Test
    fun `isValidPathParameter rejects other special characters`() {
        assertFalse(isValidPathParameter("timestamp.log"))
        assertFalse(isValidPathParameter("log-file"))
        assertFalse(isValidPathParameter("log file"))
        assertFalse(isValidPathParameter("log_file"))
        assertFalse(isValidPathParameter("log;rm"))
        assertFalse(isValidPathParameter("log&ls"))
    }

    @Test
    fun `isValidPathParameter rejects empty string`() {
        assertFalse(isValidPathParameter(""))
    }
}
