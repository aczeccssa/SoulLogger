package com.lestere.opensource.performance

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LogBufferTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `buffer initializes correctly`() {
        val file = tempDir.resolve("test.log")
        val buffer = LogBuffer(
            filePath = file,
            bufferSize = 1024,
            flushInterval = 1000,
            autoFlush = false
        )

        assertTrue(buffer.isOpen())
        runBlocking { buffer.close() }
    }

    @Test
    fun `buffer writes data correctly`() = runBlocking {
        val file = tempDir.resolve("test.log")
        val buffer = LogBuffer(
            filePath = file,
            bufferSize = 1024,
            flushInterval = 1000,
            autoFlush = false
        )

        val testData = "Test log message"
        val written = buffer.write(testData)

        assertTrue(written > 0)
        
        buffer.close()
    }

    @Test
    fun `buffer flushes data to file`() = runBlocking {
        val file = tempDir.resolve("test.log")
        val buffer = LogBuffer(
            filePath = file,
            bufferSize = 1024,
            flushInterval = 100,
            autoFlush = false
        )

        buffer.write("Test message 1\n")
        buffer.write("Test message 2\n")
        buffer.flush()

        assertTrue(Files.exists(file))
        val content = Files.readString(file)
        assertTrue(content.contains("Test message 1"))
        assertTrue(content.contains("Test message 2"))

        buffer.close()
    }

    @Test
    fun `buffer auto-flushes after interval`() = runBlocking {
        val file = tempDir.resolve("test.log")
        val buffer = LogBuffer(
            filePath = file,
            bufferSize = 1024,
            flushInterval = 50, // Very short for testing
            autoFlush = true
        )

        buffer.write("Auto flushed message\n")
        
        // Wait for auto-flush
        kotlinx.coroutines.delay(100)

        assertTrue(Files.exists(file))
        val content = Files.readString(file)
        assertTrue(content.contains("Auto flushed message"))

        buffer.close()
    }

    @Test
    fun `buffer batch write works correctly`() = runBlocking {
        val file = tempDir.resolve("test.log")
        val buffer = LogBuffer(
            filePath = file,
            bufferSize = 4096,
            flushInterval = 1000,
            autoFlush = false
        )

        val lines = listOf("Line 1", "Line 2", "Line 3")
        buffer.writeBatch(lines)
        buffer.flush()

        val content = Files.readString(file)
        assertTrue(content.contains("Line 1"))
        assertTrue(content.contains("Line 2"))
        assertTrue(content.contains("Line 3"))

        buffer.close()
    }

    @Test
    fun `buffer close releases resources`() = runBlocking {
        val file = tempDir.resolve("test.log")
        val buffer = LogBuffer(
            filePath = file,
            bufferSize = 1024,
            flushInterval = 1000,
            autoFlush = false
        )

        buffer.write("Test\n")
        buffer.close()

        assertFalse(buffer.isOpen())
    }
}

class BackpressureControllerTest {

    @Test
    fun `suspend strategy does not drop when below high watermark`() {
        val controller = BackpressureController(
            strategy = BackpressureStrategy.SUSPEND,
            highWatermark = 1000,
            lowWatermark = 200
        )

        controller.recordQueueSize(500)
        assertFalse(controller.shouldDrop())
    }

    @Test
    fun `drop strategy drops when above high watermark`() {
        val controller = BackpressureController(
            strategy = BackpressureStrategy.DROP,
            highWatermark = 1000,
            lowWatermark = 200
        )

        controller.recordQueueSize(1500)
        assertTrue(controller.shouldDrop())
    }

    @Test
    fun `resume when below low watermark`() {
        val controller = BackpressureController(
            strategy = BackpressureStrategy.DROP,
            highWatermark = 1000,
            lowWatermark = 200
        )

        // High
        controller.recordQueueSize(1500)
        assertTrue(controller.shouldDrop())

        // Below low - should resume
        controller.recordQueueSize(100)
        assertTrue(controller.shouldResume())
    }

    @Test
    fun `block strategy blocks when above high watermark`() {
        val controller = BackpressureController(
            strategy = BackpressureStrategy.BLOCK,
            highWatermark = 1000,
            lowWatermark = 200
        )

        controller.recordQueueSize(1500)
        assertTrue(controller.shouldBlock())
    }

    @Test
    fun `apply strategy returns correct action`() {
        val suspendController = BackpressureController(BackpressureStrategy.SUSPEND, 1000, 200)
        suspendController.recordQueueSize(1500)
        assertEquals(BackpressureAction.SUSPEND, suspendController.applyStrategy())

        val dropController = BackpressureController(BackpressureStrategy.DROP, 1000, 200)
        dropController.recordQueueSize(1500)
        assertEquals(BackpressureAction.DROP_NEW, dropController.applyStrategy())

        val dropOldestController = BackpressureController(BackpressureStrategy.DROP_OLDEST, 1000, 200)
        dropOldestController.recordQueueSize(1500)
        assertEquals(BackpressureAction.DROP_OLDEST, dropOldestController.applyStrategy())
    }

    @Test
    fun `enable and disable backpressure`() {
        val controller = BackpressureController(BackpressureStrategy.DROP, 1000, 200)
        
        controller.disable()
        controller.recordQueueSize(2000)
        assertFalse(controller.shouldDrop())
        
        controller.enable()
        controller.recordQueueSize(2000)
        assertTrue(controller.shouldDrop())
    }
}
