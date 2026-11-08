package com.example.mockexam.domain

/**
 * Creates one continuous mock-test deadline from the number of selected
 * questions and the pacing allowance configured for each question.
 */
object ExamTiming {
    const val MAX_TEST_DURATION_SECONDS = 86_400

    fun totalDurationSeconds(questionCount: Int, secondsPerQuestion: Int): Int {
        require(questionCount > 0) { "Select at least one question." }
        require(secondsPerQuestion > 0) { "Time per question must be greater than zero." }
        val total = questionCount.toLong() * secondsPerQuestion.toLong()
        require(total in 1L..MAX_TEST_DURATION_SECONDS.toLong()) {
            "Calculated total duration must be between 1 second and 24 hours."
        }
        return total.toInt()
    }
}
