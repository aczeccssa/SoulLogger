# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SoulLogger is a Ktor plugin for structured logging with log analysis and report generation capabilities. It's a pure
Kotlin/JVM library published as a Gradle module.

## Build Commands

```bash
# Build and test
./gradlew build                    # Full build with tests
./gradlew :core:build              # Build core module only
./gradlew assemble                 # Assemble outputs

# Testing
./gradlew test                    # Run all tests
./gradlew :core:test              # Run core module tests only

# Development
./gradlew classes                 # Compile main classes
./gradlew clean                   # Clean build directories
./gradlew javadoc                 # Generate Javadoc
./gradlew dependencies            # View dependency tree
./gradlew buildEnvironment        # Check build environment
```

## Architecture

```
SoulLogger/
├── core/                          # Main module
│   └── src/main/kotlin/com/lestere/opensource/
│       ├── SoulLogger.kt          # Core logging API (debug/info/warn/error/fatal)
│       ├── SoulLoggerProvider.kt  # Async log processor using Kotlin Channel
│       ├── SoulLoggerPlugin.kt    # Ktor plugin integration
│       ├── SoulLoggerPluginConfiguration.kt
│       ├── SoulLoggerAnalyzer.kt  # Log analysis (JSON/CSV reports)
│       ├── arc/                   # Auto-release task scheduling system
│       │   ├── AutoReleaseCyber.kt
│       │   ├── AutoReleaseCyberTask.kt
│       │   └── AutoReleaseCyberTag.kt
│       ├── csv/                   # CSV handling
│       │   ├── CSVGenerator.kt
│       │   └── CSVAnalyser.kt
│       ├── models/                # Data models
│       │   └── Logger.kt
│       ├── routes/                # HTTP endpoints
│       │   └── LogFileAnalysisRoute.kt
│       └── utils/                 # Utilities (DateTime, Extension, etc.)
```

## Key Patterns

- **Channel-based Concurrency**: `SoulLoggerProvider` uses `Channel<Logger>` for thread-safe async log processing (
  replaced fragile `ReentrantLock` + `MutableList`)
- **Ktor Plugin**: Integrates via `SoulLoggerPlugin` following Ktor's plugin lifecycle
- **Regex Parsing**: Log parsing uses typed `Regex` pattern with named groups
- **Lifecycle-aware Scopes**: Uses `SupervisorJob` + structured concurrency for proper cleanup

## Log Levels

`DEBUG < INFO < WARN < ERROR < FATAL`

## HTTP API Endpoints

| Endpoint                                              | Description                |
|-------------------------------------------------------|----------------------------|
| `GET /soul/logger/{timestamp}`                        | Retrieve log file          |
| `GET /soul/logger/analysis/{timestamp}`               | Get paginated log analysis |
| `GET /soul/logger/report/generation/html/{timestamp}` | Generate HTML report       |

## Tech Stack

- Kotlin 2.2.21, JVM Toolchain 23
- Ktor 3.2.0 (server, content-negotiation, serialization)
- kotlinx-datetime 0.6.2
- kotlinx-dataframe 1.0.0-Beta4
- kotlinx-serialization

## Configuration

| Property            | Default  | Description       |
|---------------------|----------|-------------------|
| `maxFileSize`       | 2MB      | Max log file size |
| `level`             | INFO     | Logging threshold |
| `maxItems`          | 50       | Pagination size   |
| `tempFileExpiredIn` | 600000ms | Temp file TTL     |

## Important Notes

- No `println()` statements in library code - use structured logging
- Always use structured concurrency with proper scope management
- Library is silent in production - logs go through the plugin's output mechanism
