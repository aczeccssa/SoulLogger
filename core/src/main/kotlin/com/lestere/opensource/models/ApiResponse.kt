package com.lestere.opensource.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SuccessResponse(
    val success: Boolean = true
)

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class GlobalLevelResponse(
    val level: String
)

@Serializable
data class SetLevelResponse(
    val success: Boolean = true,
    val packagePattern: String,
    val level: String
)

@Serializable
data class SetGlobalLevelResponse(
    val success: Boolean = true,
    val globalLevel: String
)

@Serializable
data class RemoveLevelResponse(
    val success: Boolean = true,
    val removed: String
)

@Serializable
data class ResetLevelsResponse(
    val success: Boolean = true,
    val message: String = "All custom levels reset"
)

@Serializable
data class LevelUpdateResponse(
    val success: Boolean = true,
    val level: String
)

@Serializable
data class CapacityUpdateResponse(
    val success: Boolean = true,
    val capacity: Int
)

@Serializable
data class SizeUpdateResponse(
    val success: Boolean = true,
    val size: Long
)

@Serializable
data class EnableUpdateResponse(
    val success: Boolean = true,
    val enabled: Boolean
)

@Serializable
data class HealthCheckResponse(
    val status: String,
    val checks: Map<String, HealthCheckItemResponse>,
    val timestamp: Long
)

@Serializable
data class HealthCheckItemResponse(
    val status: String,
    val message: String,
    val details: Map<String, HealthDetailValue> = emptyMap()
)

@Serializable
sealed class HealthDetailValue {
    @Serializable
    data class StringValue(val value: String) : HealthDetailValue()

    @Serializable
    data class IntValue(val value: Int) : HealthDetailValue()

    @Serializable
    data class LongValue(val value: Long) : HealthDetailValue()

    @Serializable
    data class DoubleValue(val value: Double) : HealthDetailValue()

    @Serializable
    data class BooleanValue(val value: Boolean) : HealthDetailValue()

    @Serializable
    data class Empty(val value: String = "") : HealthDetailValue()
}

@Serializable
data class HealthDetailResponse(
    val status: String,
    val message: String,
    val details: Map<String, HealthDetailValue> = emptyMap()
)

@Serializable
data class HealthLiveResponse(
    @SerialName("status") val status: String = "UP",
    @SerialName("service") val service: String = "SoulLogger"
)

@Serializable
data class HealthReadyResponse(
    @SerialName("status") val status: String,
    @SerialName("checks") val checks: Map<String, String>
)

@Serializable
data class ConfigResponse(
    val level: String,
    val queueCapacity: Int,
    val maxFileSize: Long,
    val enableConsole: Boolean,
    val enableFile: Boolean,
    val enableMasking: Boolean,
    val enableLogback: Boolean,
    val outputFormat: String
)

@Serializable
data class MetricsSummaryResponse(
    val logsWritten: Long,
    val logsDropped: Long,
    val logsErrors: Long,
    val queueSize: Long,
    val rotationCount: Long,
    val writeLatencyP50: Double,
    val writeLatencyP95: Double,
    val writeLatencyP99: Double,
    val flushLatencyP50: Double,
    val flushLatencyP95: Double,
    val flushLatencyP99: Double
)

@Serializable
data class StatisticsResponse(
    val total: Int,
    val byLevel: Map<String, Int>,
    val errorCount: Int,
    val errorRate: Double,
    val uniqueEntries: Int,
    val timeRange: TimeRangeResponse?
)

@Serializable
data class TimeRangeResponse(
    val start: String,
    val end: String
)
