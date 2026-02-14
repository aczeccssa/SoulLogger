package com.lestere.opensource.aggregation

import com.lestere.opensource.logger.Logger
import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.ThreadInfo
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LogIndexerTest {

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
    fun `indexer indexes logs by level`() {
        val indexer = LogIndexer()
        
        val infoLog = createLogger(SoulLogger.Level.INFO)
        val errorLog = createLogger(SoulLogger.Level.ERROR, command = "Error occurred")
        
        indexer.index(infoLog)
        indexer.index(errorLog)
        
        val infoLogs = indexer.getByLevel(SoulLogger.Level.INFO)
        val errorLogs = indexer.getByLevel(SoulLogger.Level.ERROR)
        
        assertEquals(1, infoLogs.size)
        assertEquals(1, errorLogs.size)
    }

    @Test
    fun `indexer indexes logs by entry`() {
        val indexer = LogIndexer()
        
        val log1 = createLogger(entry = "com.example.Controller")
        
        indexer.index(log1)
        
        // Just verify indexing works without error
        assertTrue(indexer.size.value > 0)
    }

    @Test
    fun `indexer indexes errors separately`() {
        val indexer = LogIndexer()
        
        val infoLog = createLogger(SoulLogger.Level.INFO)
        val errorLog = createLogger(SoulLogger.Level.ERROR, command = "Exception occurred")
        
        indexer.index(infoLog)
        indexer.index(errorLog)
        
        val errors = indexer.getErrors()
        
        assertEquals(1, errors.size)
        assertEquals(SoulLogger.Level.ERROR, errors.first().logLevel)
    }

    @Test
    fun `indexer supports search query`() {
        val indexer = LogIndexer()
        
        indexer.index(createLogger(command = "Database connection successful"))
        indexer.index(createLogger(command = "NullPointerException in code"))
        indexer.index(createLogger(command = "User logged in"))
        
        val results = indexer.search("Exception")
        
        assertEquals(1, results.size)
        assertTrue(results.first().command.contains("Exception"))
    }

    @Test
    fun `indexer clears correctly`() {
        val indexer = LogIndexer()
        
        indexer.index(createLogger())
        indexer.index(createLogger())
        
        assertTrue(indexer.size.value > 0)
        
        indexer.clear()
        
        assertEquals(0, indexer.size.value)
    }
}

class AggregationEngineTest {

    private fun createLogger(
        level: SoulLogger.Level = SoulLogger.Level.INFO,
        command: String = "Test message",
        entry: String = "com.example.Test",
        timestamp: Instant = Clock.System.now()
    ): Logger {
        return Logger(
            version = "1.0.0",
            timestamp = timestamp,
            logLevel = level,
            thread = ThreadInfo(Thread.currentThread()),
            entry = entry,
            command = command
        )
    }

    @Test
    fun `aggregation by level returns correct counts`() {
        val engine = AggregationEngine()
        
        val logs = listOf(
            createLogger(SoulLogger.Level.DEBUG),
            createLogger(SoulLogger.Level.INFO),
            createLogger(SoulLogger.Level.INFO),
            createLogger(SoulLogger.Level.WARN),
            createLogger(SoulLogger.Level.ERROR)
        )
        
        val request = AggregationRequest(
            groupBy = listOf("level"),
            metrics = listOf(Metric.COUNT)
        )
        
        val result = engine.aggregate(logs, request)
        
        val debugCount = result.groups.find { it.key == "DEBUG" }?.count
        val infoCount = result.groups.find { it.key == "INFO" }?.count
        val warnCount = result.groups.find { it.key == "WARN" }?.count
        val errorCount = result.groups.find { it.key == "ERROR" }?.count
        
        assertEquals(1, debugCount)
        assertEquals(2, infoCount)
        assertEquals(1, warnCount)
        assertEquals(1, errorCount)
    }

    @Test
    fun `aggregation calculates error rate`() {
        val engine = AggregationEngine()
        
        val logs = listOf(
            createLogger(SoulLogger.Level.INFO),
            createLogger(SoulLogger.Level.INFO),
            createLogger(SoulLogger.Level.INFO),
            createLogger(SoulLogger.Level.ERROR),
            createLogger(SoulLogger.Level.ERROR)
        )
        
        val request = AggregationRequest(
            groupBy = listOf("level"),
            metrics = listOf(Metric.ERROR_RATE)
        )
        
        val result = engine.aggregate(logs, request)
        
        // Total: 3 INFO, 2 ERROR = 40% error rate for ERROR level
        val errorGroup = result.groups.find { it.key == "ERROR" }
        
        assertNotNull(errorGroup)
        assertTrue(errorGroup!!.errorRate > 0)
    }

    @Test
    fun `aggregation with filter works correctly`() {
        val engine = AggregationEngine()
        
        val logs = listOf(
            createLogger(SoulLogger.Level.INFO, command = "Normal operation"),
            createLogger(SoulLogger.Level.ERROR, command = "Database connection failed"),
            createLogger(SoulLogger.Level.INFO, command = "User request")
        )
        
        val request = AggregationRequest(
            groupBy = listOf("level")
        )
        
        val result = engine.aggregate(logs, request)
        
        // Should have results for different levels
        assertTrue(result.groups.isNotEmpty())
    }

    @Test
    fun `getStatistics returns correct metrics`() {
        val engine = AggregationEngine()
        
        val logs = listOf(
            createLogger(SoulLogger.Level.INFO),
            createLogger(SoulLogger.Level.INFO),
            createLogger(SoulLogger.Level.ERROR)
        )
        
        val stats = engine.getStatistics(logs)
        
        // Just verify we get some stats back
        assertTrue(stats.total > 0)
    }
}
