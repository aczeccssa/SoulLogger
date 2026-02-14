# SoulLogger

<p align="center">
  <a href="https://kotlinlang.org/">
    <img src="https://img.shields.io/badge/Kotlin-2.2.21-blue.svg?style=flat&logo=kotlin" alt="Kotlin">
  </a>
  <a href="https://ktor.io/">
    <img src="https://img.shields.io/badge/Ktor-3.2.0-blue.svg?style=flat&logo=ktor" alt="Ktor">
  </a>
  <a href="https://opensource.org/licenses/Apache-2.0">
    <img src="https://img.shields.io/badge/License-Apache%202.0-green.svg?style=flat" alt="License">
  </a>
  <a href="https://github.com/AnomalyCo/SoulLogger/actions">
    <img src="https://img.shields.io/badge/Build-Passing-green.svg?style=flat" alt="Build">
  </a>
</p>

> A powerful Ktor plugin for structured logging with log analysis and report generation capabilities.

SoulLogger is a pure Kotlin/JVM library designed for Ktor applications. It provides comprehensive structured logging
with asynchronous processing, log rotation, analysis tools, and runtime management capabilities.

## Features

- **Structured Logging** - Multi-level logging (DEBUG, INFO, WARN, ERROR, FATAL) with JSON/text output
- **Asynchronous Processing** - Channel-based async log processing with backpressure support
- **Log Rotation** - Automatic log file rotation by size and time
- **Log Analysis** - Built-in analysis engine for log aggregation and statistics
- **CSV Export** - Export logs to CSV format for external analysis
- **Runtime Management** - Dynamic log level adjustment without restart
- **Hot Reload** - Configuration hot reload support
- **Health Checks** - Built-in health check endpoints
- **MDC Support** - Mapped Diagnostic Context for trace tracking
- **Data Masking** - Automatic sensitive data masking in production

## Quick Start

### 1. Add Dependency

```kotlin
// build.gradle.kts (Kotlin DSL)
dependencies {
    implementation("com.lestere.opensource:soul-logger-core:1.0.0")
}
```

```groovy
// build.gradle (Groovy)
dependencies {
    implementation 'com.lestere.opensource:soul-logger-core:1.0.0'
}
```

### 2. Install Plugin

```kotlin
import com.lestere.opensource.logger.SoulLoggerPlugin
import com.lestere.opensource.ApplicationMode

fun Application.module() {
    install(SoulLoggerPlugin) {
        mode = ApplicationMode.DEVELOPMENT
        logPath = "logs"
        maxFileSize = 10 * 1024 * 1024 // 10MB
        maxFiles = 5
    }
}
```

### 3. Use Logger

```kotlin
import com.lestere.opensource.logger.SoulLogger

// Different log levels
SoulLogger.debug("Debug information")
SoulLogger.info("Application started")
SoulLogger.warn("Warning message")
SoulLogger.error(exception)  // Returns the exception
SoulLogger.fatal(exception)   // Returns the exception

// With custom thread
SoulLogger.info("Background task completed", backgroundThread)
```

## Log Levels

| Level | Description                               |
|-------|-------------------------------------------|
| DEBUG | Detailed information for debugging        |
| INFO  | General informational messages            |
| WARN  | Warning messages, potentially problematic |
| ERROR | Error messages with exceptions            |
| FATAL | Critical errors causing application exit  |

## Configuration

### Application Modes

SoulLogger supports two application modes with different defaults:

#### Development Mode

- Console output enabled
- Full stack traces
- Introspection routes enabled
- No data masking
- Colorful text format

#### Production Mode

- Console output disabled
- Compact stack traces
- Introspection routes disabled
- Automatic data masking
- JSON format

### Configuration File

Create `application.conf`:

```hocon
soulLogger {
    mode = development
    
    log {
        path = "logs"
        max-file-size = 10485760
        max-files = 5
        async = true
        queue-capacity = 10000
    }
    
    runtime-management {
        dynamic-config-enabled = true
        runtime-level-enabled = true
        hot-reload-enabled = false
    }
    
    health-check {
        enabled = true
        path = "/soul/logger/health"
    }
    
    analysis {
        analysis-route = true
        reflex-route = true
    }
}
```

### Environment Variables

| Variable                    | Description                | Default                     |
|-----------------------------|----------------------------|-----------------------------|
| `SOUL_LOGGER_MODE`          | Application mode           | development                 |
| `SOUL_LOGGER_LOG_PATH`      | Log file directory         | logs                        |
| `SOUL_LOGGER_MAX_FILE_SIZE` | Max log file size in bytes | 10485760 (10MB)             |
| `SOUL_LOGGER_MAX_FILES`     | Max number of log files    | 5                           |
| `SOUL_LOGGER_LEVEL`         | Log level                  | DEBUG/INFO (mode dependent) |

## API Reference

### Core API

```kotlin
// Check if logger is active
SoulLogger.active

// Get log stream for external consumption
SoulLogger.logStream

// Logging methods
SoulLogger.debug(command: Any)
SoulLogger.info(command: Any)
SoulLogger.warn(command: Any)
SoulLogger.error(command: Throwable): Throwable
SoulLogger.fatal(command: Exception): Exception
```

### Log Stream

Subscribe to all logs:

```kotlin
SoulLogger.logStream.collect { log ->
    println("[${log.logLevel}] ${log.command}")
}
```

Server-Sent Events example:

```kotlin
get("/logs/stream") {
    call.respondTextWriter(ContentType.Text.EventStream) {
        SoulLogger.logStream.collect { log ->
            write("data: ${Json.encodeToString(log)}\n\n")
            flush()
        }
    }
}
```

### Log Model

```kotlin
data class Logger(
    val version: String,
    val timestamp: Instant,
    val logLevel: SoulLogger.Level,
    val thread: ThreadInfo,
    val entry: String,
    val command: String
)
```

### Runtime Management Endpoints

| Endpoint                | Method | Description               |
|-------------------------|--------|---------------------------|
| `/soul/logger/health`   | GET    | Health check              |
| `/soul/logger/level`    | GET    | Get current log level     |
| `/soul/logger/level`    | POST   | Set log level             |
| `/soul/logger/config`   | GET    | Get current configuration |
| `/soul/logger/config`   | POST   | Update configuration      |
| `/soul/logger/analysis` | POST   | Analyze logs              |
| `/soul/logger/reflex`   | GET    | Query logs                |

## Architecture

```
SoulLogger/
├── core/src/main/kotlin/com/lestere/opensource/
│   ├── logger/           # Core logging API
│   │   ├── SoulLogger.kt
│   │   ├── SoulLoggerProvider.kt
│   │   ├── SoulLoggerPlugin.kt
│   │   └── events/      # Event handling
│   ├── aggregation/     # Log aggregation engine
│   ├── arc/             # Auto-release tasks
│   ├── config/          # Configuration management
│   ├── context/         # MDC and context
│   ├── csv/             # CSV handling
│   ├── filter/          # Log filtering
│   ├── health/          # Health checks
│   ├── masking/         # Data masking
│   ├── metrics/         # Metrics collection
│   ├── models/          # Data models
│   ├── performance/     # Performance optimization
│   ├── rotation/        # Log rotation
│   ├── sink/            # Log sinks
│   └── utils/           # Utilities
└── sample/              # Sample application
```

## Tech Stack

- **Language**: Kotlin 2.2.21
- **Framework**: Ktor 3.2.0
- **Async**: Kotlin Coroutines
- **DateTime**: kotlinx-datetime
- **Serialization**: kotlinx-serialization
- **Logging**: Logback
- **Testing**: JUnit 5, MockK

## Building

```bash
# Build the project
./gradlew build

# Build core module only
./gradlew :core:build

# Run tests
./gradlew test

# Generate API documentation
./gradlew :core:dokkaGenerate
```

## Contributing

Contributions are welcome! Please read our [contributing guidelines](CONTRIBUTING.md) first.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

```
Copyright 2024 LesterE

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Acknowledgments

- [Kotlin](https://kotlinlang.org/)
- [Ktor](https://ktor.io/)
- [Kotlinx](https://github.com/Kotlin/kotlinx)

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/AnomalyCo">AnomalyCo</a>
</p>
