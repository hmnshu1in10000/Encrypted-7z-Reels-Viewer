package com.example.reelsviewer.cache

import android.content.Context
import com.example.reelsviewer.data.CacheStatus
import com.example.reelsviewer.data.VideoItem
import com.example.reelsviewer.extractor.SevenZipExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Pre-fetching & Cache Lifecycle Manager for Reels stream pipeline.
 * - Extracts active reel (N) to temp file in context.cacheDir
 * - Pre-fetches next reel (N + 1) in background
 * - Automatically purges reel (N - 2) from disk to preserve security and storage
 */
class ReelCacheManager(
    private val context: Context,
    private val extractor: SevenZipExtractor
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var currentJob: Job? = null

    private val _cacheStatusFlow = MutableSharedFlow<Pair<Int, CacheStatus>>(replay = 10)
    val cacheStatusFlow: SharedFlow<Pair<Int, CacheStatus>> = _cacheStatusFlow

    fun onPageSelected(position: Int, items: List<VideoItem>) {
        currentJob?.cancel()
        currentJob = scope.launch {
            // 1. Purge N - 2
            val purgeIndex = position - 2
            if (purgeIndex >= 0 && purgeIndex < items.size) {
                purgeItem(items[purgeIndex])
            }

            // 2. Ensure Active Reel (N) is ready
            if (position in items.indices) {
                val activeItem = items[position]
                if (activeItem.status != CacheStatus.READY) {
                    extractItem(position, activeItem)
                }
            }

            // 3. Pre-fetch Next Reel (N + 1)
            val nextIndex = position + 1
            if (nextIndex in items.indices) {
                val nextItem = items[nextIndex]
                if (nextItem.status == CacheStatus.LOCKED) {
                    extractItem(nextIndex, nextItem)
                }
            }
        }
    }

    private suspend fun extractItem(index: Int, item: VideoItem) {
        if (item.status == CacheStatus.READY && item.cachePath != null && File(item.cachePath!!).exists()) {
            return
        }

        item.status = CacheStatus.EXTRACTING
        _cacheStatusFlow.emit(Pair(index, CacheStatus.EXTRACTING))

        val tempFile = File(context.cacheDir, "reel_${item.videoId}.tmp")
        val success = extractor.extractVideoEntry(item.archiveIndex, tempFile)

        if (success && tempFile.exists()) {
            item.cachePath = tempFile.absolutePath
            item.status = CacheStatus.READY
            _cacheStatusFlow.emit(Pair(index, CacheStatus.READY))
        } else {
            item.status = CacheStatus.LOCKED
            _cacheStatusFlow.emit(Pair(index, CacheStatus.LOCKED))
        }
    }

    private suspend fun purgeItem(item: VideoItem) {
        item.cachePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        item.cachePath = null
        item.status = CacheStatus.PURGED
    }

    fun purgeAll(items: List<VideoItem>) {
        scope.launch {
            items.forEach { purgeItem(it) }
        }
    }
}
