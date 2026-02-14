package com.lestere.opensource.performance

import com.lestere.opensource.logger.Logger
import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.ThreadInfo
import com.lestere.opensource.utils.VERSION
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class LoggerObjectPool(
    private val capacity: Int = 1000
) {
    private val queue = ConcurrentLinkedQueue<PooledLogger>()
    private val created = java.util.concurrent.atomic.AtomicLong(0)
    private val reused = java.util.concurrent.atomic.AtomicLong(0)
    
    fun acquire(): PooledLogger {
        val pooled = queue.poll()
        if (pooled != null) {
            reused.incrementAndGet()
            pooled.reset()
            return pooled
        }
        
        created.incrementAndGet()
        return PooledLogger(this)
    }
    
    internal fun release(pooled: PooledLogger) {
        if (queue.size < capacity) {
            queue.offer(pooled)
        }
    }
    
    fun size(): Int = queue.size
    
    fun stats(): PoolStats {
        return PoolStats(
            created = created.get(),
            reused = reused.get(),
            available = queue.size,
            capacity = capacity
        )
    }
    
    fun clear() {
        queue.clear()
    }
}

class PooledLogger(private val pool: LoggerObjectPool) {
    var version: String = VERSION
    var timestamp: kotlinx.datetime.Instant = kotlinx.datetime.Clock.System.now()
    var level: SoulLogger.Level = SoulLogger.Level.INFO
    var threadInfo: ThreadInfo = ThreadInfo(-1, "")
    var entry: String = ""
    var command: String = ""
    private var released = false
    
    fun reset() {
        timestamp = kotlinx.datetime.Clock.System.now()
        level = SoulLogger.Level.INFO
        entry = ""
        command = ""
        released = false
    }
    
    fun set(
        level: SoulLogger.Level,
        command: Any,
        thread: Thread,
        entry: String
    ) {
        this.level = level
        this.command = command.toString()
        this.threadInfo = ThreadInfoCache.of(thread)
        this.entry = entry
    }
    
    fun toLogger(): Logger {
        return Logger(
            version = version,
            timestamp = timestamp,
            logLevel = level,
            thread = threadInfo,
            entry = entry,
            command = command
        )
    }
    
    fun release() {
        if (!released) {
            released = true
            pool.release(this)
        }
    }
}

data class PoolStats(
    val created: Long,
    val reused: Long,
    val available: Int,
    val capacity: Int
) {
    val reuseRatio: Double
        get() = if (created + available > 0) {
            (reused.toDouble() / (created + available)) * 100
        } else 0.0
}

object ThreadInfoCache {
    private val cache = ConcurrentHashMap<Long, ThreadInfo>()
    
    fun of(thread: Thread): ThreadInfo {
        return cache.getOrPut(thread.threadId()) {
            ThreadInfo(thread.threadId(), thread.name)
        }
    }
    
    fun of(id: Long, name: String): ThreadInfo {
        return cache.getOrPut(id) {
            ThreadInfo(id, name)
        }
    }
    
    fun clear() = cache.clear()
    
    fun size(): Int = cache.size
}
