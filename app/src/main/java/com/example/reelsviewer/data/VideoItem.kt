package com.example.reelsviewer.data

enum class CacheStatus { LOCKED, EXTRACTING, READY, PURGED }

/**
 * Data class representing a video reel entry inside the 7z archive.
 */
data class VideoItem(
    val videoId: String,          // SHA-256 hash of internal 7z path
    val relativePath: String,     // Full relative path inside archive
    val filename: String,         // Display filename
    val archiveIndex: Int,        // Central directory index
    val compressedSize: Long,
    val uncompressedSize: Long,
    var cachePath: String? = null,
    var status: CacheStatus = CacheStatus.LOCKED,
    var isLiked: Boolean = false
)
