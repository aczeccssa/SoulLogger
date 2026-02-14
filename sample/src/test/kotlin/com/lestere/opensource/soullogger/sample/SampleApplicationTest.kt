package com.lestere.opensource.soullogger.sample

import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.SoulLogger.Level
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for Sample SoulLogger application.
 *
 * These tests verify basic SoulLogger API usage patterns.
 */
class SampleApplicationTest {

    @Test
    fun `log level fromHttpStatus maps correctly`() {
        assertEquals(Level.INFO, Level.of(io.ktor.http.HttpStatusCode.OK))
        assertEquals(Level.INFO, Level.of(io.ktor.http.HttpStatusCode.Created))
        assertEquals(Level.WARN, Level.of(io.ktor.http.HttpStatusCode.MovedPermanently))
        assertEquals(Level.ERROR, Level.of(io.ktor.http.HttpStatusCode.NotFound))
        assertEquals(Level.ERROR, Level.of(io.ktor.http.HttpStatusCode.InternalServerError))
    }

    @Test
    fun `log level values are available`() {
        val levels = Level.entries
        assertEquals(5, levels.size)
        assertTrue(levels.contains(Level.DEBUG))
        assertTrue(levels.contains(Level.INFO))
        assertTrue(levels.contains(Level.WARN))
        assertTrue(levels.contains(Level.ERROR))
        assertTrue(levels.contains(Level.FATAL))
    }
}
