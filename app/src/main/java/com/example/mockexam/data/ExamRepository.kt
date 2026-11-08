package com.example.mockexam.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.mockexam.domain.ScoreSummary
import com.example.mockexam.domain.Scoring
import com.example.mockexam.domain.ScoringAnswer
import com.example.mockexam.importer.ParsedQuestionBank
import com.example.mockexam.importer.XlsxQuestionImporter
import java.util.UUID

 data class AttemptBundle(
    val attempt: TestAttemptEntity,
    val questions: List<AttemptQuestionEntity>
)

class ExamRepository(
    private val database: AppDatabase,
    private val appContext: Context
) {
    private val sets = database.questionSetDao()
    private val questions = database.questionDao()
    private val bookmarks = database.bookmarkDao()
    private val attempts = database.attemptDao()
    private val attemptQuestions = database.attemptQuestionDao()
    private val reviews = database.reviewDao()

    suspend fun importWorkbook(uri: Uri): ParsedQuestionBank {
        val parsed = XlsxQuestionImporter.parseUri(appContext, uri)
        database.withTransaction {
            sets.insert(
                QuestionSetEntity(
                    id = parsed.setId,
                    name = parsed.sourceFileName.removeSuffix(".xlsx").ifBlank { "Question bank" },
                    sourceFileName = parsed.sourceFileName,
                    importedAtMillis = System.currentTimeMillis(),
                    questionCount = parsed.questions.size
                )
            )
            questions.insertAll(parsed.questions)
        }
        return parsed
    }

    suspend fun getSets(): List<QuestionSetEntity> = sets.getAll()
    suspend fun getSet(id: String): QuestionSetEntity? = sets.getById(id)
    suspend fun getQuestions(setId: String): List<QuestionEntity> = questions.getForSet(setId)
    suspend fun getHistory(): List<TestAttemptEntity> = attempts.getAll()

    suspend fun createAttempt(
        setId: String,
        selectedQuestionIds: List<String>,
        testName: String,
        timingMode: String,
        timeLimitSeconds: Int,
        positiveMarks: Double,
        negativeMarks: Double,
        skippedMarks: Double,
        negativeMarkingEnabled: Boolean
    ): String {
        require(selectedQuestionIds.isNotEmpty()) { "Select at least one question." }
        require(timeLimitSeconds > 0) { "Time limit must be greater than zero." }
        require(positiveMarks > 0.0) { "Positive marks must be greater than zero." }
        require(negativeMarks >= 0.0 && skippedMarks >= 0.0) { "Negative and skipped marks cannot be negative." }
        val set = sets.getById(setId) ?: error("Question bank no longer exists.")
        val all = questions.getForSet(setId).associateBy { it.questionId }
        val selected = selectedQuestionIds.distinct().mapNotNull { all[it] }
        require(selected.isNotEmpty()) { "No selected questions were found." }
        val now = System.currentTimeMillis()
        val attemptId = UUID.randomUUID().toString()
        val isPerQuestion = timingMode == "PER_QUESTION"
        val attempt = TestAttemptEntity(
            id = attemptId,
            setId = setId,
            setName = set.name,
            testName = testName.ifBlank { set.name },
            startedAtMillis = now,
            submittedAtMillis = null,
            status = "IN_PROGRESS",
            timingMode = if (isPerQuestion) "PER_QUESTION" else "OVERALL",
            timeLimitSeconds = timeLimitSeconds,
            deadlineAtMillis = if (isPerQuestion) null else now + timeLimitSeconds * 1_000L,
            positiveMarks = positiveMarks,
            negativeMarks = negativeMarks,
            skippedMarks = skippedMarks,
            negativeMarkingEnabled = negativeMarkingEnabled,
            questionCount = selected.size,
            currentPosition = 0,
            elapsedSeconds = 0L,
            totalScore = 0.0,
            maxPossibleMarks = 0.0,
            correctCount = 0,
            incorrectCount = 0,
            skippedCount = 0,
            marksAwarded = 0.0,
            marksDeducted = 0.0,
            accuracyPercent = 0.0
        )
        val snapshots = selected.mapIndexed { index, q ->
            q.asAttemptQuestion(
                attemptId,
                index,
                if (isPerQuestion && index == 0) now + timeLimitSeconds * 1_000L else null
            )
        }
        database.withTransaction {
            attempts.insert(attempt)
            attemptQuestions.insertAll(snapshots)
        }
        return attemptId
    }

    suspend fun getAttemptBundle(attemptId: String): AttemptBundle? {
        val attempt = attempts.getById(attemptId) ?: return null
        return AttemptBundle(attempt, attemptQuestions.getForAttempt(attemptId))
    }

    suspend fun answerQuestion(attemptId: String, position: Int, answer: String) {
        require(answer in setOf("A", "B", "C", "D"))
        database.withTransaction {
            val attempt = attempts.getById(attemptId) ?: return@withTransaction
            if (attempt.status != "IN_PROGRESS") return@withTransaction
            val item = attemptQuestions.getOne(attemptId, position) ?: return@withTransaction
            if (item.timeExpired) return@withTransaction
            val now = System.currentTimeMillis()
            if (attempt.timingMode == "OVERALL" && attempt.deadlineAtMillis != null && attempt.deadlineAtMillis <= now) return@withTransaction
            if (attempt.timingMode == "PER_QUESTION" && item.questionDeadlineAtMillis != null && item.questionDeadlineAtMillis <= now) {
                if (item.selectedAnswer == null) attemptQuestions.update(item.copy(timeExpired = true))
                return@withTransaction
            }
            attemptQuestions.update(item.copy(selectedAnswer = answer))
        }
    }

    suspend fun clearAnswer(attemptId: String, position: Int) {
        database.withTransaction {
            val attempt = attempts.getById(attemptId) ?: return@withTransaction
            if (attempt.status != "IN_PROGRESS") return@withTransaction
            val item = attemptQuestions.getOne(attemptId, position) ?: return@withTransaction
            if (item.timeExpired) return@withTransaction
            val expired = attempt.timingMode == "PER_QUESTION" && item.questionDeadlineAtMillis != null && item.questionDeadlineAtMillis <= System.currentTimeMillis()
            attemptQuestions.update(item.copy(selectedAnswer = null, timeExpired = expired))
        }
    }

    suspend fun toggleFlag(attemptId: String, position: Int) {
        database.withTransaction {
            val item = attemptQuestions.getOne(attemptId, position) ?: return@withTransaction
            attemptQuestions.update(item.copy(flagged = !item.flagged))
        }
    }

    suspend fun moveTo(attemptId: String, position: Int) {
        database.withTransaction {
            val attempt = attempts.getById(attemptId) ?: return@withTransaction
            if (attempt.status != "IN_PROGRESS") return@withTransaction
            val target = position.coerceIn(0, attempt.questionCount - 1)
            val item = attemptQuestions.getOne(attemptId, target) ?: return@withTransaction
            var changedItem = item
            if (attempt.timingMode == "PER_QUESTION" && item.questionDeadlineAtMillis == null) {
                changedItem = item.copy(questionDeadlineAtMillis = System.currentTimeMillis() + attempt.timeLimitSeconds * 1_000L)
                attemptQuestions.update(changedItem)
            } else if (
                attempt.timingMode == "PER_QUESTION" &&
                item.selectedAnswer == null &&
                !item.timeExpired &&
                item.questionDeadlineAtMillis != null &&
                item.questionDeadlineAtMillis <= System.currentTimeMillis()
            ) {
                changedItem = item.copy(timeExpired = true)
                attemptQuestions.update(changedItem)
            }
            attempts.update(attempt.copy(currentPosition = target))
        }
    }

    /** Marks the current per-question timer as expired. Returns true when it was the final position. */
    suspend fun expireCurrentQuestion(attemptId: String): Boolean {
        return database.withTransaction {
            val attempt = attempts.getById(attemptId) ?: return@withTransaction true
            if (attempt.status != "IN_PROGRESS") return@withTransaction true
            val current = attemptQuestions.getOne(attemptId, attempt.currentPosition)
                ?: return@withTransaction true
            val now = System.currentTimeMillis()
            // Protect against a timer callback racing with question navigation.
            if (current.selectedAnswer != null || current.timeExpired ||
                current.questionDeadlineAtMillis == null || current.questionDeadlineAtMillis > now
            ) return@withTransaction false
            attemptQuestions.update(current.copy(timeExpired = true))
            if (attempt.currentPosition >= attempt.questionCount - 1) {
                true
            } else {
                val nextPosition = attempt.currentPosition + 1
                val next = attemptQuestions.getOne(attemptId, nextPosition)
                if (next != null && next.questionDeadlineAtMillis == null) {
                    attemptQuestions.update(next.copy(questionDeadlineAtMillis = System.currentTimeMillis() + attempt.timeLimitSeconds * 1_000L))
                }
                attempts.update(attempt.copy(currentPosition = nextPosition))
                false
            }
        }
    }

    suspend fun submitAttempt(attemptId: String): ScoreSummary? {
        return database.withTransaction {
            val attempt = attempts.getById(attemptId) ?: return@withTransaction null
            if (attempt.status == "SUBMITTED") {
                val q = attemptQuestions.getForAttempt(attemptId)
                return@withTransaction scoreFor(attempt, q)
            }
            val items = attemptQuestions.getForAttempt(attemptId)
            val summary = scoreFor(attempt, items)
            val now = System.currentTimeMillis()
            attempts.update(
                attempt.copy(
                    submittedAtMillis = now,
                    status = "SUBMITTED",
                    elapsedSeconds = ((now - attempt.startedAtMillis) / 1_000L).coerceAtLeast(0L),
                    totalScore = summary.totalScore,
                    maxPossibleMarks = summary.maxPossibleMarks,
                    correctCount = summary.correctCount,
                    incorrectCount = summary.incorrectCount,
                    skippedCount = summary.skippedCount,
                    marksAwarded = summary.marksAwarded + summary.skippedMarksAwarded,
                    marksDeducted = summary.marksDeducted,
                    accuracyPercent = summary.accuracyPercent
                )
            )
            summary
        }
    }

    private fun scoreFor(attempt: TestAttemptEntity, items: List<AttemptQuestionEntity>): ScoreSummary =
        Scoring.calculate(
            answers = items.map { ScoringAnswer(it.selectedAnswer, it.correctAnswer) },
            positiveMarks = attempt.positiveMarks,
            negativeMarks = attempt.negativeMarks,
            skippedMarks = attempt.skippedMarks,
            negativeMarkingEnabled = attempt.negativeMarkingEnabled
        )

    suspend fun isBookmarked(setId: String, questionId: String): Boolean = bookmarks.isBookmarked(setId, questionId)
    suspend fun getBookmarkedIds(setId: String): Set<String> = bookmarks.getBookmarkedIds(setId).toSet()

    suspend fun toggleBookmark(setId: String, questionId: String): Boolean {
        return if (bookmarks.isBookmarked(setId, questionId)) {
            bookmarks.delete(setId, questionId)
            false
        } else {
            bookmarks.insert(QuestionBookmarkEntity(setId, questionId, System.currentTimeMillis()))
            true
        }
    }

    suspend fun getReviewQuestions(type: String, setId: String?): List<QuestionEntity> = when (type) {
        "BOOKMARKED" -> bookmarks.getBookmarkedQuestions(setId)
        "INCORRECT" -> reviews.getIncorrectQuestions(setId)
        else -> reviews.getSkippedQuestions(setId)
    }
}
