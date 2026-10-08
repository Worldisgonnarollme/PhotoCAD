package com.example.photocad.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sites")
data class Site(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "''") val address: String = "",
    @ColumnInfo(defaultValue = "''") val description: String = ""
)

/** Optional defaults for an album cover. A report may override every value. */
@Entity(
    tableName = "site_report_details",
    foreignKeys = [ForeignKey(entity = Site::class, parentColumns = ["id"], childColumns = ["siteId"], onDelete = ForeignKey.CASCADE)]
)
data class SiteReportDetails(
    @PrimaryKey val siteId: Long,
    @ColumnInfo(defaultValue = "''") val organizationName: String = "",
    @ColumnInfo(defaultValue = "''") val organizationAddress: String = "",
    @ColumnInfo(defaultValue = "''") val phone: String = "",
    @ColumnInfo(defaultValue = "''") val email: String = "",
    @ColumnInfo(defaultValue = "''") val inn: String = "",
    @ColumnInfo(defaultValue = "''") val kpp: String = "",
    @ColumnInfo(defaultValue = "''") val ogrn: String = "",
    @ColumnInfo(defaultValue = "''") val customer: String = "",
    @ColumnInfo(defaultValue = "''") val city: String = "",
    @ColumnInfo(defaultValue = "''") val year: String = "",
    @ColumnInfo(defaultValue = "''") val albumNumber: String = ""
)

@Entity(tableName = "drawings", indices = [Index("siteId")])
data class Drawing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val filePath: String,
    val siteId: Long,
    @ColumnInfo(defaultValue = "''") val description: String = ""
)

@Entity(tableName = "points", indices = [Index("drawingId")])
data class Point(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drawingId: Long,
    val x: Float,
    val y: Float,
    @ColumnInfo(defaultValue = "1") val pageNumber: Int = 1,
    @ColumnInfo(defaultValue = "''") val comment: String = "",
    @ColumnInfo(defaultValue = "0") val colorIndex: Int = 0,
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    // Fixed points stay put: dragging them on the drawing does nothing.
    @ColumnInfo(defaultValue = "0") val isFixed: Boolean = false
)

@Entity(tableName = "photos", indices = [Index("pointId")])
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pointId: Long,
    val filePath: String,
    // null: legacy/no individual description; empty string: explicitly cleared.
    val description: String? = null
)
