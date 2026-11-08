package com.example.mockexam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.mockexam.data.AppDatabase
import com.example.mockexam.data.ExamRepository
import com.example.mockexam.ui.MockExamApp
import com.example.mockexam.ui.MockExamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = ExamRepository(AppDatabase.getInstance(applicationContext), applicationContext)
        setContent {
            MockExamTheme {
                MockExamApp(repository)
            }
        }
    }
}
