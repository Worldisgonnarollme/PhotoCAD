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
    @Test fun untouchedFallbackRefreshesButDirtyTextSurvivesCommentChanges() {
        val comment = mutableStateOf("Старый комментарий")
        compose.setContent { MaterialTheme {
            PhotoDescriptionEditor(1, null, comment.value, false) { }
        } }
        compose.onNodeWithText("Описание фотографии").assertTextContains("Старый комментарий")
        compose.runOnIdle { comment.value = "Новый комментарий" }
        compose.onNodeWithText("Описание фотографии").assertTextContains("Новый комментарий")
        compose.onNodeWithText("Описание фотографии").performTextReplacement("Своя правка")
        compose.runOnIdle { comment.value = "Ещё один комментарий" }
        compose.onNodeWithText("Описание фотографии").assertTextContains("Своя правка")
    }
}
