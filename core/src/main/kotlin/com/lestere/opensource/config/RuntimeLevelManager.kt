package com.lestere.opensource.config

import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.SoulLogger.Level
import java.util.concurrent.ConcurrentHashMap

class RuntimeLevelManager {
    private val packageLevels = ConcurrentHashMap<String, Level>()
    
    @Volatile var globalLevel = Level.INFO
    
    private val cache = ConcurrentHashMap<String, Level>()
    
    fun setLevel(packagePattern: String, level: Level) {
        if (packagePattern.isBlank()) {
            globalLevel = level
            cache.clear()
            SoulLogger.info("Global log level set to: ${level.name}")
            return
        }
        
        val normalizedPattern = normalizePattern(packagePattern)
        packageLevels[normalizedPattern] = level
        cache.clear()
        SoulLogger.info("Log level set for $packagePattern: ${level.name}")
    }
    
    fun getLevel(entry: String): Level {
        cache[entry]?.let { return it }
        
        val level = resolveLevel(entry)
        cache[entry] = level
        return level
    }
    
    private fun resolveLevel(entry: String): Level {
        return packageLevels.entries
            .filter { entryMatches(it.key, entry) }
            .maxByOrNull { it.key.length }
            ?.value ?: globalLevel
    }
    
    private fun entryMatches(pattern: String, entry: String): Boolean {
        return when {
            pattern.endsWith(".*") -> entry.startsWith(pattern.removeSuffix(".*"))
            pattern.endsWith("**") -> entry.startsWith(pattern.removeSuffix("**"))
            pattern == "*" -> true
            else -> entry.startsWith(pattern)
        }
    }
    
    private fun normalizePattern(pattern: String): String {
        var normalized = pattern.trim()
        if (!normalized.endsWith(".*") && !normalized.endsWith("**") && normalized.contains(".")) {
            normalized = "$normalized.*"
        }
        return normalized
    }
    
    fun remove(packagePattern: String) {
        val normalized = normalizePattern(packagePattern)
        packageLevels.remove(normalized)
        cache.clear()
    }
    
    fun reset() {
        packageLevels.clear()
        cache.clear()
        globalLevel = Level.INFO
    }
    
    fun reset(pattern: String?) {
        if (pattern == null) {
            reset()
            return
        }
        
        val normalized = normalizePattern(pattern)
        packageLevels.remove(normalized)
        cache.clear()
    }
    
    fun getAllLevels(): Map<String, String> {
        return buildMap {
            put("global", globalLevel.name)
            for ((k, v) in packageLevels) {
                put(k, v.name)
            }
        }
    }
    
    fun getPackageLevels(): Map<String, Level> {
        return packageLevels.toMap()
    }
    
    fun getGlobalLevelValue(): Level = globalLevel
    
    fun clearCache() = cache.clear()
    
    fun size(): Int = packageLevels.size
}

class RuntimeLevelConfig(
    var enabled: Boolean = true,
    var defaultLevel: Level = Level.INFO,
    var allowRemoteUpdate: Boolean = true
)
