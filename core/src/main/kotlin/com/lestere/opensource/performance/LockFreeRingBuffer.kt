package com.lestere.opensource.performance

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class LockFreeRingBuffer(
    private val capacity: Int = 1024 * 1024
) {
    private val buffer = ByteBuffer.allocateDirect(capacity)
    private val writeIndex = AtomicLong(0)
    private val readIndex = AtomicLong(0)
    
    @Volatile private var isClosed = false
    
    init {
        buffer.limit(capacity)
    }
    
    fun write(data: ByteArray): Boolean {
        if (isClosed) return false
        
        val writePos = writeIndex.get()
        val readPos = readIndex.get()
        val available = calculateAvailable(writePos, readPos, capacity)
        
        if (data.size + HEADER_SIZE > available) return false
        
        val actualPos = ((writePos + HEADER_SIZE) % capacity).toInt()
        val remainingToEnd = capacity - actualPos
        
        // 写入长度头
        if (remainingToEnd >= HEADER_SIZE) {
            buffer.putInt(actualPos, data.size)
        } else {
            // 跨边界写入长度
            val part1 = remainingToEnd
            val part2 = HEADER_SIZE - part1
            buffer.position(0)
            buffer.putInt(data.size)
            buffer.position(actualPos)
            val lenBytes = ByteArray(HEADER_SIZE)
            buffer.get(0, lenBytes)
            buffer.position(actualPos)
            buffer.put(lenBytes, 0, min(part1, HEADER_SIZE))
            if (part2 > 0) {
                buffer.position(0)
                buffer.put(lenBytes, part1, part2)
            }
            buffer.position(0)
        }
        
        // 写入数据
        if (remainingToEnd >= data.size + HEADER_SIZE) {
            buffer.put(actualPos + HEADER_SIZE, data)
        } else {
            // 跨边界写入数据
            val dataPart1 = min(remainingToEnd - HEADER_SIZE, data.size)
            val dataPart2 = data.size - dataPart1
            
            if (dataPart1 > 0) {
                buffer.position(actualPos + HEADER_SIZE)
                buffer.put(data, 0, dataPart1)
            }
            if (dataPart2 > 0) {
                buffer.position(0)
                buffer.put(data, dataPart1, dataPart2)
            }
            buffer.position(0)
        }
        
        writeIndex.addAndGet(data.size.toLong() + HEADER_SIZE)
        return true
    }
    
    fun readBatch(): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var readPos = readIndex.get()
        val writePos = writeIndex.get()
        
        while (readPos < writePos) {
            val actualPos = (readPos % capacity).toInt()
            
            // 读取长度
            val length = if (actualPos + HEADER_SIZE <= capacity) {
                buffer.getInt(actualPos)
            } else {
                val lenBytes = ByteArray(HEADER_SIZE)
                val part1 = min(capacity - actualPos, HEADER_SIZE)
                buffer.position(actualPos)
                buffer.get(lenBytes, 0, part1)
                if (part1 < HEADER_SIZE) {
                    buffer.position(0)
                    buffer.get(lenBytes, part1, HEADER_SIZE - part1)
                }
                java.nio.ByteBuffer.wrap(lenBytes).int
            }
            
            if (length <= 0 || length > capacity - HEADER_SIZE) break
            
            // 读取数据
            val dataPos = (actualPos + HEADER_SIZE) % capacity
            val data = if (dataPos + length <= capacity) {
                val d = ByteArray(length)
                buffer.get(dataPos, d)
                d
            } else {
                val d = ByteArray(length)
                val part1 = min(capacity - dataPos, length)
                buffer.position(dataPos)
                buffer.get(d, 0, part1)
                if (part1 < length) {
                    buffer.position(0)
                    buffer.get(d, part1, length - part1)
                }
                buffer.position(0)
                d
            }
            
            result.add(data)
            readIndex.set(readPos + length + HEADER_SIZE)
            readPos = readIndex.get()
        }
        
        return result
    }
    
    fun size(): Int {
        val w = writeIndex.get()
        val r = readIndex.get()
        return ((w - r) / (capacity + HEADER_SIZE) * capacity + 
                ((w - r) % capacity)).toInt()
    }
    
    fun isEmpty(): Boolean = writeIndex.get() == readIndex.get()
    
    fun isFull(): Boolean {
        val w = writeIndex.get()
        val r = readIndex.get()
        return calculateAvailable(w, r, capacity) <= HEADER_SIZE
    }
    
    private fun calculateAvailable(write: Long, read: Long, cap: Int): Long {
        val diff = write - read
        return if (diff >= 0) cap - diff else -diff
    }
    
    fun close() {
        isClosed = true
    }
    
    companion object {
        private const val HEADER_SIZE = 4
    }
}
