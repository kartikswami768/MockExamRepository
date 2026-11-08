package com.example.mockexam.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        QuestionSetEntity::class,
        QuestionEntity::class,
        QuestionBookmarkEntity::class,
        TestAttemptEntity::class,
        AttemptQuestionEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionSetDao(): QuestionSetDao
    abstract fun questionDao(): QuestionDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun attemptDao(): AttemptDao
    abstract fun attemptQuestionDao(): AttemptQuestionDao
    abstract fun reviewDao(): ReviewDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE test_attempts ADD COLUMN secondsPerQuestion INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE attempt_questions ADD COLUMN visited INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE attempt_questions ADD COLUMN skipped INTEGER NOT NULL DEFAULT 0")
                database.execSQL(
                    """UPDATE attempt_questions
                       SET visited = 1
                       WHERE position <= (
                           SELECT currentPosition FROM test_attempts
                           WHERE test_attempts.id = attempt_questions.attemptId
                       )""".trimIndent()
                )
                database.execSQL(
                    """UPDATE test_attempts
                       SET secondsPerQuestion = timeLimitSeconds
                       WHERE timingMode = 'PER_QUESTION'""".trimIndent()
                )
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "mock_exam.db"
            ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
        }
    }
}
