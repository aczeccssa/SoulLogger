package com.lestere.opensource.logger.routes

import com.lestere.opensource.health.HealthCheckManager
import com.lestere.opensource.health.HealthStatus
import com.lestere.opensource.logger.SoulLoggerPluginConfiguration
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureHealthCheck(config: SoulLoggerPluginConfiguration, healthManager: HealthCheckManager) {
    routing {
        route("/soul/logger/health") {
            get {
                val status = healthManager.check()
                call.respond(
                    if (status.status == HealthStatus.Status.HEALTHY) HttpStatusCode.OK
                    else if (status.status == HealthStatus.Status.DEGRADED) HttpStatusCode.ServiceUnavailable
                    else HttpStatusCode.InternalServerError,
                    mapOf(
                        "status" to status.status.toSimpleString(),
                        "checks" to status.checks.mapValues {
                            mapOf(
                                "status" to it.value.status.toSimpleString(),
                                "message" to (it.value.message ?: ""),
                                "details" to (it.value.details ?: emptyMap<String, Any>())
                            )
                        },
                        "timestamp" to status.timestamp
                    )
                )
            }
            
            get("/live") {
                call.respond(mapOf("status" to "UP", "service" to "SoulLogger"))
            }
            
            get("/ready") {
                val status = healthManager.check()
                val httpStatus = when (status.status) {
                    HealthStatus.Status.HEALTHY -> HttpStatusCode.OK
                    HealthStatus.Status.DEGRADED -> HttpStatusCode.ServiceUnavailable
                    HealthStatus.Status.UNHEALTHY -> HttpStatusCode.ServiceUnavailable
                }
                call.respond(
                    httpStatus,
                    mapOf(
                        "status" to when (status.status) {
                            HealthStatus.Status.HEALTHY -> "UP"
                            else -> "DOWN"
                        },
                        "checks" to status.checks.mapValues { it.value.status.toSimpleString() }
                    )
                )
            }
            
            get("/buffer") {
                val status = healthManager.check()
                val bufferCheck = status.checks["buffer"]
                call.respond(
                    mapOf(
                        "status" to (bufferCheck?.status?.toSimpleString() ?: "UNKNOWN"),
                        "message" to (bufferCheck?.message ?: ""),
                        "details" to (bufferCheck?.details ?: emptyMap())
                    )
                )
            }
            
            get("/disk") {
                val status = healthManager.check()
                val diskCheck = status.checks["disk"]
                call.respond(
                    mapOf(
                        "status" to (diskCheck?.status?.toSimpleString() ?: "UNKNOWN"),
                        "message" to (diskCheck?.message ?: ""),
                        "details" to (diskCheck?.details ?: emptyMap())
                    )
                )
            }
            
            get("/queue") {
                val status = healthManager.check()
                val queueCheck = status.checks["queue"]
                call.respond(
                    mapOf(
                        "status" to (queueCheck?.status?.toSimpleString() ?: "UNKNOWN"),
                        "message" to (queueCheck?.message ?: ""),
                        "details" to (queueCheck?.details ?: emptyMap())
                    )
                )
            }
        }
    }
}
