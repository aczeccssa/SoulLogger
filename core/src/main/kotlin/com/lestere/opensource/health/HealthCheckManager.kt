package com.lestere.opensource.health

import com.lestere.opensource.logger.SoulLoggerPluginConfiguration
import com.lestere.opensource.logger.SoulLoggerProvider
import com.lestere.opensource.models.HealthCheckItemResponse
import com.lestere.opensource.models.HealthCheckResponse
import com.lestere.opensource.models.HealthDetailValue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class HealthStatus(
    val status: Status,
    val checks: Map<String, CheckResult>,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Status {
        HEALTHY,
        DEGRADED,
        UNHEALTHY;
        
        fun toSimpleString(): String = name.lowercase()
    }
}

data class CheckResult(
    val status: HealthStatus.Status,
    val message: String? = null,
    val details: Map<String, HealthDetailValue>? = null
)

class HealthCheckManager(
    private val config: SoulLoggerPluginConfiguration
) {
    private val checkers = listOf(
        ::checkBuffer,
        ::checkDisk,
        ::checkQueue,
        ::checkLogDirectory,
        ::checkLogback
    )
    
    fun check(): HealthStatus {
        val results = mutableMapOf<String, CheckResult>()
        
        for (checker in checkers) {
            try {
                val result = checker()
                results[result.first] = result.second
            } catch (e: Exception) {
                results[checker.name] = CheckResult(
                    HealthStatus.Status.UNHEALTHY,
                    "Check failed: ${e.message}"
                )
            }
        }
        
        val overallStatus = when {
            results.values.any { it.status == HealthStatus.Status.UNHEALTHY } -> 
                HealthStatus.Status.UNHEALTHY
            results.values.any { it.status == HealthStatus.Status.DEGRADED } -> 
                HealthStatus.Status.DEGRADED
            else -> HealthStatus.Status.HEALTHY
        }
        
        return HealthStatus(overallStatus, results)
    }
    
    fun checkSync(): HealthCheckResponse {
        val status = check()
        return HealthCheckResponse(
            status = status.status.toSimpleString(),
            checks = status.checks.mapValues { (_, v) ->
                HealthCheckItemResponse(
                    status = v.status.toSimpleString(),
                    message = v.message ?: "",
                    details = v.details ?: emptyMap()
                )
            },
            timestamp = status.timestamp
        )
    }
    
    private fun checkBuffer(): Pair<String, CheckResult> {
        return try {
            val buffer = SoulLoggerProvider.getBuffer()
            if (buffer == null) {
                "buffer" to CheckResult(
                    HealthStatus.Status.DEGRADED,
                    "Buffer not initialized"
                )
            } else {
                val bufferSize = if (buffer is com.lestere.opensource.performance.LogBuffer) {
                    buffer.size()
                } else {
                    -1
                }
                "buffer" to CheckResult(
                    HealthStatus.Status.HEALTHY,
                    null,
                    mapOf("size" to HealthDetailValue.IntValue(bufferSize))
                )
            }
        } catch (e: Exception) {
            "buffer" to CheckResult(
                HealthStatus.Status.UNHEALTHY,
                e.message
            )
        }
    }
    
    private fun checkDisk(): Pair<String, CheckResult> {
        return try {
            val path = Paths.get(config.logDictionary)
            val fileStore = Files.getFileStore(path)
            val usableSpace = fileStore.usableSpace
            val threshold = 1024L * 1024L * 1024L // 1GB
            
            when {
                usableSpace < threshold / 10 -> "disk" to CheckResult(
                    HealthStatus.Status.UNHEALTHY,
                    "Critical: ${usableSpace / 1024 / 1024}MB available",
                    mapOf(
                        "usableSpace" to HealthDetailValue.LongValue(usableSpace),
                        "threshold" to HealthDetailValue.LongValue(threshold)
                    )
                )
                usableSpace < threshold -> "disk" to CheckResult(
                    HealthStatus.Status.DEGRADED,
                    "Low disk: ${usableSpace / 1024 / 1024}MB",
                    mapOf(
                        "usableSpace" to HealthDetailValue.LongValue(usableSpace),
                        "threshold" to HealthDetailValue.LongValue(threshold)
                    )
                )
                else -> "disk" to CheckResult(
                    HealthStatus.Status.HEALTHY,
                    null,
                    mapOf("usableSpace" to HealthDetailValue.LongValue(usableSpace))
                )
            }
        } catch (e: Exception) {
            "disk" to CheckResult(
                HealthStatus.Status.UNHEALTHY,
                e.message
            )
        }
    }
    
    private fun checkQueue(): Pair<String, CheckResult> {
        return try {
            val queueSize = SoulLoggerProvider.getQueueSize()
            val capacity = config.queueCapacity
            val usage = if (capacity > 0) queueSize.toDouble() / capacity else 0.0
            
            when {
                usage > 0.95 -> "queue" to CheckResult(
                    HealthStatus.Status.UNHEALTHY,
                    "Queue critical: ${(usage * 100).toInt()}% full",
                    mapOf(
                        "size" to HealthDetailValue.IntValue(queueSize),
                        "capacity" to HealthDetailValue.IntValue(capacity),
                        "usage" to HealthDetailValue.DoubleValue(usage)
                    )
                )
                usage > 0.8 -> "queue" to CheckResult(
                    HealthStatus.Status.DEGRADED,
                    "Queue high: ${(usage * 100).toInt()}% full",
                    mapOf(
                        "size" to HealthDetailValue.IntValue(queueSize),
                        "capacity" to HealthDetailValue.IntValue(capacity),
                        "usage" to HealthDetailValue.DoubleValue(usage)
                    )
                )
                else -> "queue" to CheckResult(
                    HealthStatus.Status.HEALTHY,
                    null,
                    mapOf(
                        "size" to HealthDetailValue.IntValue(queueSize),
                        "capacity" to HealthDetailValue.IntValue(capacity),
                        "usage" to HealthDetailValue.DoubleValue(usage)
                    )
                )
            }
        } catch (e: Exception) {
            "queue" to CheckResult(
                HealthStatus.Status.UNHEALTHY,
                e.message
            )
        }
    }
    
    private fun checkLogDirectory(): Pair<String, CheckResult> {
        return try {
            val path = Paths.get(config.logDictionary)
            val exists = Files.exists(path)
            val writable = Files.isWritable(path)
            
            when {
                !exists -> "logDirectory" to CheckResult(
                    HealthStatus.Status.UNHEALTHY,
                    "Log directory does not exist"
                )
                !writable -> "logDirectory" to CheckResult(
                    HealthStatus.Status.UNHEALTHY,
                    "Log directory is not writable"
                )
                else -> "logDirectory" to CheckResult(
                    HealthStatus.Status.HEALTHY,
                    null,
                    mapOf("path" to HealthDetailValue.StringValue(path.toString()))
                )
            }
        } catch (e: Exception) {
            "logDirectory" to CheckResult(
                HealthStatus.Status.UNHEALTHY,
                e.message
            )
        }
    }
    
    private fun checkLogback(): Pair<String, CheckResult> {
        return try {
            val enabled = config.enableLogback
            if (enabled) {
                "logback" to CheckResult(
                    HealthStatus.Status.HEALTHY,
                    "Logback enabled",
                    mapOf("enabled" to HealthDetailValue.BooleanValue(true))
                )
            } else {
                "logback" to CheckResult(
                    HealthStatus.Status.DEGRADED,
                    "Logback disabled",
                    mapOf("enabled" to HealthDetailValue.BooleanValue(false))
                )
            }
        } catch (e: Exception) {
            "logback" to CheckResult(
                HealthStatus.Status.UNHEALTHY,
                e.message
            )
        }
    }
}

class HealthCheckConfig(
    var enabled: Boolean = true,
    var includeDiskCheck: Boolean = true,
    var includeQueueCheck: Boolean = true,
    var diskSpaceThresholdMb: Long = 1024,
    var queueUsageThreshold: Double = 0.8
)
