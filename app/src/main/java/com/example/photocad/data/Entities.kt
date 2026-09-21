package com.example.photocad.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sites")
data class Site(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "''") val address: String = "",
    @ColumnInfo(defaultValue = "''") val description: String = ""
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
    @ColumnInfo(defaultValue = "''") val comment: String = "",
    @ColumnInfo(defaultValue = "0") val colorIndex: Int = 0
)

@Entity(tableName = "photos", indices = [Index("pointId")])
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pointId: Long,
    val filePath: String,
    // null: legacy/no individual description; empty string: explicitly cleared.
    val description: String? = null
)
