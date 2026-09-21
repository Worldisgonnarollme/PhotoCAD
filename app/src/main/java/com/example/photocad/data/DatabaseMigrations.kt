package com.example.photocad.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE points ADD COLUMN comment TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE photos ADD COLUMN description TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_points_drawingId ON points(drawingId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_pointId ON photos(pointId)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE drawings ADD COLUMN description TEXT NOT NULL DEFAULT ''")
        }
    }

    /** Existing drawings didn't belong to any object; group them under one default site so nothing is lost. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS sites (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, address TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '')")
            db.execSQL("INSERT INTO sites (id, name, address, description) VALUES (1, 'Мои чертежи', '', '')")
            db.execSQL("ALTER TABLE drawings ADD COLUMN siteId INTEGER NOT NULL DEFAULT 1")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_drawings_siteId ON drawings(siteId)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE points ADD COLUMN colorIndex INTEGER NOT NULL DEFAULT 0")
        }
    }
}
