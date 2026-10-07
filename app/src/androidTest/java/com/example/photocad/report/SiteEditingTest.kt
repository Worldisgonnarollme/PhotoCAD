package com.example.photocad.report

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.photocad.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SiteEditingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun updatingSiteChangesNameAddressAndDescription() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val id = db.siteDao().insert(Site(name = "Старое название", address = "Старый адрес", description = "Старое описание"))
            val updated = db.siteDao().update(id, "Новое название", "Новый адрес", "Новое описание")
            assertEquals(1, updated)
            val site = db.siteDao().getById(id)!!
            assertEquals("Новое название", site.name)
            assertEquals("Новый адрес", site.address)
            assertEquals("Новое описание", site.description)
        } finally { db.close() }
    }
}
