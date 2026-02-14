package com.lestere.opensource.sink

import com.lestere.opensource.logger.Logger
import com.lestere.opensource.logger.SoulLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue

interface RemoteSink {
    suspend fun send(log: Logger): Result<Unit>
    suspend fun sendBatch(logs: List<Logger>): Result<Int>
    fun isConnected(): Boolean
    fun close()
}

@Serializable
data class LogMessage(
    val version: String,
    val timestamp: String,
    val level: String,
    val thread: String,
    val entry: String,
    val command: String
) {
    companion object {
        fun from(logger: Logger): LogMessage {
            return LogMessage(
                version = logger.version,
                timestamp = logger.timestamp.toString(),
                level = logger.logLevel.name,
                thread = logger.thread.toString(),
                entry = logger.entry,
                command = logger.command
            )
        }
    }
}

class HttpSink(
    private val endpoint: String,
    private val batchSize: Int = 100,
    private val flushIntervalMs: Long = 5000,
    private val retryCount: Int = 3,
    private val connectionTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 30000
) : RemoteSink {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ConcurrentLinkedQueue<Logger>()
    
    private val _isConnected = MutableStateFlow(false)
    override fun isConnected(): Boolean = _isConnected.value
    
    private var isRunning = false
    
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    
    init {
        scope.launch {
            flushLoop()
        }
    }
    
    private suspend fun flushLoop() {
        isRunning = true
        while (isRunning) {
            delay(flushIntervalMs)
            if (queue.isNotEmpty()) {
                flush()
            }
        }
    }
    
    override suspend fun send(log: Logger): Result<Unit> = withContext(Dispatchers.IO) {
        queue.offer(log)
        
        if (queue.size >= batchSize) {
            flush()
        }
        
        Result.success(Unit)
    }
    
    override suspend fun sendBatch(logs: List<Logger>): Result<Int> = withContext(Dispatchers.IO) {
        var successCount = 0
        
        for (log in logs) {
            val result = sendSingle(log)
            if (result.isSuccess) {
                successCount++
            }
        }
        
        Result.success(successCount)
    }
    
    private suspend fun sendSingle(log: Logger): Result<Unit> {
        repeat(retryCount) { attempt ->
            try {
                val url = URL(endpoint)
                val connection = url.openConnection() as java.net.HttpURLConnection
                
                connection.requestMethod = "POST"
                connection.connectTimeout = connectionTimeoutMs
                connection.readTimeout = readTimeoutMs
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                
                val logMessage = LogMessage.from(log)
                val jsonBody = json.encodeToString(logMessage)
                
                connection.outputStream.use { os ->
                    os.write(jsonBody.toByteArray(StandardCharsets.UTF_8))
                }
                
                val responseCode = connection.responseCode
                connection.disconnect()
                
                if (responseCode in 200..299) {
                    _isConnected.value = true
                    return Result.success(Unit)
                }
                
                if (attempt < retryCount - 1) {
                    delay(100L * (attempt + 1))
                }
            } catch (e: Exception) {
                if (attempt < retryCount - 1) {
                    delay(100L * (attempt + 1))
                }
            }
        }
        
        _isConnected.value = false
        return Result.failure(Exception("Failed to send log after $retryCount attempts"))
    }
    
    private suspend fun flush() {
        val batch = mutableListOf<Logger>()
        repeat(batchSize) {
            queue.poll()?.let { batch.add(it) }
        }
        
        if (batch.isEmpty()) return
        
        try {
            val url = URL(endpoint)
            val connection = url.openConnection() as java.net.HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.connectTimeout = connectionTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Batch-Size", batch.size.toString())
            connection.doOutput = true
            
            val messages = batch.map { LogMessage.from(it) }
            val jsonBody = "[" + messages.joinToString(",") { json.encodeToString(it) } + "]"
            
            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray(StandardCharsets.UTF_8))
            }
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            _isConnected.value = responseCode in 200..299
        } catch (e: Exception) {
            // Re-queue failed logs
            batch.forEach { queue.offer(it) }
            _isConnected.value = false
        }
    }
    
    override fun close() {
        isRunning = false
        scope.launch {
            flush()
        }
    }
}

class TcpSink(
    private val host: String,
    private val port: Int,
    private val batchSize: Int = 100,
    private val flushIntervalMs: Long = 5000,
    private val connectionTimeoutMs: Int = 5000
) : RemoteSink {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ConcurrentLinkedQueue<Logger>()
    
    private val _isConnected = MutableStateFlow(false)
    override fun isConnected(): Boolean = _isConnected.value
    
    private var isRunning = false
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    
    init {
        connect()
        scope.launch {
            flushLoop()
        }
    }
    
    private fun connect() {
        try {
            socket = Socket()
            socket?.connect(InetSocketAddress(host, port), connectionTimeoutMs)
            writer = PrintWriter(socket?.getOutputStream()?.let { 
                java.io.OutputStreamWriter(it, StandardCharsets.UTF_8)
            }, true)
            _isConnected.value = true
        } catch (e: Exception) {
            _isConnected.value = false
        }
    }
    
    private suspend fun flushLoop() {
        isRunning = true
        while (isRunning) {
            delay(flushIntervalMs)
            if (queue.isNotEmpty()) {
                flush()
            }
        }
    }
    
    override suspend fun send(log: Logger): Result<Unit> {
        queue.offer(log)
        
        if (queue.size >= batchSize) {
            flush()
        }
        
        return Result.success(Unit)
    }
    
    override suspend fun sendBatch(logs: List<Logger>): Result<Int> {
        var successCount = 0
        
        for (log in logs) {
            try {
                val message = json.encodeToString(LogMessage.from(log))
                writer?.println(message)
                successCount++
            } catch (e: Exception) {
                // Continue with next log
            }
        }
        
        writer?.flush()
        
        return Result.success(successCount)
    }
    
    private suspend fun flush() {
        val batch = mutableListOf<Logger>()
        repeat(batchSize) {
            queue.poll()?.let { batch.add(it) }
        }
        
        if (batch.isEmpty()) return
        
        try {
            if (socket == null || socket?.isClosed != false) {
                connect()
            }
            
            for (log in batch) {
                val message = json.encodeToString(LogMessage.from(log))
                writer?.println(message)
            }
            
            writer?.flush()
            _isConnected.value = true
        } catch (e: Exception) {
            // Re-queue failed logs
            batch.forEach { queue.offer(it) }
            _isConnected.value = false
        }
    }
    
    override fun close() {
        isRunning = false
        writer?.close()
        socket?.close()
    }
}

class FailoverSink(
    private val sinks: List<RemoteSink>
) : RemoteSink {
    
    private var currentSinkIndex = 0
    
    override suspend fun send(log: Logger): Result<Unit> {
        for (i in 0 until sinks.size) {
            val index = (currentSinkIndex + i) % sinks.size
            val result = sinks[index].send(log)
            
            if (result.isSuccess) {
                currentSinkIndex = index
                return Result.success(Unit)
            }
        }
        
        return Result.failure(Exception("All sinks failed"))
    }
    
    override suspend fun sendBatch(logs: List<Logger>): Result<Int> {
        var totalSuccess = 0
        
        for (sink in sinks) {
            val result = sink.sendBatch(logs)
            if (result.isSuccess) {
                return result
            }
        }
        
        return Result.success(totalSuccess)
    }
    
    override fun isConnected(): Boolean {
        return sinks.any { it.isConnected() }
    }
    
    override fun close() {
        sinks.forEach { it.close() }
    }
}

enum class SinkType {
    HTTP,
    TCP,
    UDP,
    SYSLOG,
    FAILOVER
}

data class SinkConfig(
    val type: SinkType = SinkType.HTTP,
    val endpoint: String = "http://localhost:8080/logs",
    val host: String = "localhost",
    val port: Int = 5140,
    val batchSize: Int = 100,
    val flushIntervalMs: Long = 5000,
    val retryCount: Int = 3,
    val connectionTimeoutMs: Int = 5000,
    val readTimeoutMs: Int = 30000,
    val fallbackEndpoints: List<String> = emptyList()
) {
    fun createSink(): RemoteSink {
        return when (type) {
            SinkType.HTTP -> HttpSink(
                endpoint = endpoint,
                batchSize = batchSize,
                flushIntervalMs = flushIntervalMs,
                retryCount = retryCount,
                connectionTimeoutMs = connectionTimeoutMs,
                readTimeoutMs = readTimeoutMs
            )
            SinkType.TCP -> TcpSink(
                host = host,
                port = port,
                batchSize = batchSize,
                flushIntervalMs = flushIntervalMs,
                connectionTimeoutMs = connectionTimeoutMs
            )
            SinkType.FAILOVER -> {
                val allSinks = listOf(endpoint) + fallbackEndpoints
                val sinks = allSinks.map { HttpSink(it, batchSize, flushIntervalMs, retryCount) }
                FailoverSink(sinks)
            }
            else -> throw IllegalArgumentException("Unsupported sink type: $type")
        }
    }
}
