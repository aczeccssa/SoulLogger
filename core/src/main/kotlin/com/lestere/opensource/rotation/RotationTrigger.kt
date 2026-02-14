package com.lestere.opensource.rotation

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

sealed class RotationTrigger {
    abstract fun shouldTrigger(currentFile: Path, currentSize: Long, lastRotation: Instant): Boolean
    
    data object Never : RotationTrigger() {
        override fun shouldTrigger(currentFile: Path, currentSize: Long, lastRotation: Instant): Boolean = false
    }
}

class TimeBasedTrigger(
    private val pattern: TimeRotationPattern
) : RotationTrigger() {
    
    enum class TimeRotationPattern(val pattern: String, val periodMs: Long) {
        HOURLY("yyyy-MM-dd_HH", 3600_000L),
        DAILY("yyyy-MM-dd", 86400_000L),
        WEEKLY("yyyy-'W'ww", 604800_000L),
        MONTHLY("yyyy-MM", 2592000_000L);
        
        companion object {
            fun fromString(value: String): TimeRotationPattern {
                return entries.find { it.name.equals(value, ignoreCase = true) }
                    ?: DAILY
            }
        }
    }
    
    private var lastRotationTime: Instant = Clock.System.now()
    
    override fun shouldTrigger(currentFile: Path, currentSize: Long, lastRotation: Instant): Boolean {
        val now = Clock.System.now()
        val elapsed = now.toEpochMilliseconds() - lastRotation.toEpochMilliseconds()
        return elapsed >= pattern.periodMs
    }
    
    fun getNextRotationTime(): Instant {
        val now = Clock.System.now()
        val elapsed = now.toEpochMilliseconds() % pattern.periodMs
        return Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + pattern.periodMs - elapsed)
    }
}

class SizeBasedTrigger(
    private val maxSize: Long
) : RotationTrigger() {
    
    override fun shouldTrigger(currentFile: Path, currentSize: Long, lastRotation: Instant): Boolean {
        return currentSize >= maxSize
    }
}

class CompositeTrigger(
    private val triggers: List<RotationTrigger>
) : RotationTrigger() {
    
    constructor(vararg triggers: RotationTrigger) : this(triggers.toList())
    
    override fun shouldTrigger(currentFile: Path, currentSize: Long, lastRotation: Instant): Boolean {
        return triggers.any { it.shouldTrigger(currentFile, currentSize, lastRotation) }
    }
}
