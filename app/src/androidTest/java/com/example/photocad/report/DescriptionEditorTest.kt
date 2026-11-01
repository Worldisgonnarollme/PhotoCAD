package com.example.photocad.report

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.photocad.ui.PhotoDescriptionEditor
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class DescriptionEditorTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fieldDisplaysAndUpdatesTheOwnersDraft() {
        val draft = mutableStateOf("Сохранённое")
        compose.setContent { MaterialTheme {
            PhotoDescriptionEditor(draft.value, false, onDraftChange = { draft.value = it })
        } }
        compose.onNodeWithText("Описание фотографии").assertTextContains("Сохранённое")
        compose.runOnIdle { draft.value = "Обновлённое" }
        compose.onNodeWithText("Описание фотографии").assertTextContains("Обновлённое")
        compose.onNodeWithText("Описание фотографии").performTextReplacement("Своя правка")
        compose.runOnIdle { assertEquals("Своя правка", draft.value) }
        compose.onNodeWithText("Описание фотографии").assertTextContains("Своя правка")
    }
}
