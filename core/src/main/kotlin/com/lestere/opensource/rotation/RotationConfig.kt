package com.lestere.opensource.rotation

/**
 * Log rotation configuration for SoulLogger.
 * 
 * Provides enterprise-grade log rotation with:
 * - Time-based rotation (hourly, daily, weekly, monthly)
 * - Size-based rotation
 * - Composite rotation (time + size)
 * - Automatic compression of rotated files
 * - Retention policies (by age, count, total size)
 * 
 * ## Configuration Examples
 * 
 * ### Daily rotation with 30-day retention:
 * ```kotlin
 * rotation {
 *     policy = RotationPolicy.TimeBased(
 *         pattern = TimeRotationPattern.DAILY
 *     )
 *     retention = RetentionConfig(
 *         maxHistoryDays = 30
 *     )
 * }
 * ```
 * 
 * ### Size-based rotation with compression:
 * ```kotlin
 * rotation {
 *     policy = RotationPolicy.SizeBased(
 *         maxSizeBytes = 100 * 1024 * 1024 // 100MB
 *     )
 *     compression = CompressionConfig(
 *         enabled = true
 *     )
 * }
 * ```
 * 
 * ### Composite rotation:
 * ```kotlin
 * rotation {
 *     policy = RotationPolicy.Composite(
 *         timePattern = TimeRotationPattern.DAILY,
 *         maxSizeBytes = 100 * 1024 * 1024
 *     )
 * }
 * ```
 * 
 * @since 1.1.0
 */
data class RotationConfig(
    val enabled: Boolean = true,
    val policy: RotationPolicy = RotationPolicy.TimeBased(),
    val retention: RetentionConfig = RetentionConfig(),
    val compression: CompressionConfig = CompressionConfig()
)

enum class TimeRotationPattern(val pattern: String) {
    HOURLY("yyyy-MM-dd_HH"),
    DAILY("yyyy-MM-dd"),
    WEEKLY("yyyy-'W'ww"),
    MONTHLY("yyyy-MM")
}
