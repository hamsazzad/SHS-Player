package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    @ColumnInfo(name = "video_uri")
    val videoUri: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
)
