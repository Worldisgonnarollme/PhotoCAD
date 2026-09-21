package com.example.photocad.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteDao {
    @Insert suspend fun insert(site: Site): Long
    @Query("SELECT * FROM sites") fun getAll(): Flow<List<Site>>
    @Query("SELECT * FROM sites WHERE id = :id") suspend fun getById(id: Long): Site?
    @Query("UPDATE sites SET name = :name, address = :address, description = :description WHERE id = :id")
    suspend fun update(id: Long, name: String, address: String, description: String): Int
    @Query("DELETE FROM sites WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface DrawingDao {
    @Insert suspend fun insert(drawing: Drawing): Long
    @Query("SELECT * FROM drawings WHERE siteId = :siteId") fun getBySite(siteId: Long): Flow<List<Drawing>>
    @Query("SELECT * FROM drawings WHERE id = :id") suspend fun getById(id: Long): Drawing?
    @Query("UPDATE drawings SET name = :name, description = :description WHERE id = :id")
    suspend fun update(id: Long, name: String, description: String): Int
    @Query("DELETE FROM drawings WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface PointDao {
    @Insert suspend fun insert(point: Point): Long
    @Query("SELECT * FROM points WHERE drawingId = :drawingId") fun getByDrawing(drawingId: Long): Flow<List<Point>>
    @Query("SELECT * FROM points WHERE id = :id") suspend fun getById(id: Long): Point?
    @Query("UPDATE points SET comment = :comment WHERE id = :id")
    suspend fun updateComment(id: Long, comment: String): Int
    @Query("UPDATE points SET x = :x, y = :y WHERE id = :id")
    suspend fun updatePosition(id: Long, x: Float, y: Float): Int
    @Query("UPDATE points SET colorIndex = :colorIndex WHERE id = :id")
    suspend fun updateColor(id: Long, colorIndex: Int): Int
    @Query("DELETE FROM points WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM points WHERE drawingId = :drawingId") suspend fun deleteByDrawing(drawingId: Long)
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
    @Query("DELETE FROM photos WHERE pointId = :pointId") suspend fun deleteByPoint(pointId: Long)
    @Query("""
        SELECT photos.id AS photoId, photos.pointId, photos.filePath,
               photos.description, points.comment AS pointComment
        FROM photos INNER JOIN points ON photos.pointId = points.id
        WHERE points.drawingId = :drawingId
        ORDER BY points.id ASC, photos.id ASC
    """)
    suspend fun getReportRows(drawingId: Long): List<ReportPhotoRow>
}

@Database(entities = [Site::class, Drawing::class, Point::class, Photo::class], version = 5, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun siteDao(): SiteDao
    abstract fun drawingDao(): DrawingDao
    abstract fun pointDao(): PointDao
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "photocad.db")
                    .addMigrations(
                        DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3,
                        DatabaseMigrations.MIGRATION_3_4, DatabaseMigrations.MIGRATION_4_5
                    )
                    .build().also { INSTANCE = it }
            }
    }
}
