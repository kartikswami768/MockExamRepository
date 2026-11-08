package com.example.mockexam.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "question_sets")
data class QuestionSetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceFileName: String,
    val importedAtMillis: Long,
    val questionCount: Int
)

@Entity(
    tableName = "questions",
    primaryKeys = ["setId", "questionId"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("setId")]
)
data class QuestionEntity(
    val setId: String,
    val questionId: String,
    val question: String,
    val imagePath: String?,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctAnswer: String,
    val explanation: String
)

@Entity(
    tableName = "question_bookmarks",
    primaryKeys = ["setId", "questionId"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["setId", "questionId"],
            childColumns = ["setId", "questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("setId", "questionId")]
)
data class QuestionBookmarkEntity(
    val setId: String,
    val questionId: String,
    val createdAtMillis: Long
)

@Entity(tableName = "test_attempts", indices = [Index("startedAtMillis"), Index("status")])
data class TestAttemptEntity(
    @PrimaryKey val id: String,
    val setId: String,
    val setName: String,
    val testName: String,
    val startedAtMillis: Long,
    val submittedAtMillis: Long?,
    val status: String,
    val timingMode: String,
    val timeLimitSeconds: Int,
    val deadlineAtMillis: Long?,
    val positiveMarks: Double,
    val negativeMarks: Double,
    val skippedMarks: Double,
    val negativeMarkingEnabled: Boolean,
    val questionCount: Int,
    val currentPosition: Int,
    val elapsedSeconds: Long,
    val totalScore: Double,
    val maxPossibleMarks: Double,
    val correctCount: Int,
    val incorrectCount: Int,
    val skippedCount: Int,
    val marksAwarded: Double,
    val marksDeducted: Double,
    val accuracyPercent: Double
)

@Entity(
    tableName = "attempt_questions",
    primaryKeys = ["attemptId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = TestAttemptEntity::class,
            parentColumns = ["id"],
            childColumns = ["attemptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("attemptId"), Index("setId", "questionId")]
)
data class AttemptQuestionEntity(
    val attemptId: String,
    val position: Int,
    val setId: String,
    val questionId: String,
    val question: String,
    val imagePath: String?,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctAnswer: String,
    val explanation: String,
    val selectedAnswer: String?,
    val flagged: Boolean,
    val questionDeadlineAtMillis: Long?,
    val timeExpired: Boolean
)

fun QuestionEntity.asAttemptQuestion(attemptId: String, position: Int, deadlineAtMillis: Long?): AttemptQuestionEntity =
    AttemptQuestionEntity(
        attemptId = attemptId,
        position = position,
        setId = setId,
        questionId = questionId,
        question = question,
        imagePath = imagePath,
        optionA = optionA,
        optionB = optionB,
        optionC = optionC,
        optionD = optionD,
        correctAnswer = correctAnswer,
        explanation = explanation,
        selectedAnswer = null,
        flagged = false,
        questionDeadlineAtMillis = deadlineAtMillis,
        timeExpired = false
    )
