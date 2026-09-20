package com.example.photocad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.photocad.ui.report.ReportScreen
import com.example.photocad.data.AppDatabase
import com.example.photocad.ui.DrawingListScreen
import com.example.photocad.ui.DrawingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getInstance(this) // создание бд при старте
        setContent {
            MaterialTheme {
                var selectedDrawingId by rememberSaveable { mutableStateOf<Long?>(null) }
                var reportOpen by rememberSaveable { mutableStateOf(false) }
                if (selectedDrawingId != null && reportOpen) {
                    ReportScreen(db, selectedDrawingId!!, onBack = { reportOpen = false })
                } else if (selectedDrawingId == null) {
                    DrawingListScreen(db = db, onOpenDrawing = { selectedDrawingId = it })
                } else {
                    DrawingScreen(db = db, drawingId = selectedDrawingId!!, onBack = { selectedDrawingId = null }, onReport = { reportOpen = true })
                }
            }
        }
    }
}