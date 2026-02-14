# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-02-15

### Changed
- **Breaking**: Health check details now use typed `HealthDetailValue` sealed class instead of raw `Any` type for better type safety
- All HTTP endpoint responses now use `@Serializable` data classes instead of `mapOf()`
- Enhanced JSON serialization with explicit type handling for health check metrics

### Added
- New `ApiResponse.kt` with comprehensive serializable response models:
  - `ConfigResponse`, `MetricsSummaryResponse`, `StatisticsResponse`
  - `HealthCheckResponse`, `HealthLiveResponse`, `HealthReadyResponse`, `HealthDetailResponse`
  - `LevelUpdateResponse`, `CapacityUpdateResponse`, `SizeUpdateResponse`, `EnableUpdateResponse`
  - `HealthDetailValue` sealed class with `StringValue`, `IntValue`, `LongValue`, `DoubleValue`, `BooleanValue` variants
- All config and level management endpoints now return typed response objects

### Fixed
- Sample application JSON serialization to include default values (`encodeDefaults = true`)
- Test adaptation for `AggregationEngine.getStatistics()` return type change

### Refactored
- `HealthCheckManager.checkSync()` → returns `HealthCheckResponse`
- `DynamicConfigManager.getCurrentConfig()` → returns `ConfigResponse`
- `SoulLoggerMetrics.getSummary()` → returns `MetricsSummaryResponse`
- `AggregationEngine.getStatistics()` → returns `StatisticsResponse`
- All route handlers now use typed response data classes

---

## [1.0.0] - 2026-02-14

### Added
- Initial release of SoulLogger
- Structured logging with Ktor plugin integration
- Log analysis and report generation capabilities
- CSV/HTML report generation
- Runtime level management
- Health check endpoints
- Performance metrics collection
- Log buffering and batching
- File rotation support
- Sensitive data masking
- Hot configuration reload
