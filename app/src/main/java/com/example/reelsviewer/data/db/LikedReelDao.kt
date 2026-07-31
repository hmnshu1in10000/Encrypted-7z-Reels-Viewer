package com.example.reelsviewer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for liked reels database table.
 */
@Dao
interface LikedReelDao {
    @Query("SELECT * FROM liked_reels ORDER BY likedAt DESC")
    fun getAllLiked(): Flow<List<LikedReelEntity>>

    @Query("SELECT * FROM liked_reels ORDER BY likedAt DESC")
    suspend fun getAllLikedDirect(): List<LikedReelEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_reels WHERE videoId = :videoId LIMIT 1)")
    suspend fun isLiked(videoId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLiked(entity: LikedReelEntity)

    @Query("DELETE FROM liked_reels WHERE videoId = :videoId")
    suspend fun deleteLiked(videoId: String)
}
