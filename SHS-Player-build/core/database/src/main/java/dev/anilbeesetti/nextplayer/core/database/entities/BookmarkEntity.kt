package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "video_uri")
    val videoUri: String,
    @ColumnInfo(name = "position")
    val position: Long,
    @ColumnInfo(name = "label")
    val label: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
)
