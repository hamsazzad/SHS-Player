package dev.anilbeesetti.nextplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.anilbeesetti.nextplayer.core.database.entities.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE video_uri = :videoUri ORDER BY position ASC")
    fun getBookmarksForVideo(videoUri: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("DELETE FROM bookmarks WHERE video_uri = :videoUri")
    suspend fun deleteBookmarksForVideo(videoUri: String)
}
