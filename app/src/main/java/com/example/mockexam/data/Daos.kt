package com.example.mockexam.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface QuestionSetDao {
    @Query("SELECT * FROM question_sets ORDER BY importedAtMillis DESC")
    suspend fun getAll(): List<QuestionSetEntity>

    @Query("SELECT * FROM question_sets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): QuestionSetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: QuestionSetEntity)
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE setId = :setId ORDER BY questionId COLLATE NOCASE")
    suspend fun getForSet(setId: String): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE setId = :setId AND questionId = :questionId LIMIT 1")
    suspend fun getOne(setId: String, questionId: String): QuestionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<QuestionEntity>)

    @Query("SELECT COUNT(*) FROM questions WHERE setId = :setId")
    suspend fun countForSet(setId: String): Int
}

@Dao
interface BookmarkDao {
    @Query("""
        SELECT q.* FROM questions q
        INNER JOIN question_bookmarks b
          ON q.setId = b.setId AND q.questionId = b.questionId
        WHERE (:setId IS NULL OR q.setId = :setId)
        ORDER BY b.createdAtMillis DESC
    """)
    suspend fun getBookmarkedQuestions(setId: String?): List<QuestionEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM question_bookmarks WHERE setId = :setId AND questionId = :questionId)")
    suspend fun isBookmarked(setId: String, questionId: String): Boolean

    @Query("SELECT questionId FROM question_bookmarks WHERE setId = :setId")
    suspend fun getBookmarkedIds(setId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: QuestionBookmarkEntity)

    @Query("DELETE FROM question_bookmarks WHERE setId = :setId AND questionId = :questionId")
    suspend fun delete(setId: String, questionId: String)
}

@Dao
interface AttemptDao {
    @Query("SELECT * FROM test_attempts ORDER BY startedAtMillis DESC")
    suspend fun getAll(): List<TestAttemptEntity>

    @Query("SELECT * FROM test_attempts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TestAttemptEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: TestAttemptEntity)

    @Update
    suspend fun update(item: TestAttemptEntity)
}

@Dao
interface AttemptQuestionDao {
    @Query("SELECT * FROM attempt_questions WHERE attemptId = :attemptId ORDER BY position")
    suspend fun getForAttempt(attemptId: String): List<AttemptQuestionEntity>

    @Query("SELECT * FROM attempt_questions WHERE attemptId = :attemptId AND position = :position LIMIT 1")
    suspend fun getOne(attemptId: String, position: Int): AttemptQuestionEntity?

    @Update
    suspend fun update(item: AttemptQuestionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<AttemptQuestionEntity>)
}

@Dao
interface ReviewDao {
    @Query("""
        SELECT aq.selectedAnswer AS selectedAnswer, t.startedAtMillis AS attemptedAtMillis
        FROM attempt_questions aq
        INNER JOIN test_attempts t ON t.id = aq.attemptId
        WHERE t.status = 'SUBMITTED'
          AND aq.setId = :setId
          AND aq.questionId = :questionId
          AND (
              :responseFilter = 'ANY'
              OR (:responseFilter = 'INCORRECT' AND aq.selectedAnswer IS NOT NULL AND aq.selectedAnswer != aq.correctAnswer)
              OR (:responseFilter = 'SKIPPED' AND aq.selectedAnswer IS NULL)
          )
        ORDER BY t.startedAtMillis DESC
        LIMIT 1
    """)
    suspend fun getLatestResponse(setId: String, questionId: String, responseFilter: String): LatestQuestionResponse?

    @Query("""
        SELECT DISTINCT q.* FROM questions q
        INNER JOIN attempt_questions aq
          ON q.setId = aq.setId AND q.questionId = aq.questionId
        INNER JOIN test_attempts t ON t.id = aq.attemptId
        WHERE t.status = 'SUBMITTED'
          AND aq.selectedAnswer IS NOT NULL
          AND aq.selectedAnswer != aq.correctAnswer
          AND (:setId IS NULL OR q.setId = :setId)
        ORDER BY q.setId, q.questionId
    """)
    suspend fun getIncorrectQuestions(setId: String?): List<QuestionEntity>

    @Query("""
        SELECT DISTINCT q.* FROM questions q
        INNER JOIN attempt_questions aq
          ON q.setId = aq.setId AND q.questionId = aq.questionId
        INNER JOIN test_attempts t ON t.id = aq.attemptId
        WHERE t.status = 'SUBMITTED'
          AND aq.selectedAnswer IS NULL
          AND (:setId IS NULL OR q.setId = :setId)
        ORDER BY q.setId, q.questionId
    """)
    suspend fun getSkippedQuestions(setId: String?): List<QuestionEntity>
}
