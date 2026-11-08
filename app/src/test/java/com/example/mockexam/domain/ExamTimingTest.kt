package com.example.mockexam.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExamTimingTest {
    @Test
    fun totalDurationUsesSelectedQuestionCount() {
        assertEquals(300, ExamTiming.totalDurationSeconds(5, 60))
        assertEquals(1_500, ExamTiming.totalDurationSeconds(25, 60))
        assertEquals(9_000, ExamTiming.totalDurationSeconds(100, 90))
    }

    @Test
    fun rejectsInvalidQuestionCount() {
        assertThrows(IllegalArgumentException::class.java) {
            ExamTiming.totalDurationSeconds(0, 60)
        }
    }

    @Test
    fun rejectsInvalidSecondsPerQuestion() {
        assertThrows(IllegalArgumentException::class.java) {
            ExamTiming.totalDurationSeconds(5, 0)
        }
    }

    @Test
    fun rejectsTotalDurationAbove24Hours() {
        assertThrows(IllegalArgumentException::class.java) {
            ExamTiming.totalDurationSeconds(2_000, 60)
        }
    }
}
