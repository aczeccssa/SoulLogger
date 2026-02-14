package com.lestere.opensource.config

import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.SoulLogger.Level
import com.lestere.opensource.logger.SoulLoggerPluginConfiguration
import com.lestere.opensource.models.ConfigResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

interface ConfigObserver {
    fun onConfigChanged(change: ConfigChange)
}

data class ConfigChange(
    val key: String,
    val oldValue: Any?,
    val newValue: Any?,
    val timestamp: Long = System.currentTimeMillis()
)

class DynamicConfigManager(
    private val config: SoulLoggerPluginConfiguration
) {
    private val observers = CopyOnWriteArrayList<ConfigObserver>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun updateLevel(level: Level): Boolean {
        val old = config.level
        if (old == level) return false
        
        config.level = level
        notifyObservers(ConfigChange("level", old, level))
        SoulLogger.info("Log level changed: $old -> $level")
        return true
    }
    
    fun updateQueueCapacity(capacity: Int): Boolean {
        val old = config.queueCapacity
        if (old == capacity) return false
        
        config.queueCapacity = capacity
        notifyObservers(ConfigChange("queueCapacity", old, capacity))
        SoulLogger.info("Queue capacity changed: $old -> $capacity")
        return true
    }
    
    fun updateMaxFileSize(size: Long): Boolean {
        val old = config.maxFileSize
        if (old == size) return false
        
        config.maxFileSize = size
        notifyObservers(ConfigChange("maxFileSize", old, size))
        return true
    }
    
    fun updateEnableConsole(enabled: Boolean): Boolean {
        val old = config.enableConsole
        if (old == enabled) return false
        
        config.enableConsole = enabled
        notifyObservers(ConfigChange("enableConsole", old, enabled))
        return true
    }
    
    fun updateEnableFile(enabled: Boolean): Boolean {
        val old = config.enableFile
        if (old == enabled) return false
        
        config.enableFile = enabled
        notifyObservers(ConfigChange("enableFile", old, enabled))
        return true
    }
    
    fun updateEnableMasking(enabled: Boolean): Boolean {
        val old = config.enableMasking
        if (old == enabled) return false
        
        config.enableMasking = enabled
        notifyObservers(ConfigChange("enableMasking", old, enabled))
        return true
    }
    
    fun addObserver(observer: ConfigObserver) = observers.add(observer)
    
    fun removeObserver(observer: ConfigObserver) = observers.remove(observer)
    
    private fun notifyObservers(change: ConfigChange) {
        for (observer in observers) {
            try {
                scope.launch {
                    observer.onConfigChanged(change)
                }
            } catch (_: Exception) {
                // ignore
            }
        }
    }
    
    fun getCurrentConfig(): ConfigResponse = ConfigResponse(
        level = config.level.name,
        queueCapacity = config.queueCapacity,
        maxFileSize = config.maxFileSize,
        enableConsole = config.enableConsole,
        enableFile = config.enableFile,
        enableMasking = config.enableMasking,
        enableLogback = config.enableLogback,
        outputFormat = config.outputFormat.name
    )
    
    fun shutdown() {
        try {
            scope.cancel()
        } catch (_: Exception) {
            // ignore
        }
    }
}
