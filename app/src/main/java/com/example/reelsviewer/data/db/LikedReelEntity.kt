package com.example.reelsviewer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity storing user-liked reels persistent state.
 */
@Entity(tableName = "liked_reels")
data class LikedReelEntity(
    @PrimaryKey val videoId: String, // SHA-256 hash of internal 7z path
    val relativePath: String,
    val likedAt: Long = System.currentTimeMillis()
)
