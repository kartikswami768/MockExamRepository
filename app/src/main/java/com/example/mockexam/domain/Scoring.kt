package com.example.mockexam.domain

data class ScoringAnswer(
    val selectedAnswer: String?,
    val correctAnswer: String
)

data class ScoreSummary(
    val totalScore: Double,
    val maxPossibleMarks: Double,
    val correctCount: Int,
    val incorrectCount: Int,
    val skippedCount: Int,
    val marksAwarded: Double,
    val marksDeducted: Double,
    val skippedMarksAwarded: Double,
    val accuracyPercent: Double
)

object Scoring {
    fun calculate(
        answers: List<ScoringAnswer>,
        positiveMarks: Double,
        negativeMarks: Double,
        skippedMarks: Double,
        negativeMarkingEnabled: Boolean
    ): ScoreSummary {
        require(positiveMarks > 0.0) { "Positive marks must be greater than zero." }
        require(negativeMarks >= 0.0) { "Negative marks cannot be negative." }
        require(skippedMarks >= 0.0) { "Skipped marks cannot be negative." }

        val correct = answers.count { it.selectedAnswer != null && it.selectedAnswer.equals(it.correctAnswer, true) }
        val incorrect = answers.count { it.selectedAnswer != null && !it.selectedAnswer.equals(it.correctAnswer, true) }
        val skipped = answers.size - correct - incorrect
        val awarded = correct * positiveMarks
        val deducted = if (negativeMarkingEnabled) incorrect * negativeMarks else 0.0
        val skippedAwarded = skipped * skippedMarks
        val maximum = answers.size * maxOf(positiveMarks, skippedMarks, 0.0)
        val attempted = correct + incorrect
        val accuracy = if (attempted == 0) 0.0 else correct.toDouble() * 100.0 / attempted

        return ScoreSummary(
            totalScore = awarded - deducted + skippedAwarded,
            maxPossibleMarks = maximum,
            correctCount = correct,
            incorrectCount = incorrect,
            skippedCount = skipped,
            marksAwarded = awarded,
            marksDeducted = deducted,
            skippedMarksAwarded = skippedAwarded,
            accuracyPercent = accuracy
        )
    }
}
