package com.lestere.opensource.arc

import com.lestere.opensource.logger.SoulLogger
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*

val AutoReleaseCyberPlugin = createApplicationPlugin(name = "AutoReleaseCyberPlugin") {
    on(MonitoringEvent(ApplicationStarted)) {
        AutoReleaseCyber.launch()
        SoulLogger.info("ARC(Auto Release Cyber) plugin enabled")
    }
}