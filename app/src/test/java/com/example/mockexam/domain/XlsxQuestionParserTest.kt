package com.example.mockexam.domain

import com.example.mockexam.importer.ImportValidationException
import com.example.mockexam.importer.XlsxQuestionParser
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxQuestionParserTest {
    private val sample = File("src/main/assets/sample_questions.xlsx")

    @Test fun importsSampleRowsAndEmbeddedImage() {
        val images = createTempDir(prefix = "mcq-images-")
        val parsed = XlsxQuestionParser.parseWorkbook(sample, images)
        assertEquals(5, parsed.size)
        assertEquals("BIO-001", parsed.first().questionId)
        assertEquals("A", parsed.first().correctAnswer)
        assertNotNull(parsed.first().imagePath)
        assertTrue(File(parsed.first().imagePath!!).length() > 0L)
        assertTrue(parsed.drop(1).all { it.imagePath == null })
    }

    @Test fun rejectsInvalidCorrectAnswer() {
        val invalid = copyWithSheetChange { it.replaceFirst("<x:v>A</x:v>", "<x:v>Z</x:v>") }
        try {
            XlsxQuestionParser.parseWorkbook(invalid, createTempDir(prefix = "mcq-images-"))
            throw AssertionError("Expected invalid answer to be rejected")
        } catch (error: ImportValidationException) {
            assertTrue(error.issues.any { it.contains("Correct_Answer must be A, B, C or D") })
        } finally { invalid.delete() }
    }

    @Test fun rejectsDuplicateQuestionIds() {
        val invalid = copyWithSheetChange { it.replaceFirst("<x:v>BIO-002</x:v>", "<x:v>BIO-001</x:v>") }
        try {
            XlsxQuestionParser.parseWorkbook(invalid, createTempDir(prefix = "mcq-images-"))
            throw AssertionError("Expected duplicate ID to be rejected")
        } catch (error: ImportValidationException) {
            assertTrue(error.issues.any { it.contains("duplicate Question_ID 'BIO-001'") })
        } finally { invalid.delete() }
    }

    private fun copyWithSheetChange(change: (String) -> String): File {
        val output = File.createTempFile("invalid-mcq-", ".xlsx")
        ZipFile(sample).use { source ->
            ZipOutputStream(output.outputStream()).use { target ->
                val entries = source.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val data = source.getInputStream(entry).use { it.readBytes() }
                    val finalData = if (entry.name == "xl/worksheets/sheet1.xml") {
                        change(data.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
                    } else data
                    target.putNextEntry(ZipEntry(entry.name))
                    target.write(finalData)
                    target.closeEntry()
                }
            }
        }
        return output
    }
}
