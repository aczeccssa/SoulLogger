package com.lestere.opensource.config

import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.SoulLoggerPluginConfiguration
import kotlinx.coroutines.*
import java.nio.file.*
import java.nio.file.attribute.FileTime
import kotlin.coroutines.CoroutineContext

class HotReloader(
    private val configPath: Path,
    private val configManager: DynamicConfigManager,
    private val strategy: ReloadStrategy = ReloadStrategy.WATCH,
    private val reloadContext: CoroutineContext = Dispatchers.IO
) {
    enum class ReloadStrategy {
        POLLING,
        WATCH
    }
    
    private var watcher: WatchService? = null
    private val scope = CoroutineScope(reloadContext + SupervisorJob())
    private val _isRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
    
    private val isRunning: kotlinx.coroutines.flow.StateFlow<Boolean>
        get() = _isRunning
    
    private var lastModified: FileTime = FileTime.fromMillis(0)
    private var reloadIntervalMs: Long = 5000L
    
    fun start() {
        if (_isRunning.value) return
        
        _isRunning.value = true
        
        when (strategy) {
            ReloadStrategy.POLLING -> startPolling()
            ReloadStrategy.WATCH -> startWatching()
        }
        
        SoulLogger.info("HotReloader started with strategy: ${strategy.name}")
    }
    
    private fun startPolling() {
        scope.launch {
            while (isActive && _isRunning.value) {
                try {
                    val current = Files.getLastModifiedTime(configPath)
                    if (current.toMillis() > lastModified.toMillis()) {
                        lastModified = current
                        reload()
                    }
                } catch (_: Exception) {
                    // ignore
                }
                delay(reloadIntervalMs)
            }
        }
    }
    
    private fun startWatching() {
        try {
            watcher = FileSystems.getDefault().newWatchService()
            configPath.parent?.let { parent ->
                parent.register(
                    watcher,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE
                )
            }
            
            scope.launch {
                while (isActive && _isRunning.value) {
                    try {
                        val key = watcher?.take() ?: break
                        for (event in key.pollEvents()) {
                            val path = event.context() as? Path
                            if (path?.fileName == configPath.fileName) {
                                reload()
                            }
                        }
                        key.reset()
                    } catch (_: InterruptedException) {
                        break
                    } catch (e: Throwable) {
                        // ignore
                    }
                }
            }
        } catch (e: Exception) {
            SoulLogger.warn("Failed to start file watcher, falling back to polling: ${e.message}")
            startPolling()
        }
    }
    
    private suspend fun reload() {
        try {
            val newConfig = loadConfigFromFile(configPath)
            applyConfig(newConfig)
            SoulLogger.info("Configuration hot-reloaded from $configPath")
        } catch (e: Exception) {
            SoulLogger.warn("Failed to reload config: ${e.message}")
        }
    }
    
    private fun loadConfigFromFile(path: Path): Map<String, Any> {
        val content = Files.readAllBytes(path)
        return try {
            kotlinx.serialization.json.Json.decodeToMap(
                kotlinx.serialization.json.Json.parseToJsonElement(
                    String(content, Charsets.UTF_8)
                )
            )
        } catch (_: Exception) {
            // 尝试 HOCON 解析
            parseHocon(content)
        }
    }
    
    private fun parseHocon(content: ByteArray): Map<String, Any> {
        // 简化 HOCON 解析
        val result = mutableMapOf<String, Any>()
        val lines = String(content).lines()
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                continue
            }
            
            val idx = trimmed.indexOf('=')
            if (idx > 0) {
                val key = trimmed.substring(0, idx).trim()
                val value = trimmed.substring(idx + 1).trim()
                result[key] = value
            }
        }
        
        return result
    }
    
    private fun applyConfig(config: Map<String, Any>) {
        config["level"]?.let {
            try {
                val level = com.lestere.opensource.logger.SoulLogger.Level.valueOf(it.toString().uppercase())
                configManager.updateLevel(level)
            } catch (_: Exception) {
                // ignore
            }
        }
        
        config["queue-capacity"]?.let {
            it.toString().toIntOrNull()?.let { cap ->
                configManager.updateQueueCapacity(cap)
            }
        }
        
        config["max-file-size"]?.let {
            it.toString().toLongOrNull()?.let { size ->
                configManager.updateMaxFileSize(size)
            }
        }
        
        config["console"]?.let {
            it.toString().toBooleanStrictOrNull()?.let { enabled ->
                configManager.updateEnableConsole(enabled)
            }
        }
        
        config["file"]?.let {
            it.toString().toBooleanStrictOrNull()?.let { enabled ->
                configManager.updateEnableFile(enabled)
            }
        }
    }
    
    fun stop() {
        _isRunning.value = false
        try {
            watcher?.close()
        } catch (_: Exception) {
            // ignore
        }
        scope.cancel()
        SoulLogger.info("HotReloader stopped")
    }
    
    fun setReloadInterval(ms: Long) {
        reloadIntervalMs = ms
    }
}

private fun kotlinx.serialization.json.Json.parseToJsonElement(s: String): kotlinx.serialization.json.JsonElement {
    return kotlinx.serialization.json.Json.parseToJsonElement(s)
}

private fun kotlinx.serialization.json.Json.decodeToMap(element: kotlinx.serialization.json.JsonElement): Map<String, Any> {
    val result = mutableMapOf<String, Any>()
    if (element is kotlinx.serialization.json.JsonObject) {
        for ((k, v) in element) {
            result[k] = when (v) {
                is kotlinx.serialization.json.JsonPrimitive -> v.toString()
                is kotlinx.serialization.json.JsonObject -> decodeToMap(v)
                is kotlinx.serialization.json.JsonArray -> v.map { it.toString() }
                else -> v.toString()
            }
        }
    }
    return result
}
