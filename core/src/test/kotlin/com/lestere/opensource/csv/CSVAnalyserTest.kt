package com.lestere.opensource.csv

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Paths

class CSVAnalyserTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `test analysisCSVToHtmlWithAnalyzedFooter creates nested directories and file`() {
        val df = dataFrameOf("c1", "c2", "c3", "c4", "c5", "c6")(
            "v1", "v2", "v3", "v4", "v5", "Call 127.0.0.1 --> /path"
        )

        val nestedPath = tempDir.resolve("nested/dir")
        val resultPath = CSVAnalyser.analysisCSVToHtmlWithAnalyzedFooter(df, nestedPath)

        assertTrue(Files.exists(resultPath), "Result file should exist")
        assertTrue(Files.exists(nestedPath), "Parent directory should exist")
        assertTrue(resultPath.toString().endsWith(CSVAnalyser.FULLY_RESULT_HTML_NAME), "Filename should be correct")
    }

    @Test
    fun `test analysisCSVToHtmlWithAnalyzedFooter handles existing file`() {
        val df = dataFrameOf("c1", "c2", "c3", "c4", "c5", "c6")(
            "v1", "v2", "v3", "v4", "v5", "Call 127.0.0.1 --> /path"
        )

        val output = tempDir.resolve(CSVAnalyser.FULLY_RESULT_HTML_NAME)
        Files.createFile(output)
        assertTrue(Files.exists(output), "File should exist before call")

        val resultPath = CSVAnalyser.analysisCSVToHtmlWithAnalyzedFooter(df, tempDir)

        assertTrue(Files.exists(resultPath), "Result file should exist")
    }

    @Test
    fun `test analysisCSVToHtmlWithAnalyzedFooter handles path with no parent`() {
        val df = dataFrameOf("c1", "c2", "c3", "c4", "c5", "c6")(
            "v1", "v2", "v3", "v4", "v5", "Call 127.0.0.1 --> /path"
        )

        // This test case would throw NPE in the old logic because it.resolve(FULLY_RESULT_HTML_NAME).parent
        // would be null if the path itself is empty/null-like.
        // Paths.get("") has no parent.

        val emptyPath = Paths.get("")
        // Ensure that it doesn't throw NPE or other exception.
        // We'll catch and verify no exception is thrown.
        try {
            CSVAnalyser.analysisCSVToHtmlWithAnalyzedFooter(df, emptyPath)
        } catch (e: Exception) {
            fail("Should not throw an exception for empty path: ${e.message}")
        } finally {
            Files.deleteIfExists(Paths.get(CSVAnalyser.FULLY_RESULT_HTML_NAME))
        }
    }
}
