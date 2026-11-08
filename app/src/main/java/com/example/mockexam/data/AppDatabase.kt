package com.example.mockexam.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        QuestionSetEntity::class,
        QuestionEntity::class,
        QuestionBookmarkEntity::class,
        TestAttemptEntity::class,
        AttemptQuestionEntity::class
    ],
    version = 1,
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
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "mock_exam.db"
            ).build().also { INSTANCE = it }
        }
    }
}
