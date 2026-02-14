package com.lestere.opensource.aggregation

import com.lestere.opensource.logger.Logger
import com.lestere.opensource.logger.SoulLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap

class LogIndexer(
    private val maxEntries: Int = 100_000
) {
    private val index = ConcurrentHashMap<String, MutableList<IndexedLog>>()
    
    data class IndexedLog(
        val logger: Logger,
        val index: Int
    )
    
    private var currentIndex = 0
    private val _size = MutableStateFlow(0)
    val size: StateFlow<Int> = _size.asStateFlow()
    
    fun index(logger: Logger) {
        val idx = currentIndex++
        
        // Index by level
        getOrCreateList("level:${logger.logLevel.name}").add(IndexedLog(logger, idx))
        
        // Index by entry (class name)
        getOrCreateList("entry:${logger.entry}").add(IndexedLog(logger, idx))
        
        // Index by date (yyyy-MM-dd)
        val date = logger.timestamp.toString().substring(0, 10)
        getOrCreateList("date:$date").add(IndexedLog(logger, idx))
        
        // Index by hour (yyyy-MM-dd_HH)
        val hour = logger.timestamp.toString().substring(0, 13).replace("T", "_")
        getOrCreateList("hour:$hour").add(IndexedLog(logger, idx))
        
        // Index errors separately for fast access
        if (logger.logLevel >= SoulLogger.Level.ERROR) {
            getOrCreateList("errors").add(IndexedLog(logger, idx))
        }
        
        // Simple word index for common keywords
        val words = logger.command.split(Regex("[\\s,.:;!?()\\[\\]]+"))
        words.filter { it.length > 3 }.take(5).forEach { word ->
            val normalized = word.lowercase()
            if (normalized !in COMMON_WORDS) {
                getOrCreateList("word:$normalized").add(IndexedLog(logger, idx))
            }
        }
        
        _size.value = currentIndex
        
        // Trim if exceeding max
        if (currentIndex > maxEntries) {
            // In production, we'd implement actual trimming
        }
    }
    
    private fun getOrCreateList(key: String): MutableList<IndexedLog> {
        return index.getOrPut(key) { mutableListOf() }
    }
    
    fun search(query: String): List<Logger> {
        val results = mutableMapOf<Int, IndexedLog>()
        
        // Parse query - simple implementation
        val terms = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        
        for (term in terms) {
            val normalized = term.lowercase().trim()
            
            when {
                normalized.startsWith("level:") -> {
                    val level = normalized.substringAfter(":")
                    index["level:$level"]?.forEach { results[it.index] = it }
                }
                normalized.startsWith("entry:") -> {
                    val entry = normalized.substringAfter(":")
                    index["entry:$entry"]?.forEach { results[it.index] = it }
                }
                normalized.startsWith("date:") -> {
                    val date = normalized.substringAfter(":")
                    index["date:$date"]?.forEach { results[it.index] = it }
                }
                normalized.startsWith("hour:") -> {
                    val hour = normalized.substringAfter(":")
                    index["hour:$hour"]?.forEach { results[it.index] = it }
                }
                normalized == "error" || normalized == "errors" -> {
                    index["errors"]?.forEach { results[it.index] = it }
                }
                else -> {
                    // Search in word index
                    index.filter { it.key.startsWith("word:") && it.key.contains(normalized) }
                        .values.flatten()
                        .forEach { results[it.index] = it }
                }
            }
        }
        
        return results.values.sortedBy { it.index }.map { it.logger }
    }
    
    fun getByLevel(level: SoulLogger.Level): List<Logger> {
        return index["level:${level.name}"]?.map { it.logger } ?: emptyList()
    }
    
    fun getErrors(): List<Logger> {
        return index["errors"]?.map { it.logger } ?: emptyList()
    }
    
    fun getByDate(date: String): List<Logger> {
        return index["date:$date"]?.map { it.logger } ?: emptyList()
    }
    
    fun getRecent(count: Int): List<Logger> {
        return index.entries
            .filter { it.key.startsWith("hour:") }
            .flatMap { it.value }
            .sortedByDescending { it.logger.timestamp }
            .take(count)
            .map { it.logger }
    }
    
    fun clear() {
        index.clear()
        currentIndex = 0
        _size.value = 0
    }
    
    companion object {
        private val COMMON_WORDS = setOf(
            "the", "and", "for", "are", "but", "not", "you", "all", "can", "had",
            "her", "was", "one", "our", "out", "has", "have", "been", "were"
        )
    }
}
