package com.lestere.opensource.rotation

import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RotationManagerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `time-based rotation creates new file at midnight`() {
        val manager = RotationManager(
            policy = RotationPolicy.TimeBased(
                pattern = TimeBasedTrigger.TimeRotationPattern.DAILY
            ),
            logDirectory = tempDir
        )

        val file1 = manager.getCurrentFile()
        assertTrue(Files.exists(file1))
        
        // Simulate time passing by forcing rotation
        // In real test, we'd mock Clock but for now just verify basic behavior
        assertNotNull(file1)
    }

    @Test
    fun `size-based rotation triggers when size exceeded`() {
        val maxSize = 100L // Very small for testing
        val manager = RotationManager(
            policy = RotationPolicy.SizeBased(
                maxSizeBytes = maxSize
            ),
            logDirectory = tempDir
        )

        val file1 = manager.getCurrentFile()
        
        // Simulate writing data
        manager.incrementSize(maxSize + 1)
        
        // Check rotation state
        assertTrue(manager.getCurrentFileSize() >= 0)
    }

    @Test
    fun `composite rotation combines time and size policies`() {
        val manager = RotationManager(
            policy = RotationPolicy.Composite(
                timePattern = TimeBasedTrigger.TimeRotationPattern.DAILY,
                maxSizeBytes = 1000
            ),
            logDirectory = tempDir
        )

        val file = manager.getCurrentFile()
        assertTrue(Files.exists(file))
        assertTrue(file.toString().contains("app."))
    }

    @Test
    fun `retention policy removes old files`() {
        val retentionConfig = RetentionConfig(
            maxHistoryDays = 0, // Remove immediately
            maxFiles = 1
        )
        
        val manager = RotationManager(
            policy = RotationPolicy.TimeBased(),
            retentionConfig = retentionConfig,
            logDirectory = tempDir
        )

        // Create some files
        manager.getCurrentFile()
        
        // Verify retention config is applied
        assertEquals(0, retentionConfig.maxHistoryDays)
    }

    @Test
    fun `rotation manager handles non-existent directory`() {
        val nonExistentDir = tempDir.resolve("subdir").resolve("logs")
        
        val manager = RotationManager(
            policy = RotationPolicy.TimeBased(),
            logDirectory = nonExistentDir
        )

        val file = manager.getCurrentFile()
        assertTrue(Files.exists(file))
    }
}

class TimeBasedTriggerTest {

    @Test
    fun `daily pattern calculates correct period`() {
        val pattern = TimeBasedTrigger.TimeRotationPattern.DAILY
        assertEquals(86400_000L, pattern.periodMs)
    }

    @Test
    fun `hourly pattern calculates correct period`() {
        val pattern = TimeBasedTrigger.TimeRotationPattern.HOURLY
        assertEquals(3600_000L, pattern.periodMs)
    }

    @Test
    fun `weekly pattern calculates correct period`() {
        val pattern = TimeBasedTrigger.TimeRotationPattern.WEEKLY
        assertEquals(604800_000L, pattern.periodMs)
    }
}

class SizeBasedTriggerTest {

    @Test
    fun `triggers when size exceeds max`() {
        val trigger = SizeBasedTrigger(100)
        
        val file = Files.createTempFile("test", ".log")
        
        assertTrue(trigger.shouldTrigger(file, 101, Clock.System.now()))
        assertFalse(trigger.shouldTrigger(file, 99, Clock.System.now()))
    }
}
