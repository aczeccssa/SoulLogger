package com.lestere.opensource.logger.routes

import com.lestere.opensource.health.HealthCheckManager
import com.lestere.opensource.health.HealthStatus
import com.lestere.opensource.models.HealthCheckItemResponse
import com.lestere.opensource.models.HealthCheckResponse
import com.lestere.opensource.models.HealthDetailResponse
import com.lestere.opensource.models.HealthDetailValue
import com.lestere.opensource.models.HealthLiveResponse
import com.lestere.opensource.models.HealthReadyResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureHealthCheck(healthManager: HealthCheckManager) {
    routing {
        route("/soul/logger/health") {
            get {
                val status = healthManager.check()
                call.respond(
                    if (status.status == HealthStatus.Status.HEALTHY) HttpStatusCode.OK
                    else if (status.status == HealthStatus.Status.DEGRADED) HttpStatusCode.ServiceUnavailable
                    else HttpStatusCode.InternalServerError,
                    HealthCheckResponse(
                        status = status.status.toSimpleString(),
                        checks = status.checks.mapValues { (_, v) ->
                            HealthCheckItemResponse(
                                status = v.status.toSimpleString(),
                                message = v.message ?: "",
                                details = v.details?.mapValues { convertToHealthDetailValue(it.value) } ?: emptyMap()
                            )
                        },
                        timestamp = status.timestamp
                    )
                )
            }
            
            get("/live") {
                call.respond(HealthLiveResponse(status = "UP", service = "SoulLogger"))
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
                    HealthReadyResponse(
                        status = when (status.status) {
                            HealthStatus.Status.HEALTHY -> "UP"
                            else -> "DOWN"
                        },
                        checks = status.checks.mapValues { it.value.status.toSimpleString() }
                    )
                )
            }
            
            get("/buffer") {
                val status = healthManager.check()
                val bufferCheck = status.checks["buffer"]
                call.respond(
                    HealthDetailResponse(
                        status = bufferCheck?.status?.toSimpleString() ?: "UNKNOWN",
                        message = bufferCheck?.message ?: "",
                        details = bufferCheck?.details?.mapValues { convertToHealthDetailValue(it.value) } ?: emptyMap()
                    )
                )
            }
            
            get("/disk") {
                val status = healthManager.check()
                val diskCheck = status.checks["disk"]
                call.respond(
                    HealthDetailResponse(
                        status = diskCheck?.status?.toSimpleString() ?: "UNKNOWN",
                        message = diskCheck?.message ?: "",
                        details = diskCheck?.details?.mapValues { convertToHealthDetailValue(it.value) } ?: emptyMap()
                    )
                )
            }
            
            get("/queue") {
                val status = healthManager.check()
                val queueCheck = status.checks["queue"]
                call.respond(
                    HealthDetailResponse(
                        status = queueCheck?.status?.toSimpleString() ?: "UNKNOWN",
                        message = queueCheck?.message ?: "",
                        details = queueCheck?.details?.mapValues { convertToHealthDetailValue(it.value) } ?: emptyMap()
                    )
                )
            }
        }
    }
}

private fun convertToHealthDetailValue(value: Any?): HealthDetailValue {
    return when (value) {
        is String -> HealthDetailValue.StringValue(value)
        is Int -> HealthDetailValue.IntValue(value)
        is Long -> HealthDetailValue.LongValue(value)
        is Double -> HealthDetailValue.DoubleValue(value)
        is Boolean -> HealthDetailValue.BooleanValue(value)
        null -> HealthDetailValue.Empty()
        else -> HealthDetailValue.StringValue(value.toString())
    }
}
