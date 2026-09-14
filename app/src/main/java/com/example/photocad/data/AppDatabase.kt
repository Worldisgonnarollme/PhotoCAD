// бд

package com.example.photocad.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DrawingDao {
    @Insert suspend fun insert(drawing: Drawing): Long
    @Query("SELECT * FROM drawings") fun getAll(): Flow<List<Drawing>>
}

@Dao
interface PointDao {
    @Insert suspend fun insert(point: Point): Long
    @Query("SELECT * FROM points WHERE drawingId = :drawingId") fun getByDrawing(drawingId: Long): Flow<List<Point>>
}

@Dao
interface PhotoDao {
    @Insert suspend fun insert(photo: Photo): Long
    @Query("SELECT * FROM photos WHERE pointId = :pointId") fun getByPoint(pointId: Long): Flow<List<Photo>>
}

@Database(entities = [Drawing::class, Point::class, Photo::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun drawingDao(): DrawingDao
    abstract fun pointDao(): PointDao
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "photocad.db")
                    .build().also { INSTANCE = it }
            }
    }
}