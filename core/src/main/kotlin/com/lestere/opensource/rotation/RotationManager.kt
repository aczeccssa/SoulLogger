package com.lestere.opensource.rotation

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.format
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPOutputStream
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

sealed class RotationPolicy {
    abstract val enabled: Boolean
    
    data class TimeBased(
        override val enabled: Boolean = true,
        val pattern: TimeBasedTrigger.TimeRotationPattern = TimeBasedTrigger.TimeRotationPattern.DAILY
    ) : RotationPolicy()
    
    data class SizeBased(
        override val enabled: Boolean = true,
        val maxSizeBytes: Long = 100 * 1024 * 1024 // 100MB
    ) : RotationPolicy()
    
    data class Composite(
        override val enabled: Boolean = true,
        val timePattern: TimeBasedTrigger.TimeRotationPattern = TimeBasedTrigger.TimeRotationPattern.DAILY,
        val maxSizeBytes: Long = 100 * 1024 * 1024
    ) : RotationPolicy()
}

data class RetentionConfig(
    val maxHistoryDays: Int = 30,
    val maxFiles: Int = 100,
    val totalSizeCapBytes: Long = 10L * 1024 * 1024 * 1024 // 10GB
)

data class CompressionConfig(
    val enabled: Boolean = true,
    val compressionLevel: Int = 6, // 0-9
    val compressExtensions: List<String> = listOf(".log")
)

class RotationManager(
    private val policy: RotationPolicy,
    private val retentionConfig: RetentionConfig = RetentionConfig(),
    private val compressionConfig: CompressionConfig = CompressionConfig(),
    private val logDirectory: Path
) {
    private var currentFile: Path? = null
    private var currentFileStartTime: Instant = Clock.System.now()
    private var currentFileSize: Long = 0
    
    sealed class RotationResult {
        data class Rotated(val previousPath: Path, val newPath: Path) : RotationResult()
        data class Compressed(val originalPath: Path, val compressedPath: Path) : RotationResult()
        data object NoRotation : RotationResult()
        data class Error(val message: String) : RotationResult()
    }
    
    fun getCurrentFile(): Path {
        if (currentFile == null || shouldRotate()) {
            val result = rotate()
            if (result is RotationResult.Rotated) {
                currentFile = result.newPath
            }
        }
        return currentFile ?: createNewFile()
    }
    
    fun getCurrentFileSize(): Long = currentFileSize
    
    fun getLastRotationTime(): Instant = currentFileStartTime
    
    private fun shouldRotate(): Boolean {
        val file = currentFile ?: return true
        
        return when (policy) {
            is RotationPolicy.TimeBased -> {
                val trigger = TimeBasedTrigger(policy.pattern)
                trigger.shouldRotate(file, currentFileSize, currentFileStartTime)
            }
            is RotationPolicy.SizeBased -> {
                currentFileSize >= policy.maxSizeBytes
            }
            is RotationPolicy.Composite -> {
                val timeTrigger = TimeBasedTrigger(policy.timePattern)
                val sizeTrigger = SizeBasedTrigger(policy.maxSizeBytes)
                timeTrigger.shouldRotate(file, currentFileSize, currentFileStartTime) ||
                sizeTrigger.shouldRotate(file, currentFileSize, currentFileStartTime)
            }
        }
    }
    
    private fun rotate(): RotationResult {
        val previousFile = currentFile
        val newFile = createNewFile()
        
        if (previousFile != null && Files.exists(previousFile)) {
            // Compress if enabled
            if (compressionConfig.enabled && previousFile.name.endsWith(".log")) {
                compressFile(previousFile)
            }
            
            // Apply retention policy
            applyRetention()
        }
        
        currentFile = newFile
        currentFileStartTime = Clock.System.now()
        currentFileSize = 0
        
        return if (previousFile != null) {
            RotationResult.Rotated(previousFile, newFile)
        } else {
            RotationResult.NoRotation
        }
    }
    
    private fun createNewFile(): Path {
        val timestamp = getTimestampForFile()
        val fileName = "app.$timestamp.log"
        val filePath = logDirectory.resolve(fileName)
        
        Files.createDirectories(filePath.parent)
        if (!Files.exists(filePath)) {
            Files.createFile(filePath)
        }
        
        currentFileSize = 0
        return filePath
    }
    
    private fun getTimestampForFile(): String {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        
        return when (policy) {
            is RotationPolicy.TimeBased -> {
                val pattern = policy.pattern
                formatTimestamp(now, pattern)
            }
            is RotationPolicy.Composite -> {
                val pattern = policy.timePattern
                formatTimestamp(now, pattern)
            }
            else -> {
                formatTimestamp(now, TimeBasedTrigger.TimeRotationPattern.DAILY)
            }
        }
    }
    
    private fun formatTimestamp(instant: Instant, pattern: TimeBasedTrigger.TimeRotationPattern): String {
        val timestampStr = instant.toString()
        val year = timestampStr.substring(0, 4)
        val month = timestampStr.substring(5, 7)
        val day = timestampStr.substring(8, 10)
        val hour = timestampStr.substring(11, 13)
        
        return when (pattern) {
            TimeBasedTrigger.TimeRotationPattern.HOURLY -> {
                "$year-$month-${day}_$hour"
            }
            TimeBasedTrigger.TimeRotationPattern.DAILY -> {
                "$year-$month-$day"
            }
            TimeBasedTrigger.TimeRotationPattern.WEEKLY -> {
                "$year-W${((day.toInt() / 7) + 1).toString().padStart(2, '0')}"
            }
            TimeBasedTrigger.TimeRotationPattern.MONTHLY -> {
                "$year-$month"
            }
        }
    }
    
    private fun compressFile(file: Path): RotationResult? {
        return try {
            val compressedPath = Paths.get("$file.gz")
            Files.move(file, compressedPath, StandardCopyOption.ATOMIC_MOVE)
            RotationResult.Compressed(file, compressedPath)
        } catch (e: Exception) {
            RotationResult.Error("Failed to compress file: ${e.message}")
        }
    }
    
    private fun applyRetention() {
        try {
            val fileList = Files.list(logDirectory)
                .filter { Files.isRegularFile(it) }
                .toList()
            
            val sortedFiles = fileList.sortedBy { Files.getLastModifiedTime(it).toMillis() }
            
            // Remove old files by count
            if (sortedFiles.size > retentionConfig.maxFiles) {
                sortedFiles.take(sortedFiles.size - retentionConfig.maxFiles).forEach { file ->
                    Files.deleteIfExists(file)
                }
            }
            
            // Remove old files by age
            val now = Clock.System.now().toEpochMilliseconds()
            val maxAgeMs = retentionConfig.maxHistoryDays * 24 * 3600 * 1000L
            sortedFiles.forEach { file ->
                val age = now - Files.getLastModifiedTime(file).toMillis()
                if (age > maxAgeMs) {
                    Files.deleteIfExists(file)
                }
            }
            
            // Check total size
            var totalSize = sortedFiles.sumOf { Files.size(it) }
            while (totalSize > retentionConfig.totalSizeCapBytes && sortedFiles.isNotEmpty()) {
                val oldestFile = sortedFiles.firstOrNull() ?: break
                val size = Files.size(oldestFile)
                Files.deleteIfExists(oldestFile)
                totalSize -= size
            }
        } catch (e: Exception) {
            // Log retention error but don't fail
        }
    }
    
    fun incrementSize(bytes: Long) {
        currentFileSize += bytes
    }
}

private fun TimeBasedTrigger.shouldRotate(file: Path, currentSize: Long, lastRotation: Instant): Boolean {
    return shouldTrigger(file, currentSize, lastRotation)
}

private fun SizeBasedTrigger.shouldRotate(file: Path, currentSize: Long, lastRotation: Instant): Boolean {
    return shouldTrigger(file, currentSize, lastRotation)
}
