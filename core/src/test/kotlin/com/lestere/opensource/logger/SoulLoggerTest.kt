package com.lestere.opensource.logger

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for SoulLogger parsing and basic functionality.
 */
class SoulLoggerTest {

    @Test
    fun `parse valid log line successfully`() {
        val logLine = "0.0.1-dev 2024-01-15T10:30:45.123456Z INFO Thread-1(123) [com.example.Main] Operation completed"
        val logger = Logger.parse(logLine)

        assertNotNull(logger)
        assertEquals("0.0.1-dev", logger?.version)
        assertEquals("2024-01-15T10:30:45.123456Z", logger?.timestamp?.toString())
        assertEquals(SoulLogger.Level.INFO, logger?.logLevel)
        assertEquals("Thread-1", logger?.thread?.name)
        assertEquals(123L, logger?.thread?.id)
        assertEquals("com.example.Main", logger?.entry)
        assertEquals("Operation completed", logger?.command)
    }

    @Test
    fun `parse log line with different levels`() {
        val levels = listOf("DEBUG", "INFO", "WARN", "ERROR", "FATAL")

        levels.forEach { level ->
            val logLine = "0.0.1-dev 2024-01-15T10:30:45.123456Z $level Thread-1(123) [com.example.Main] Test message"
            val logger = Logger.parse(logLine)

            assertNotNull(logger, "Failed to parse level: $level")
            assertEquals(SoulLogger.Level.valueOf(level), logger?.logLevel)
        }
    }

    @Test
    fun `parse log line with complex entry class`() {
        val logLine = "0.0.1-dev 2024-01-15T10:30:45.123456Z INFO Thread-1(123) [com.example.module.Controller] Request handled"
        val logger = Logger.parse(logLine)

        assertNotNull(logger)
        assertEquals("com.example.module.Controller", logger?.entry)
    }

    @Test
    fun `parse log line with multiline command`() {
        val logLine = "0.0.1-dev 2024-01-15T10:30:45.123456Z ERROR Thread-1(123) [com.example.Main] Exception: java.lang.NullPointerException at line 42"
        val logger = Logger.parse(logLine)

        assertNotNull(logger)
        assertEquals(SoulLogger.Level.ERROR, logger?.logLevel)
        assertEquals("Exception: java.lang.NullPointerException at line 42", logger?.command)
    }

    @Test
    fun `parse invalid log line returns null`() {
        val invalidLines = listOf(
            "invalid log line",
            "2024-01-15T10:30:45.123456Z INFO Thread-1 [com.example.Main] Missing thread id",
            "",
            "   "
        )

        invalidLines.forEach { line ->
            val logger = Logger.parse(line)
            assertNull(logger, "Should return null for: $line")
        }
    }

    @Test
    fun `parse handles unknown version gracefully`() {
        val logLine = "unknown-version 2024-01-15T10:30:45.123456Z INFO Thread-1(123) [com.example.Main] Test message"
        val logger = Logger.parse(logLine)

        assertNotNull(logger)
        assertEquals("unknown-version", logger?.version)
    }

    @Test
    fun `parse handles unknown level by returning null`() {
        // Unknown levels (not in regex pattern) cause regex to not match, returning null
        val logLine = "0.0.1-dev 2024-01-15T10:30:45.123456Z UNKNOWN Thread-1(123) [com.example.Main] Test message"
        val logger = Logger.parse(logLine)
        // Unknown level causes regex mismatch, returns null
        assertNull(logger)
    }

    @Test
    fun `parse handles invalid timestamp format by returning null`() {
        // Invalid timestamp format causes regex to not match, returning null
        val logLine = "0.0.1-dev invalid-timestamp INFO Thread-1(123) [com.example.Main] Test message"
        val logger = Logger.parse(logLine)
        // Invalid timestamp format causes regex mismatch, returns null
        assertNull(logger)
    }

    @Test
    fun `parse handles thread without parentheses format`() {
        val logLine = "0.0.1-dev 2024-01-15T10:30:45.123456Z INFO invalid-format [com.example.Main] Test message"
        val logger = Logger.parse(logLine)

        assertNull(logger) // Should fail due to invalid thread format
    }

    @Test
    fun `logger toString produces parseable output`() {
        val timestamp = Instant.parse("2024-01-15T10:30:45.123456Z")
        val threadInfo = ThreadInfo(123L, "TestThread")
        val originalLogger = Logger(
            version = "0.0.1-dev",
            timestamp = timestamp,
            logLevel = SoulLogger.Level.INFO,
            thread = threadInfo,
            entry = "com.example.Test",
            command = "Test operation"
        )

        val logString = originalLogger.toString()
        val parsedLogger = Logger.parse(logString)

        assertNotNull(parsedLogger)
        assertEquals(originalLogger.version, parsedLogger?.version)
        assertEquals(originalLogger.timestamp, parsedLogger?.timestamp)
        assertEquals(originalLogger.logLevel, parsedLogger?.logLevel)
        assertEquals(originalLogger.thread.name, parsedLogger?.thread?.name)
        assertEquals(originalLogger.thread.id, parsedLogger?.thread?.id)
        assertEquals(originalLogger.entry, parsedLogger?.entry)
        assertEquals(originalLogger.command, parsedLogger?.command)
    }

    @Test
    fun `CSV_HEADER has correct columns`() {
        val expectedColumns = listOf("Version", "Timestamp", "Level", "Thread", "Entry", "Command")
        assertEquals(expectedColumns, Logger.CSV_HEADER.toList())
    }

    @Test
    fun `paramsArray returns correct values`() {
        val timestamp = Instant.parse("2024-01-15T10:30:45.123456Z")
        val threadInfo = ThreadInfo(123L, "TestThread")
        val logger = Logger(
            version = "0.0.1-dev",
            timestamp = timestamp,
            logLevel = SoulLogger.Level.INFO,
            thread = threadInfo,
            entry = "com.example.Test",
            command = "Test operation"
        )

        val params = logger.paramsArray
        assertEquals(6, params.size)
        assertEquals("0.0.1-dev", params[0])
        assertEquals("2024-01-15T10:30:45.123456Z", params[1])
        assertEquals("INFO", params[2])
        assertEquals("TestThread(123)", params[3])
        assertEquals("com.example.Test", params[4])
        assertEquals("Test operation", params[5])
    }
}
