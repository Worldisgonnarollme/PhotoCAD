package com.example.photocad.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DrawingDao {
    @Insert suspend fun insert(drawing: Drawing): Long
    @Query("SELECT * FROM drawings") fun getAll(): Flow<List<Drawing>>
    @Query("SELECT * FROM drawings WHERE id = :id") suspend fun getById(id: Long): Drawing?
}

@Dao
interface PointDao {
    @Insert suspend fun insert(point: Point): Long
    @Query("SELECT * FROM points WHERE drawingId = :drawingId") fun getByDrawing(drawingId: Long): Flow<List<Point>>
    @Query("SELECT * FROM points WHERE id = :id") suspend fun getById(id: Long): Point?
    @Query("UPDATE points SET comment = :comment WHERE id = :id")
    suspend fun updateComment(id: Long, comment: String): Int
}

data class ReportPhotoRow(
    val photoId: Long,
    val pointId: Long,
    val filePath: String,
    val description: String?,
    val pointComment: String
)

@Dao
interface PhotoDao {
    @Insert suspend fun insert(photo: Photo): Long
    @Query("SELECT * FROM photos WHERE pointId = :pointId") fun getByPoint(pointId: Long): Flow<List<Photo>>
    @Query("UPDATE photos SET description = :description WHERE id = :id")
    suspend fun updateDescription(id: Long, description: String): Int
    @Query("""
        SELECT photos.id AS photoId, photos.pointId, photos.filePath,
               photos.description, points.comment AS pointComment
        FROM photos INNER JOIN points ON photos.pointId = points.id
        WHERE points.drawingId = :drawingId
        ORDER BY points.id ASC, photos.id ASC
    """)
    suspend fun getReportRows(drawingId: Long): List<ReportPhotoRow>
}

@Database(entities = [Drawing::class, Point::class, Photo::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun drawingDao(): DrawingDao
    abstract fun pointDao(): PointDao
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "photocad.db")
                    .addMigrations(DatabaseMigrations.MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
