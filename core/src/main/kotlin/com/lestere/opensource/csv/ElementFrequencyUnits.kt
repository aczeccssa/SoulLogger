package com.lestere.opensource.csv

import com.lestere.opensource.models.CodableException
import kotlinx.serialization.Serializable

@Serializable
internal data class ElementFrequency<T>(val element: T, val frequency: Int)

@Serializable
internal data class RequestPrompt(val ip: ElementFrequency<String>?, val path: ElementFrequency<String>?) {
    fun toHtml(): String = CSVAnalyser.generateTable(
        headers = listOf("Request IP", "Times", "Request path", "Times"),
        rows = listOf(
            listOf(
                ip?.element.toString(),
                ip?.frequency?.toString() ?: "0",
                path?.element.toString(),
                path?.frequency?.toString() ?: "0"
            )
        )
    )
}

@Serializable
internal data class ExceptionPrompt(
    val request: ElementFrequency<CodableException>?,
    val run: ElementFrequency<CodableException>?
) {
    fun toHtml(): String {
        val requestLine = listOf(
            request?.element?.code.toString(),
            request?.element?.message.toString(),
            request?.frequency?.toString() ?: "0"
        )
        val arg1 = run?.element?.code.toString()
        val arg2 = run?.element?.message.toString()
        val arg3 = run?.frequency?.toString() ?: "0"
        val runLine = listOf(arg1, arg2, arg3)
        return CSVAnalyser.generateTable(
            headers = listOf("Responding code", "Message", "Times", "Running Code", "Message", "Times"),
            rows = listOf(requestLine + runLine)
        )
    }
}
