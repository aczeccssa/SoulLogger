@file:OptIn(ExperimentalTime::class)

package com.lestere.opensource.utils

import kotlinx.datetime.*
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.char
import kotlin.time.ExperimentalTime

/**
 * Central Standard Time -> CST, conforms to ISO 8601:2004, same as UTC+8
 * LifeMark develop on China, using `Beijing` time zone as same as `Asia/Shanghai`.
 * Is `Asia/Shanghai`
 * @see <a href="https://www.timeanddate.com/time/zones/cst">CST</a>
 */
internal val TimeZone.Companion.CST: TimeZone
    get() = of("Asia/Shanghai")

/**
 * Get the time of `1970-01-01T00:00:00Z`
 */
internal val LocalDateTime.Companion.ISO_ZERO
    get() = DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET.parse(ISO_ZERO_DATETIME).toLocalDateTime()

/**
 * Get the time of `1970-01-01T00:00:00Z`
 */
internal val Clock.Companion.ISO_ZERO
    get() = LocalDateTime.ISO_ZERO.toInstant(TimeZone.CST)

/**
 * Format for better read effective.
 *
 * Format: `yyyy.MM.dd'T'HH:mm:ss`
 */
internal val DateTimeFormat.Companion.READABLE_FORMAT
    get() = DateTimeComponents.Format {
        year()
        char('.')
        monthNumber()
        char('.')
        dayOfMonth()
        char('T')
        hour()
        char(':')
        minute()
        char(':')
        second()
    }

/**
 * Format: `yyyy.MM.dd'T'HH:mm:ss`
 */
fun Instant.toReadableString() = format(DateTimeFormat.READABLE_FORMAT)