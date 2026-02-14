package com.lestere.opensource.performance

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class LogBuffer(
    private val filePath: Path,
    private val bufferSize: Int = 8 * 1024,
    private val flushInterval: Long = 1000,
    private val autoFlush: Boolean = false
) {
    private var channel: FileChannel? = null
    private var buffer: ByteBuffer? = null
    private var lastFlushTime = System.currentTimeMillis()
    private val mutex = Mutex()
    
    private var isClosed = false
    
    init {
        initialize()
    }
    
    private fun initialize() {
        try {
            channel = FileChannel.open(
                filePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE
            )
            buffer = ByteBuffer.allocateDirect(bufferSize)
        } catch (e: Exception) {
            throw LogBufferException("Failed to initialize buffer: ${e.message}", e)
        }
    }
    
    suspend fun write(data: String): Int {
        return write(data, StandardCharsets.UTF_8)
    }
    
    suspend fun write(data: String, charset: java.nio.charset.Charset): Int {
        if (isClosed) throw LogBufferException("Buffer is closed")
        
        mutex.withLock {
            val bytes = data.toByteArray(charset)
            val totalWritten = 0
            
            var remaining = bytes.size
            var offset = 0
            
            while (remaining > 0) {
                val available = buffer!!.capacity() - buffer!!.position()
                
                if (available < remaining) {
                    // Buffer is full, flush first
                    flushInternal()
                }
                
                val toWrite = minOf(remaining, buffer!!.capacity() - buffer!!.position())
                buffer!!.put(bytes, offset, toWrite)
                offset += toWrite
                remaining -= toWrite
            }
            
            // Check if we should auto-flush
            if (autoFlush || System.currentTimeMillis() - lastFlushTime >= flushInterval) {
                flushInternal()
            }
            
            return bytes.size
        }
    }
    
    suspend fun writeBatch(lines: List<String>): Int {
        if (isClosed) throw LogBufferException("Buffer is closed")
        
        return mutex.withLock {
            var totalWritten = 0
            for (line in lines) {
                val bytes = (line + "\n").toByteArray(StandardCharsets.UTF_8)
                
                if (buffer!!.capacity() - buffer!!.position() < bytes.size) {
                    flushInternal()
                }
                
                buffer!!.put(bytes)
                totalWritten += bytes.size
            }
            
            if (autoFlush || System.currentTimeMillis() - lastFlushTime >= flushInterval) {
                flushInternal()
            }
            
            totalWritten
        }
    }
    
    suspend fun flush() {
        mutex.withLock {
            flushInternal()
        }
    }
    
    private fun flushInternal() {
        if (buffer!!.position() > 0) {
            buffer!!.flip()
            channel!!.write(buffer!!)
            buffer!!.clear()
            lastFlushTime = System.currentTimeMillis()
        }
    }
    
    suspend fun close() {
        mutex.withLock {
            flushInternal()
            channel?.close()
            channel = null
            buffer = null
            isClosed = true
        }
    }
    
    fun isOpen(): Boolean = !isClosed
    
    fun size(): Int = buffer?.position() ?: 0
}

class LogBufferException(message: String, cause: Throwable? = null) : Exception(message, cause)

enum class BackpressureStrategy {
    SUSPEND,
    DROP,
    DROP_OLDEST,
    LATEST,
    BLOCK
}

class BackpressureController(
    private val strategy: BackpressureStrategy = BackpressureStrategy.SUSPEND,
    private val highWatermark: Int = 8000,
    private val lowWatermark: Int = 2000,
    private var currentSize: Int = 0
) {
    @Volatile
    private var enabled = true
    
    fun recordQueueSize(size: Int) {
        currentSize = size
    }
    
    fun shouldDrop(): Boolean {
        if (!enabled) return false
        return when (strategy) {
            BackpressureStrategy.DROP, BackpressureStrategy.DROP_OLDEST -> currentSize >= highWatermark
            else -> false
        }
    }
    
    fun shouldResume(): Boolean {
        if (!enabled) return true
        return currentSize <= lowWatermark
    }
    
    fun shouldBlock(): Boolean {
        if (!enabled) return false
        return strategy == BackpressureStrategy.BLOCK && currentSize >= highWatermark
    }
    
    fun applyStrategy(): BackpressureAction {
        return when {
            currentSize >= highWatermark -> {
                when (strategy) {
                    BackpressureStrategy.DROP -> BackpressureAction.DROP_NEW
                    BackpressureStrategy.DROP_OLDEST -> BackpressureAction.DROP_OLDEST
                    BackpressureStrategy.SUSPEND -> BackpressureAction.SUSPEND
                    BackpressureStrategy.BLOCK -> BackpressureAction.BLOCK
                    BackpressureStrategy.LATEST -> BackpressureAction.KEEP_LATEST
                }
            }
            else -> BackpressureAction.NONE
        }
    }
    
    fun enable() { enabled = true }
    fun disable() { enabled = false }
    fun isEnabled(): Boolean = enabled
}

enum class BackpressureAction {
    NONE,
    DROP_NEW,
    DROP_OLDEST,
    SUSPEND,
    BLOCK,
    KEEP_LATEST
}
