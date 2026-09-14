// объекты

package com.example.photocad.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drawings")
data class Drawing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val filePath: String
)

@Entity(tableName = "points")
data class Point(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drawingId: Long,
    val x: Float, // ширина изображения, 0..1
    val y: Float  // высота изображения, 0..1
)

@Entity(tableName = "photos")
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pointId: Long,
    val filePath: String
)
