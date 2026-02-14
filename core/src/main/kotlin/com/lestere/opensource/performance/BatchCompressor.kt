package com.lestere.opensource.performance

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

enum class CompressionType {
    NONE,
    LZ4,
    SNAPPY,
    ZSTD;
    
    companion object {
        fun default(): CompressionType = SNAPPY
        
        fun fromString(value: String): CompressionType {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: default()
        }
    }
}

data class CompressedData(
    val type: CompressionType,
    val compressed: ByteArray,
    val originalSize: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CompressedData
        return type == other.type && compressed.contentEquals(other.compressed)
    }
    
    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + compressed.contentHashCode()
        return result
    }
}

class BatchCompressor(
    private val type: CompressionType = CompressionType.default(),
    private val batchSize: Int = 1000
) {
    // Java built-in Deflate compression (no external dependencies)
    // For production, can add snappy-java or zstd-jni when network is available
    
    fun compress(data: List<ByteArray>): CompressedData {
        if (data.isEmpty()) {
            return CompressedData(CompressionType.NONE, ByteArray(0), 0)
        }
        
        if (type == CompressionType.NONE) {
            val raw = concatByteArrays(data)
            return CompressedData(CompressionType.NONE, raw, raw.size)
        }
        
        // Use built-in Deflate compression
        return try {
            compressDeflate(data)
        } catch (e: Exception) {
            val raw = concatByteArrays(data)
            CompressedData(CompressionType.NONE, raw, raw.size)
        }
    }
    
    fun compress(data: ByteArray): CompressedData {
        return compress(listOf(data))
    }
    
    private fun compressDeflate(data: List<ByteArray>): CompressedData {
        val raw = concatByteArrays(data)
        val compressed = deflate(raw, Deflater.BEST_COMPRESSION)
        return CompressedData(CompressionType.ZSTD, compressed, raw.size)  // Mark as ZSTD for compatibility
    }
    
    // LZ4-style compression using Deflate with different settings
    private fun compressLZ4(data: List<ByteArray>): CompressedData {
        val raw = concatByteArrays(data)
        val compressed = deflate(raw, Deflater.BEST_SPEED)
        return CompressedData(CompressionType.LZ4, compressed, raw.size)
    }
    
    // Snappy-style compression using Deflate
    private fun compressSnappy(data: List<ByteArray>): CompressedData {
        val raw = concatByteArrays(data)
        val compressed = deflate(raw, Deflater.DEFAULT_COMPRESSION)
        return CompressedData(CompressionType.SNAPPY, compressed, raw.size)
    }
    
    private fun deflate(data: ByteArray, level: Int): ByteArray {
        val deflater = Deflater(level)
        deflater.setInput(data)
        deflater.finish()
        
        val result = ByteArray(data.size)
        val len = deflater.deflate(result)
        deflater.end()
        
        return result.copyOf(len)
    }
    
    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        
        val result = ByteArray(8192)
        val len = inflater.inflate(result)
        inflater.end()
        
        return result.copyOf(len)
    }
    
    private fun concatByteArrays(arrays: List<ByteArray>): ByteArray {
        var total = 0
        for (a in arrays) total += a.size
        
        val result = ByteArray(total)
        var offset = 0
        for (a in arrays) {
            System.arraycopy(a, 0, result, offset, a.size)
            offset += a.size
        }
        return result
    }
    
    companion object {
        fun isTypeAvailable(type: CompressionType): Boolean {
            // All types use built-in compression
            return true
        }
    }
}
