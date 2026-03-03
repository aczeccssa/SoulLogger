package com.lestere.opensource.config

import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.SoulLoggerPluginConfiguration
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DynamicConfigManagerTest {
    private lateinit var config: SoulLoggerPluginConfiguration
    private lateinit var manager: DynamicConfigManager

    @BeforeEach
    fun setup() {
        config = SoulLoggerPluginConfiguration()
        manager = DynamicConfigManager(config)
    }

    @Test
    fun `getCurrentConfig returns correct initial values`() {
        val current = manager.getCurrentConfig()
        assertEquals(config.level.name, current.level)
        assertEquals(config.queueCapacity, current.queueCapacity)
        assertEquals(config.maxFileSize, current.maxFileSize)
        assertEquals(config.enableConsole, current.enableConsole)
        assertEquals(config.enableFile, current.enableFile)
        assertEquals(config.enableMasking, current.enableMasking)
        assertEquals(config.enableLogback, current.enableLogback)
        assertEquals(config.outputFormat.name, current.outputFormat)
    }

    @Test
    fun `updateLevel changes level and notifies observer`() = runTest {
        val observer = mockk<ConfigObserver>(relaxed = true)
        manager.addObserver(observer)

        val result = manager.updateLevel(SoulLogger.Level.DEBUG)

        assertTrue(result)
        assertEquals(SoulLogger.Level.DEBUG, config.level)

        advanceUntilIdle()

        verify {
            observer.onConfigChanged(match {
                it.key == "level" && it.oldValue == SoulLogger.Level.INFO && it.newValue == SoulLogger.Level.DEBUG
            })
        }
    }

    @Test
    fun `updateLevel with same value returns false and does not notify`() = runTest {
        val observer = mockk<ConfigObserver>(relaxed = true)
        manager.addObserver(observer)

        val result = manager.updateLevel(SoulLogger.Level.INFO)

        assertFalse(result)

        advanceUntilIdle()
        verify(exactly = 0) { observer.onConfigChanged(any()) }
    }

    @Test
    fun `updateQueueCapacity changes capacity and notifies observer`() = runTest {
        val observer = mockk<ConfigObserver>(relaxed = true)
        manager.addObserver(observer)
        val oldCapacity = config.queueCapacity
        val newCapacity = oldCapacity + 1000

        val result = manager.updateQueueCapacity(newCapacity)

        assertTrue(result)
        assertEquals(newCapacity, config.queueCapacity)

        advanceUntilIdle()
        verify {
            observer.onConfigChanged(match {
                it.key == "queueCapacity" && it.oldValue == oldCapacity && it.newValue == newCapacity
            })
        }
    }

    @Test
    fun `updateMaxFileSize changes size and notifies observer`() = runTest {
        val observer = mockk<ConfigObserver>(relaxed = true)
        manager.addObserver(observer)
        val oldSize = config.maxFileSize
        val newSize = oldSize * 2

        val result = manager.updateMaxFileSize(newSize)

        assertTrue(result)
        assertEquals(newSize, config.maxFileSize)

        advanceUntilIdle()
        verify {
            observer.onConfigChanged(match {
                it.key == "maxFileSize" && it.oldValue == oldSize && it.newValue == newSize
            })
        }
    }

    @Test
    fun `updateEnableConsole changes value and notifies observer`() = runTest {
        val observer = mockk<ConfigObserver>(relaxed = true)
        manager.addObserver(observer)
        val oldVal = config.enableConsole
        val newVal = !oldVal

        val result = manager.updateEnableConsole(newVal)

        assertTrue(result)
        assertEquals(newVal, config.enableConsole)

        advanceUntilIdle()
        verify {
            observer.onConfigChanged(match {
                it.key == "enableConsole" && it.oldValue == oldVal && it.newValue == newVal
            })
        }
    }

    @Test
    fun `updateEnableFile changes value and notifies observer`() = runTest {
        val observer = mockk<ConfigObserver>(relaxed = true)
        manager.addObserver(observer)
        val oldVal = config.enableFile
        val newVal = !oldVal

        val result = manager.updateEnableFile(newVal)

        assertTrue(result)
        assertEquals(newVal, config.enableFile)

        advanceUntilIdle()
        verify {
            observer.onConfigChanged(match {
                it.key == "enableFile" && it.oldValue == oldVal && it.newValue == newVal
            })
        }
    }

    @Test
    fun `updateEnableMasking changes value and notifies observer`() = runTest {
        val observer = mockk<ConfigObserver>(relaxed = true)
        manager.addObserver(observer)
        val oldVal = config.enableMasking
        val newVal = !oldVal

        val result = manager.updateEnableMasking(newVal)

        assertTrue(result)
        assertEquals(newVal, config.enableMasking)

        advanceUntilIdle()
        verify {
            observer.onConfigChanged(match {
                it.key == "enableMasking" && it.oldValue == oldVal && it.newValue == newVal
            })
        }
    }

    @Test
    fun `updateQueueCapacity with same value returns false and does not notify`() = runTest {
        val observer = mockk<ConfigObserver>(relaxed = true)
        manager.addObserver(observer)
        val currentCapacity = config.queueCapacity

        val result = manager.updateQueueCapacity(currentCapacity)

        assertFalse(result)

        advanceUntilIdle()
        verify(exactly = 0) { observer.onConfigChanged(any()) }
    }

    @Test
    fun `updateMaxFileSize with same value returns false and does not notify`() = runTest {
        val observer = mockk<ConfigObserver>(relaxed = true)
        manager.addObserver(observer)
        val currentSize = config.maxFileSize

        val result = manager.updateMaxFileSize(currentSize)

        assertFalse(result)

        advanceUntilIdle()
        verify(exactly = 0) { observer.onConfigChanged(any()) }
    }

    @Test
    fun `updateEnableConsole with same value returns false and does not notify`() = runTest {
        val observer = mockk<ConfigObserver>(relaxed = true)
        manager.addObserver(observer)
        val currentVal = config.enableConsole

        val result = manager.updateEnableConsole(currentVal)

        assertFalse(result)

        advanceUntilIdle()
        verify(exactly = 0) { observer.onConfigChanged(any()) }
    }

    @Test
    fun `updateEnableFile with same value returns false and does not notify`() = runTest {
        val observer = mockk<ConfigObserver>(relaxed = true)
        manager.addObserver(observer)
        val currentVal = config.enableFile

        val result = manager.updateEnableFile(currentVal)

        assertFalse(result)

        advanceUntilIdle()
        verify(exactly = 0) { observer.onConfigChanged(any()) }
    }

    @Test
    fun `updateEnableMasking with same value returns false and does not notify`() = runTest {
        val observer = mockk<ConfigObserver>(relaxed = true)
        manager.addObserver(observer)
        val currentVal = config.enableMasking

        val result = manager.updateEnableMasking(currentVal)

        assertFalse(result)

        advanceUntilIdle()
        verify(exactly = 0) { observer.onConfigChanged(any()) }
    }

    @Test
    fun `removeObserver stops notifications`() = runTest {
        val observer = mockk<ConfigObserver>(relaxed = true)
        manager.addObserver(observer)
        manager.removeObserver(observer)

        manager.updateLevel(SoulLogger.Level.DEBUG)

        advanceUntilIdle()
        verify(exactly = 0) { observer.onConfigChanged(any()) }
    }

    @Test
    fun `shutdown cancels scope and prevents further notifications`() = runTest {
        val observer = mockk<ConfigObserver>(relaxed = true)
        manager.addObserver(observer)

        manager.shutdown()

        val result = manager.updateLevel(SoulLogger.Level.DEBUG)

        assertTrue(result)
        assertEquals(SoulLogger.Level.DEBUG, config.level)

        advanceUntilIdle()
        verify(exactly = 0) { observer.onConfigChanged(any()) }
    }

    @Test
    fun `failing observer does not prevent other observers from being notified`() = runTest {
        val failingObserver = mockk<ConfigObserver>()
        val workingObserver = mockk<ConfigObserver>(relaxed = true)
        every { failingObserver.onConfigChanged(any()) } throws RuntimeException("Observer failed!")

        manager.addObserver(failingObserver)
        manager.addObserver(workingObserver)

        manager.updateLevel(SoulLogger.Level.DEBUG)

        advanceUntilIdle()

        verify { failingObserver.onConfigChanged(any()) }
        verify { workingObserver.onConfigChanged(any()) }
    }
}
