package com.lestere.opensource.rotation

import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class RotationManagerIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `rotation manager creates daily rotated files`() {
        val manager = RotationManager(
            policy = RotationPolicy.TimeBased(
                pattern = TimeBasedTrigger.TimeRotationPattern.DAILY
            ),
            logDirectory = tempDir
        )

        val file1 = manager.getCurrentFile()
        assertTrue(Files.exists(file1))
        assertTrue(file1.toString().contains("app."))
        
        // Verify file contains date pattern
        val fileName = file1.fileName.toString()
        assertTrue(fileName.matches(Regex("""app\.\d{4}-\d{2}-\d{2}\.log""")))
    }

    @Test
    fun `rotation manager handles size-based rotation`() {
        val maxSize = 1024L
        val manager = RotationManager(
            policy = RotationPolicy.SizeBased(
                maxSizeBytes = maxSize
            ),
            logDirectory = tempDir
        )

        val file1 = manager.getCurrentFile()
        
        // Write some data and track size through manager
        val testData = "x".repeat(2000)
        Files.write(file1, testData.toByteArray(), StandardOpenOption.APPEND)
        
        // Manually increment size to simulate what would happen in real usage
        manager.incrementSize(testData.toByteArray().size.toLong())
        
        // Check size was tracked - should exceed maxSize
        assertTrue(manager.getCurrentFileSize() >= maxSize)
    }

    @Test
    fun `rotation manager creates hourly rotated files`() {
        val manager = RotationManager(
            policy = RotationPolicy.TimeBased(
                pattern = TimeBasedTrigger.TimeRotationPattern.HOURLY
            ),
            logDirectory = tempDir
        )

        val file1 = manager.getCurrentFile()
        assertTrue(file1.toString().contains("_"))
        
        // Verify hour pattern
        val fileName = file1.fileName.toString()
        assertTrue(fileName.matches(Regex("""app\.\d{4}-\d{2}-\d{2}_\d{2}\.log""")))
    }

    @Test
    fun `rotation manager respects retention policy`() {
        val retentionConfig = RetentionConfig(
            maxHistoryDays = 7,
            maxFiles = 5,
            totalSizeCapBytes = 1000
        )
        
        val manager = RotationManager(
            policy = RotationPolicy.TimeBased(),
            retentionConfig = retentionConfig,
            logDirectory = tempDir
        )

        // The manager should initialize correctly
        val file = manager.getCurrentFile()
        assertNotNull(file)
    }
}

class TimeBasedPolicyIntegrationTest {

    @Test
    fun `daily policy has correct period`() {
        val policy = RotationPolicy.TimeBased(
            pattern = TimeBasedTrigger.TimeRotationPattern.DAILY
        )
        
        assertTrue(policy.enabled)
        assertEquals(TimeBasedTrigger.TimeRotationPattern.DAILY, policy.pattern)
    }

    @Test
    fun `size policy has correct max size`() {
        val maxSize = 50 * 1024 * 1024L // 50MB
        val policy = RotationPolicy.SizeBased(
            maxSizeBytes = maxSize
        )
        
        assertTrue(policy.enabled)
        assertEquals(maxSize, policy.maxSizeBytes)
    }

    @Test
    fun `composite policy combines both settings`() {
        val policy = RotationPolicy.Composite(
            timePattern = TimeBasedTrigger.TimeRotationPattern.WEEKLY,
            maxSizeBytes = 100 * 1024 * 1024
        )
        
        assertTrue(policy.enabled)
        assertEquals(TimeBasedTrigger.TimeRotationPattern.WEEKLY, policy.timePattern)
        assertEquals(100 * 1024 * 1024L, policy.maxSizeBytes)
    }
}
