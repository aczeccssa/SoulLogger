package com.lestere.opensource.performance

import com.lestere.opensource.rotation.RotationPolicy
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.math.min

class MmapWriter(
    private val filePath: Path,
    private val maxFileSize: Long = 1024L * 1024L * 1024L
) {
    private var raf: java.io.RandomAccessFile? = null
    private var channel: FileChannel? = null
    private var buffer: MappedByteBuffer? = null
    
    @Volatile private var writePosition = 0L
    @Volatile private var isClosed = false
    
    init {
        initialize()
    }
    
    private fun initialize() {
        try {
            Files.createDirectories(filePath.parent)
            raf = java.io.RandomAccessFile(filePath.toFile(), "rw")
            channel = raf!!.channel
            buffer = channel!!.map(MapMode.READ_WRITE, 0, maxFileSize)
        } catch (e: Exception) {
            throw MmapWriterException("Failed to initialize MmapWriter: ${e.message}", e)
        }
    }
    
    fun write(data: String): Long {
        return write(data, StandardCharsets.UTF_8)
    }
    
    fun write(data: String, charset: java.nio.charset.Charset): Long {
        if (isClosed) throw MmapWriterException("Writer is closed")
        
        val bytes = charset.encode(data)
        val length = bytes.remaining()
        
        val pos = writePosition
        if (pos + length > maxFileSize) {
            throw MmapWriterException("Data size exceeds file capacity")
        }
        
        val actualPos = (pos % maxFileSize).toInt()
        
        if (actualPos + length <= maxFileSize) {
            buffer!!.position(actualPos)
            buffer!!.put(bytes)
        } else {
            // 跨边界写入
            val part1 = (maxFileSize - actualPos).toInt()
            buffer!!.position(actualPos)
            bytes.limit(part1)
            buffer!!.put(bytes)
            bytes.position(0)
            bytes.limit(length)
            buffer!!.position(0)
            buffer!!.put(bytes)
        }
        
        writePosition += length
        return pos
    }
    
    fun writeBatch(dataList: List<String>): Long {
        var total = 0L
        for (data in dataList) {
            total += write(data)
        }
        return total
    }
    
    fun force() {
        buffer?.force()
    }
    
    fun size(): Long = writePosition
    
    fun canWrite(additionalSize: Int): Boolean {
        return writePosition + additionalSize <= maxFileSize
    }
    
    fun close() {
        isClosed = true
        try {
            buffer?.force()
            channel?.close()
            raf?.close()
        } catch (e: Exception) {
            // ignore
        }
    }
}

class MmapRotationWriter(
    private val directory: Path,
    private val maxFileSize: Long = 1024L * 1024L * 1024L,
    private val rotationPolicy: String = "SIZE",
    private val filePrefix: String = "app"
) {
    private var currentWriter: MmapWriter? = null
    private var currentIndex = 0
    private var currentFilePath: Path? = null
    
    init {
        rotate()
    }
    
    fun write(data: String): Long {
        val writer = currentWriter ?: throw MmapWriterException("Writer not initialized")
        
        if (!writer.canWrite(data.toByteArray().size)) {
            rotate()
        }
        
        return currentWriter!!.write(data)
    }
    
    fun writeBatch(dataList: List<String>): Long {
        var total = 0L
        for (data in dataList) {
            total += write(data)
        }
        return total
    }
    
    private fun rotate() {
        currentWriter?.close()
        
        currentIndex++
        val timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        currentFilePath = directory.resolve("${filePrefix}-${timestamp}.log")
        
        try {
            Files.createDirectories(directory)
            currentWriter = MmapWriter(currentFilePath!!, maxFileSize)
        } catch (e: Exception) {
            throw MmapWriterException("Failed to create new log file: ${e.message}", e)
        }
    }
    
    fun force() {
        currentWriter?.force()
    }
    
    fun size(): Long = currentWriter?.size() ?: 0L
    
    fun getCurrentFilePath(): Path? = currentFilePath
    
    fun close() {
        currentWriter?.close()
    }
}

class MmapWriterException(message: String, cause: Throwable? = null) : Exception(message, cause)
