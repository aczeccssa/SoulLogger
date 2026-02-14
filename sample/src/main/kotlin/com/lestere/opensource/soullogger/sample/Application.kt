package com.lestere.opensource.soullogger.sample

import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.logger.SoulLoggerPlugin
import com.lestere.opensource.logger.SoulLoggerProvider
import com.lestere.opensource.ApplicationMode
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * SoulLogger Sample Application
 * 
 * Demonstrates core logging, performance features, runtime management,
 * analysis & export, and Web UI dashboard.
 * 
 * Note: The DEBUG/WARN logs from Netty/Ktor during startup/shutdown are
 * framework internals and not related to SoulLogger functionality.
 * 
 * @since 1.0.0
 */
fun main() {
    val server = embeddedServer(Netty, port = 8000) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }

        install(SoulLoggerPlugin) {
            mode = ApplicationMode.DEVELOPMENT
            level = SoulLogger.Level.DEBUG
            maxFileSize = 10 * 1024 * 1024

            enableConsole = true
            enableFile = true

            rotation.enabled = true
            rotation.policyType = "TIME"
            rotation.timePattern = "DAILY"
            rotation.maxHistoryDays = 30
            rotation.compress = true

            logFilter.enabled = true
            logFilter.minLevel = SoulLogger.Level.DEBUG

            enableMasking = true

            performance.bufferType = com.lestere.opensource.logger.SoulLoggerPluginConfiguration.PerformanceConfiguration.BufferType.MMAP
            performance.mmapFileSize = 1024L * 1024L * 1024L
            performance.compressionEnabled = true
            performance.compressionType = "SNAPPY"

            runtimeManagement.dynamicConfigEnabled = true
            runtimeManagement.hotReloadEnabled = true
            runtimeManagement.hotReloadStrategy = "WATCH"
            runtimeManagement.runtimeLevelEnabled = true

            healthCheck.enabled = true

            stream.enabled = true
            stream.replay = 100
            stream.minLevel = SoulLogger.Level.DEBUG

            enableLogback = true
        }

        routing {
            get("/") {
                call.respondRedirect("/index.html")
            }

            get("/health") {
                call.respond(mapOf(
                    "status" to "healthy",
                    "service" to "SoulLogger Sample",
                    "version" to "1.0.0"
                ))
            }

            configureDemoRoutes()
            configureWebUI()
        }

        launch {
            delay(1000)
            SoulLogger.info("==============================================")
            SoulLogger.info("SoulLogger Sample Application Started")
            SoulLogger.info("==============================================")
            SoulLogger.info("Web UI: http://localhost:8000/webui/")
            SoulLogger.info("Demo:   http://localhost:8000/demo")
            SoulLogger.info("API:    http://localhost:8000/api/v1")
            SoulLogger.info("Health: http://localhost:8000/health")
            SoulLogger.info("==============================================")
        }

        launch {
            while (true) {
                delay(60_000)
                SoulLogger.debug("Heartbeat - application running")
            }
        }
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        try {
            server.stop(1, 5, TimeUnit.SECONDS)
        } catch (_: Throwable) { }
        try {
            SoulLoggerProvider.stop()
        } catch (_: Throwable) { }
    })
    
    server.start(wait = true)
}
