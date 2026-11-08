package com.example.mockexam.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.mockexam.data.QuestionEntity
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

 data class ParsedQuestionBank(
    val setId: String,
    val sourceFileName: String,
    val questions: List<QuestionEntity>
)

/** SAF adapter: copies the selected document to cache, then delegates to the JVM-tested OOXML parser. */
object XlsxQuestionImporter {
    private const val MAX_WORKBOOK_BYTES = 30L * 1024L * 1024L

    fun parseUri(context: Context, uri: Uri): ParsedQuestionBank {
        val displayName = queryDisplayName(context, uri) ?: "question_bank.xlsx"
        if (!displayName.lowercase().endsWith(".xlsx")) {
            throw ImportValidationException(listOf("Please select an Excel .xlsx workbook. Legacy .xls files are not supported."))
        }
        val temp = File.createTempFile("mock_exam_import_", ".xlsx", context.cacheDir)
        val setId = UUID.randomUUID().toString()
        val imageDir = File(context.filesDir, "question_images/$setId")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw ImportValidationException(listOf("Android could not open the selected document."))
            input.use { source ->
                FileOutputStream(temp).use { target ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_WORKBOOK_BYTES) {
                            throw ImportValidationException(listOf("Workbook exceeds the 30 MB import limit."))
                        }
                        target.write(buffer, 0, read)
                    }
                }
            }
            val parsed = XlsxQuestionParser.parseWorkbook(temp, imageDir)
            val questions = parsed.map { item ->
                QuestionEntity(
                    setId = setId,
                    questionId = item.questionId,
                    question = item.question,
                    imagePath = item.imagePath,
                    optionA = item.optionA,
                    optionB = item.optionB,
                    optionC = item.optionC,
                    optionD = item.optionD,
                    correctAnswer = item.correctAnswer,
                    explanation = item.explanation
                )
            }
            if (questions.isEmpty()) throw ImportValidationException(listOf("No question rows were found. Add questions below the header row."))
            return ParsedQuestionBank(setId, displayName, questions)
        } catch (error: Exception) {
            imageDir.deleteRecursively()
            throw error
        } finally {
            temp.delete()
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (_: Exception) { null }
}
