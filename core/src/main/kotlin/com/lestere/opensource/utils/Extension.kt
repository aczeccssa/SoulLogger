package com.lestere.opensource.utils

import com.lestere.opensource.csv.ElementFrequency
import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.models.CodableException
import com.lestere.opensource.models.ResponseData
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

/// @Mark: DO NOT FORMATED THESE CODE!!!!!!!!!

/**
 * @param status [io.ktor.http.HttpStatusCode] Request response status code.
 * @param quota [ResponseData.Quota] Request quota.
 * @param error [Exception] Will transform the exception message to client.
 * @param main [Any] This parameter value will automatically convert to `null` if is object `Unit`.
 */
suspend inline fun <reified T : Any> ApplicationCall.respondRefiled(
    status: HttpStatusCode,
    quota: ResponseData.Quota?,
    error: CodableException?,
    main: T?
) {
    // Convert `Unit` to `null` if necessary.
    val mainValue = if (main is Unit) null else main
    // Logout exception when response
    if (SoulLogger.active && error !== null) {
        SoulLogger.create(
            level = SoulLogger.Level.ERROR,
            command = "${request.local.remoteHost}(${request.path()}) ${error.toMultiStackString()}"
        )
    }
    // Call source method to response.
    respond(status, ResponseData(status.value, quota, error, mainValue))
}

/**
 * @param status [io.ktor.http.HttpStatusCode] Request response status code.
 * @param main [Any] This parameter value will automatically convert to `null` if is object `Unit`.
 */
suspend inline fun <reified T : Any> ApplicationCall.respondRefiled(status: HttpStatusCode, main: T) =
    respondRefiled(status, null, null, main)

/**
 * @param status [io.ktor.http.HttpStatusCode] Request response status code
 * @param error [Exception] Will transform the exception message to client.
 * @param main [Any] This parameter value will automatically convert to `null` if is object `Unit`, we suggest using `Unit` to express the means of no main content response.
 */
suspend inline fun <reified T : Any> ApplicationCall.respondRefiled(
    status: HttpStatusCode,
    error: CodableException?,
    main: T
) = respondRefiled(status, null, error, main)

/**
 * @param builder [ResponseData.ResponseBuilder] Response data builder
 */
suspend inline fun <reified T : Any> ApplicationCall.respondRefiled(
    builder: ResponseData.ResponseBuilder<T>.() -> Unit
) {
    val data = ResponseData.ResponseBuilder<T>()
    builder(data)
    respondRefiled(data.status, data.quota, data.error, data.main)
}

// Extension function for ReentrantLock to simplify locking

/**
 * The block will run in with the reentrant lock.
 * @param block [() -> Unit] The block of code to execute within the lock.
 */
internal fun ReentrantLock.withLock(block: () -> Unit) {
    lock(this) {
        block()
    }
}

/**
 * The block will run in with the reentrant lock.
 * @param lock [java.util.concurrent.locks.ReentrantLock] The reentrant lock to use.
 * @param block [() -> Unit] The block of code to execute within the lock.
 */
internal fun lock(lock: ReentrantLock, block: () -> Unit) {
    lock.lock()
    try {
        block()
    } finally {
        lock.unlock()
    }
}

/**
 * Convert the exception to a multi-line string with stack trace.
 */
fun Throwable.toMultiStackString() =
    "${toString()}\n${"\t".repeat(4)}${this.stackTrace.toList().joinToString("\n    ")}"

/**
 *
 */
public fun UUID.toLcsSecString() = this
    .toString()
    .lowercase()
    .replace("-", "")

/**
 *
 */
internal fun <T> List<T>.findMostFrequentWithCount() =
    groupBy { it }
        .maxByOrNull { it.value.size }
        ?.let { item ->
            ElementFrequency(item.key, item.value.size)
        }

/**
 * @param notEmpty [T] The value returned when the string is not empty.
 */
fun <T> String.isNotEmptyOrNull(notEmpty: (it: String) -> T): T? = if (this.isNotEmpty()) notEmpty(this) else null
