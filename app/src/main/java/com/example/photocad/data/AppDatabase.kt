package com.example.photocad.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class SiteSummary(
    val id: Long,
    val name: String,
    val address: String,
    val description: String,
    val blueprintCount: Int,
    val pointCount: Int,
    val thumbnailPath: String?
)

@Dao
interface SiteDao {
    @Insert suspend fun insert(site: Site): Long
    @Query("SELECT * FROM sites") fun getAll(): Flow<List<Site>>
    @Query("SELECT * FROM sites WHERE id = :id") suspend fun getById(id: Long): Site?
    @Query("UPDATE sites SET name = :name, address = :address, description = :description WHERE id = :id")
    suspend fun update(id: Long, name: String, address: String, description: String): Int
    @Query("DELETE FROM sites WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT COUNT(*) FROM sites") fun siteCount(): Flow<Int>
    @Query(
        """
        SELECT sites.id AS id, sites.name AS name, sites.address AS address, sites.description AS description,
               (SELECT COUNT(*) FROM drawings WHERE drawings.siteId = sites.id) AS blueprintCount,
               (SELECT COUNT(*) FROM points INNER JOIN drawings ON points.drawingId = drawings.id WHERE drawings.siteId = sites.id) AS pointCount,
               (SELECT filePath FROM drawings WHERE drawings.siteId = sites.id ORDER BY drawings.id ASC LIMIT 1) AS thumbnailPath
        FROM sites
        ORDER BY sites.id DESC
        """
    )
    fun getAllSummaries(): Flow<List<SiteSummary>>
}

data class DrawingSummary(
    val id: Long,
    val name: String,
    val filePath: String,
    val siteId: Long,
    val description: String,
    val pointCount: Int
)

@Dao
interface DrawingDao {
    @Insert suspend fun insert(drawing: Drawing): Long
    @Query("SELECT * FROM drawings WHERE siteId = :siteId") fun getBySite(siteId: Long): Flow<List<Drawing>>
    @Query("SELECT * FROM drawings WHERE id = :id") suspend fun getById(id: Long): Drawing?
    @Query("UPDATE drawings SET name = :name, description = :description WHERE id = :id")
    suspend fun update(id: Long, name: String, description: String): Int
    @Query("DELETE FROM drawings WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT COUNT(*) FROM drawings") fun drawingCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM drawings WHERE filePath = :path") suspend fun countByFilePath(path: String): Int
    @Query(
        """
        SELECT drawings.*, (SELECT COUNT(*) FROM points WHERE points.drawingId = drawings.id) AS pointCount
        FROM drawings WHERE siteId = :siteId
        """
    )
    fun getBySiteWithPointCount(siteId: Long): Flow<List<DrawingSummary>>
}

@Dao
interface PointDao {
    @Insert suspend fun insert(point: Point): Long
    @Query("SELECT * FROM points WHERE drawingId = :drawingId ORDER BY id ASC") fun getByDrawing(drawingId: Long): Flow<List<Point>>
    @Query("SELECT * FROM points WHERE drawingId = :drawingId AND pageNumber = :pageNumber ORDER BY id ASC")
    fun getByDrawingPage(drawingId: Long, pageNumber: Int): Flow<List<Point>>
    @Query("SELECT * FROM points WHERE id = :id") suspend fun getById(id: Long): Point?
    @Query("UPDATE points SET comment = :comment WHERE id = :id")
    suspend fun updateComment(id: Long, comment: String): Int
    @Query("UPDATE points SET x = :x, y = :y WHERE id = :id")
    suspend fun updatePosition(id: Long, x: Float, y: Float): Int
    @Query("UPDATE points SET colorIndex = :colorIndex WHERE id = :id")
    suspend fun updateColor(id: Long, colorIndex: Int): Int
    @Query("DELETE FROM points WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM points WHERE drawingId = :drawingId") suspend fun deleteByDrawing(drawingId: Long)
    @Query("SELECT COUNT(*) FROM points") fun pointCount(): Flow<Int>
}

data class ReportPhotoRow(
    val photoId: Long,
    val pointId: Long,
    val pageNumber: Int,
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
    @Query("SELECT COUNT(*) FROM photos WHERE filePath = :path") suspend fun countByFilePath(path: String): Int
    @Query("""
        SELECT photos.id AS photoId, photos.pointId, points.pageNumber, photos.filePath,
               photos.description, points.comment AS pointComment
        FROM photos INNER JOIN points ON photos.pointId = points.id
        WHERE points.drawingId = :drawingId
        ORDER BY points.id ASC, photos.id ASC
    """)
    suspend fun getReportRows(drawingId: Long): List<ReportPhotoRow>
}

@Database(entities = [Site::class, Drawing::class, Point::class, Photo::class], version = 6, exportSchema = true)
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
                        DatabaseMigrations.MIGRATION_3_4, DatabaseMigrations.MIGRATION_4_5,
                        DatabaseMigrations.MIGRATION_5_6
                    )
                    .build().also { INSTANCE = it }
            }
    }
}
