package com.lestere.opensource.filter

import com.lestere.opensource.logger.Logger
import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.ThreadInfo
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FilterChainTest {

    private fun createLogger(
        level: SoulLogger.Level = SoulLogger.Level.INFO,
        command: String = "Test message",
        entry: String = "com.example.Test"
    ): Logger {
        return Logger(
            version = "1.0.0",
            timestamp = Clock.System.now(),
            logLevel = level,
            thread = ThreadInfo(Thread.currentThread()),
            entry = entry,
            command = command
        )
    }

    @Test
    fun `level filter accepts logs above minimum level`() {
        val filter = LevelFilter(SoulLogger.Level.INFO)
        
        val infoLog = createLogger(SoulLogger.Level.INFO)
        val debugLog = createLogger(SoulLogger.Level.DEBUG)
        
        assertTrue(filter.shouldLog(infoLog))
        assertFalse(filter.shouldLog(debugLog))
    }

    @Test
    fun `level filter rejects logs below minimum level`() {
        val filter = LevelFilter(SoulLogger.Level.WARN)
        
        val warnLog = createLogger(SoulLogger.Level.WARN)
        val infoLog = createLogger(SoulLogger.Level.INFO)
        
        assertTrue(filter.shouldLog(warnLog))
        assertFalse(filter.shouldLog(infoLog))
    }

    @Test
    fun `rate filter samples based on rate`() {
        // Use deterministic seed for testing
        val filter = RateFilter(sampleRate = 0.5, seed = 42)
        
        val logger = createLogger()
        
        // Run multiple times to check sampling behavior
        val results = (1..100).map { filter.shouldSample(logger) }
        
        // Should have some true and some false due to sampling
        assertTrue(results.any { it })
        assertTrue(results.any { !it })
    }

    @Test
    fun `rate filter with rate 1 always samples`() {
        val filter = RateFilter(sampleRate = 1.0)
        val logger = createLogger()
        
        assertTrue(filter.shouldSample(logger))
    }

    @Test
    fun `rate filter with rate 0 never samples`() {
        val filter = RateFilter(sampleRate = 0.0)
        val logger = createLogger()
        
        assertFalse(filter.shouldSample(logger))
    }

    @Test
    fun `regex filter includes matching pattern`() {
        val filter = RegexFilter(
            includePattern = ".*Exception.*",
            excludePattern = null
        )
        
        val matchingLog = createLogger(command = "NullPointerException occurred")
        val nonMatchingLog = createLogger(command = "Operation completed")
        
        assertTrue(filter.shouldLog(matchingLog))
        assertFalse(filter.shouldLog(nonMatchingLog))
    }

    @Test
    fun `regex filter excludes matching pattern`() {
        val filter = RegexFilter(
            includePattern = null,
            excludePattern = ".*debug.*"
        )
        
        val debugLog = createLogger(command = "debug info")
        val normalLog = createLogger(command = "normal operation")
        
        assertFalse(filter.shouldLog(debugLog))
        assertTrue(filter.shouldLog(normalLog))
    }

    @Test
    fun `regex filter with both include and exclude`() {
        val filter = RegexFilter(
            includePattern = ".*Error.*",
            excludePattern = ".*timeout.*"
        )
        
        val matchingLog = createLogger(command = "DatabaseError occurred")
        val excludedLog = createLogger(command = "timeout error")
        val nonMatchingLog = createLogger(command = "operation success")
        
        assertTrue(filter.shouldLog(matchingLog))
        assertFalse(filter.shouldLog(excludedLog))
        assertFalse(filter.shouldLog(nonMatchingLog))
    }

    @Test
    fun `composite filter requires all filters to pass`() {
        val filter = CompositeFilter(
            LevelFilter(SoulLogger.Level.INFO),
            RegexFilter(includePattern = ".*test.*", excludePattern = ".*debug.*")
        )
        
        // Should pass: INFO level + contains "test" + no "debug"
        val passingLog = createLogger(SoulLogger.Level.INFO, "this is a test")
        assertTrue(filter.shouldLog(passingLog))
        
        // Should fail: DEBUG level
        val levelFailLog = createLogger(SoulLogger.Level.DEBUG, "this is a test")
        assertFalse(filter.shouldLog(levelFailLog))
        
        // Should fail: contains "debug"
        val excludeFailLog = createLogger(SoulLogger.Level.INFO, "debug test")
        assertFalse(filter.shouldLog(excludeFailLog))
    }

    @Test
    fun `filter chain applies all filters in order`() {
        val filter = FilterChain(
            levelFilter = LevelFilter(SoulLogger.Level.WARN),
            rateFilter = RateFilter(sampleRate = 1.0)
        )
        
        val infoLog = createLogger(SoulLogger.Level.INFO)
        val warnLog = createLogger(SoulLogger.Level.WARN)
        val errorLog = createLogger(SoulLogger.Level.ERROR)
        
        // INFO should be filtered out by level
        assertFalse(filter.shouldLog(infoLog))
        
        // WARN and ERROR should pass
        assertTrue(filter.shouldLog(warnLog))
        assertTrue(filter.shouldLog(errorLog))
    }

    @Test
    fun `error boost increases sampling rate`() {
        val filter = RateFilter(sampleRate = 0.0) // Start with no sampling
        
        val normalLog = createLogger(SoulLogger.Level.INFO)
        val errorLog = createLogger(SoulLogger.Level.ERROR)
        
        // Without boost, INFO should not be sampled
        assertFalse(filter.shouldSample(normalLog))
        
        // Enable error boost
        filter.enableErrorBoost(durationMs = 60_000)
        
        // After enabling boost, errors should be sampled
        assertTrue(filter.shouldSample(errorLog))
        
        // INFO should still not be sampled after boost (only errors boosted)
        assertFalse(filter.shouldSample(normalLog))
    }

    @Test
    fun `filter config creates correct filter chain`() {
        val config = FilterConfig(
            enabled = true,
            minLevel = SoulLogger.Level.DEBUG,
            samplingEnabled = true,
            sampleRate = 0.5,
            samplingStrategy = RateFilter.SamplingStrategy.RANDOM,
            includePatterns = listOf(".*test.*"),
            excludePatterns = listOf(".*debug.*"),
            errorBoostEnabled = true
        )
        
        val chain = config.toFilterChain()
        
        assertNotNull(chain)
        assertTrue(chain.shouldLog(createLogger(SoulLogger.Level.DEBUG, "important test")))
    }
}
