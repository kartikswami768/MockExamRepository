package com.example.mockexam.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {
    @Test fun correctIncorrectAndSkippedAreScored() {
        val result = Scoring.calculate(
            answers = listOf(
                ScoringAnswer("A", "A"),
                ScoringAnswer("B", "C"),
                ScoringAnswer(null, "D")
            ),
            positiveMarks = 4.0,
            negativeMarks = 1.0,
            skippedMarks = 0.0,
            negativeMarkingEnabled = true
        )
        assertEquals(3.0, result.totalScore, 0.0001)
        assertEquals(4.0, result.marksAwarded, 0.0001)
        assertEquals(1.0, result.marksDeducted, 0.0001)
        assertEquals(1, result.correctCount)
        assertEquals(1, result.incorrectCount)
        assertEquals(1, result.skippedCount)
        assertEquals(50.0, result.accuracyPercent, 0.0001)
        assertEquals(12.0, result.maxPossibleMarks, 0.0001)
    }

    @Test fun disablingNegativeMarkingDoesNotDeduct() {
        val result = Scoring.calculate(
            listOf(ScoringAnswer("D", "A")), 4.0, 1.0, 0.0, false
        )
        assertEquals(0.0, result.totalScore, 0.0001)
        assertEquals(0.0, result.marksDeducted, 0.0001)
    }

    @Test fun skippedMarksCanContributeToMaximum() {
        val result = Scoring.calculate(
            listOf(ScoringAnswer(null, "A")), 1.0, 0.25, 2.0, true
        )
        assertEquals(2.0, result.totalScore, 0.0001)
        assertEquals(2.0, result.maxPossibleMarks, 0.0001)
    }
}
