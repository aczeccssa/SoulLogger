package com.lestere.opensource.csv

import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.stream.Stream

internal object CSVGenerator {
    private fun escapeSpecialCharacters(source: String): String {
        var data = source
        var escapedData = data.replace("\\R".toRegex(), " ")
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"")
            escapedData = "\"" + data + "\""
        }
        return escapedData
    }

    private fun convertToCSV(data: Array<String>): String? =
        Stream.of(*data)
            .map(this::escapeSpecialCharacters)
            .collect(Collectors.joining(","))

    fun writeList(dataLines: List<Array<String>>, path: Path) {
        val csvContent = dataLines
            .stream()
            .map(::convertToCSV)
            .collect(Collectors.joining("\n"))
        Files.writeString(path, csvContent)
    }
}
