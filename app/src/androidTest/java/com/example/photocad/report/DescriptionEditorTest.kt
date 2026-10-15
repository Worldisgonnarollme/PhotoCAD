package com.example.photocad.report

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.photocad.ui.PhotoDescriptionEditor
import org.junit.Rule
import org.junit.Test

class DescriptionEditorTest {
    @get:Rule val compose = createComposeRule()
    @Test fun untouchedFieldFollowsTheStoredTextButDirtyTextSurvivesReloads() {
        val saved = mutableStateOf<String?>("Сохранённое")
        compose.setContent { MaterialTheme {
            PhotoDescriptionEditor(1, saved.value, false) { }
        } }
        compose.onNodeWithText("Описание фотографии").assertTextContains("Сохранённое")
        compose.runOnIdle { saved.value = "Обновлённое" }
        compose.onNodeWithText("Описание фотографии").assertTextContains("Обновлённое")
        compose.onNodeWithText("Описание фотографии").performTextReplacement("Своя правка")
        compose.runOnIdle { saved.value = "Ещё одно" }
        compose.onNodeWithText("Описание фотографии").assertTextContains("Своя правка")
    }
}
