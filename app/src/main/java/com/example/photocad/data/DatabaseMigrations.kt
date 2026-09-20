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
}
