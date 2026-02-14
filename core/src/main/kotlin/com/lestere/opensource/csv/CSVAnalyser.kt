package com.lestere.opensource.csv

import com.lestere.opensource.models.CodableException
import com.lestere.opensource.utils.findMostFrequentWithCount
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.forEachIndexed
import org.jetbrains.kotlinx.dataframe.io.DisplayConfiguration
import org.jetbrains.kotlinx.dataframe.io.toStandaloneHTML
import java.nio.file.Files
import java.nio.file.Path

internal object CSVAnalyser {
    internal const val FULLY_RESULT_HTML_NAME = "fully_result_from_csv.html"

    private val htmlConfig = DisplayConfiguration(
        rowsLimit = null,
        nestedRowsLimit = null,
        cellContentLimit = Int.MAX_VALUE,
        useDarkColorScheme = true
    )

    fun generateTable(headers: List<String>, rows: List<List<String>>): String {
        val style = """
        <style>
            * { font-family: 'Jetbrains Mono', Helvetica, Arial, sans-serif; }
            th, td {
                border: 1px solid #ddd;
                padding: 8px;
                text-align: left;
                font-size: 0.8rem;
            }
            th {
                background-color: #f2f2f2;
                font-weight: bold;
            }
            tr:nth-child(even) {
                background-color: #f9f9f9;
            }
        </style>
    """.trimIndent()

        val tableStyle = "style=\"border-collapse: collapse; margin-top: 1em;\""
        val headerRow = headers.joinToString("\n") { "<th><span>$it</span></th>" }
        val dataRows = rows.joinToString("\n") { row ->
            "<tr>\n" + row.joinToString("\n") { "<td><span>$it</span></td>" } + "\n</tr>"
        }

        return """
        <table $tableStyle>
            <thead>
                <tr>
                    $headerRow
                </tr>
            </thead>
            <tbody>
                $dataRows
            </tbody>
        </table>
        $style
    """.trimIndent()
    }

    private fun analysisCallCommand(commands: List<String>): RequestPrompt {
        val ipList = mutableListOf<String>()
        val requestList = mutableListOf<String>()
        commands.forEach { cell ->
            if (!cell.startsWith("Call ")) return@forEach

            val parts = cell.split(" --> ")

            val ipAddress = parts
                .getOrNull(0)
                ?.split(" ")
                ?.getOrNull(1)
                ?: return@forEach

            val requestPath = parts
                .getOrNull(1)
                ?: return@forEach

            ipList.add(ipAddress)
            requestList.add(requestPath)
        }
        val freqIp = ipList.findMostFrequentWithCount()
        val freqPath = requestList.findMostFrequentWithCount()
        return RequestPrompt(freqIp, freqPath)
    }

    private fun analysisExceptionCommand(commands: List<String>): ExceptionPrompt {
        val callExceptionList = mutableListOf<CodableException>()
        val runExceptionList = mutableListOf<CodableException>()
        commands.forEach { cell ->
            if (!cell.contains("CodableException")) return@forEach
            val startIndex = cell.indexOf("CodableException")
            if (startIndex > -1) {
                parseCodableException(cell.substring(startIndex))?.let {
                    if (startIndex > 0) callExceptionList.add(it) else runExceptionList.add(it)
                }
            } else return@forEach
        }
        val call = callExceptionList.map { it }.findMostFrequentWithCount()
        val run = runExceptionList.map { it }.findMostFrequentWithCount()
        return ExceptionPrompt(call, run)
    }

    private fun parseCodableException(input: String): CodableException? {
        val regex = Regex("CodableException code=(-\\d+), message=(.+)")
        return regex.find(input)?.let {
            val errorCode = it.groupValues.getOrNull(1)?.toLong() ?: return null
            val errorMessage = it.groupValues.getOrNull(2) ?: return null
            CodableException(errorCode, errorMessage)
        }
    }

    fun analysisCSVToHtmlWithAnalyzedFooter(df: DataFrame<*>, path: Path): Path {
        val output = path.resolve(FULLY_RESULT_HTML_NAME)
        // Write dataframe to html
        Files.createDirectories(output.parent) // @FIXME: Parent folder complete.
        Files.createFile(output)
        val file = output.toFile()
        val html = df.toStandaloneHTML(htmlConfig) {
            // c6 column
            df.getColumnOrNull(5)?.let { column ->
                val suffixedColumn: MutableList<String> = mutableListOf()
                column.forEachIndexed { i, row -> if (i > 0) suffixedColumn.add(row.toString()) }
                val call = analysisCallCommand(suffixedColumn).toHtml()
                val exception = analysisExceptionCommand(suffixedColumn).toHtml()
                "<h4>Log analysis report</h4><h5>Requests</h5>$call<h5>Exception</h5>$exception"
            }
        }

        // Generate json file
        html.writeHTML(file)
        return file.toPath()
    }
}
