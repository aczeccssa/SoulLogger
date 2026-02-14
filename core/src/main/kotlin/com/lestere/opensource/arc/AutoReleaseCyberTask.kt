package com.lestere.opensource.arc

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import java.util.*
import kotlinx.datetime.Clock as KxClock

class AutoReleaseCyberTask private constructor(
    val id: UUID,
    val tag: AutoReleaseCyberTag,
    val timestamp: Long,
    val desc: String,
    val activity: () -> Unit
) {
    companion object {
        fun build(
            tag: AutoReleaseCyberTag,
            timestamp: Long,
            desc: String,
            task: () -> Unit
        ) = AutoReleaseCyberTask(tag, timestamp, desc, task)

        /**
         * @Mark: 1000ms = 1sec
         */
        fun build(
            desc: String,
            tag: AutoReleaseCyberTag,
            ms: Long,
            task: () -> Unit
        ) = AutoReleaseCyberTask(desc, tag, ms, task)
    }

    public constructor(
        tag: AutoReleaseCyberTag,
        timestamp: Long,
        desc: String,
        task: () -> Unit
    ) : this(UUID.randomUUID(), tag, timestamp, desc, task)

    private constructor(
        desc: String,
        tag: AutoReleaseCyberTag,
        ms: Long,
        task: () -> Unit
    ) : this(
        id = UUID.randomUUID(),
        tag = tag,
        timestamp = KxClock.System.now()
            .plus(ms, DateTimeUnit.MILLISECOND, TimeZone.currentSystemDefault())
            .toEpochMilliseconds(),
        desc = desc,
        activity = task
    )
}