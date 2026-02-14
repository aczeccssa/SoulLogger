package com.lestere.opensource.logger.events

/**
 * Sealed class representing log events that can be published via the event bus.
 * This decouples the core logging module from analysis functionality.
 */
public sealed class LogEvent {
    /**
     * Emitted when a new log file is created.
     * @param previousPath The path of the previous log file, or null if this is the first file.
     * @param currentPath The path of the newly created log file.
     */
    public data class LogFileCreated(val previousPath: String?, val currentPath: String) : LogEvent()

    /**
     * Emitted when the application is shutting down.
     * @param currentPath The path of the current log file to generate final report.
     */
    public data class ApplicationShutdown(val currentPath: String) : LogEvent()
}
