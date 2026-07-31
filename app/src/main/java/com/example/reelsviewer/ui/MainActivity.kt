package com.example.reelsviewer.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.reelsviewer.R
import com.example.reelsviewer.cache.ReelCacheManager
import com.example.reelsviewer.data.CacheStatus
import com.example.reelsviewer.data.SessionManager
import com.example.reelsviewer.data.VideoItem
import com.example.reelsviewer.data.db.AppDatabase
import com.example.reelsviewer.data.db.LikedReelEntity
import com.example.reelsviewer.databinding.ActivityMainBinding
import com.example.reelsviewer.extractor.SevenZipExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var videoItems: List<VideoItem> = emptyList()

    private var extractor: SevenZipExtractor? = null
    private var cacheManager: ReelCacheManager? = null
    private var adapter: ReelPlayerAdapter? = null

    private var previousPosition: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!SessionManager.isAuthenticated) {
            showPasswordDialog()
        } else {
            loadArchive()
        }
    }

    private fun showPasswordDialog() {
        val dialog = PasswordDialogFragment.newInstance()
        dialog.onUnlockListener = {
            loadArchive()
        }
        dialog.show(supportFragmentManager, PasswordDialogFragment.TAG)
    }

    private fun loadArchive() {
        val archivePath = SessionManager.archiveFilePath ?: return
        val rawPassword = SessionManager.rawPassword ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val passwordString = String(rawPassword)
            val ext = SevenZipExtractor(archivePath, passwordString)
            ext.initSevenZip(applicationContext)
            extractor = ext

            try {
                val scannedItems = ext.scanVideoItems()

                // Query encrypted Room database to mark liked videos
                val db = AppDatabase.getInstance(applicationContext, rawPassword)
                val likedEntities = db.likedReelDao().getAllLikedDirect()
                val likedIds = likedEntities.map { it.videoId }.toSet()

                scannedItems.forEach { item ->
                    if (likedIds.contains(item.videoId)) {
                        item.isLiked = true
                    }
                }

                videoItems = scannedItems
                val manager = ReelCacheManager(applicationContext, ext)
                cacheManager = manager

                withContext(Dispatchers.Main) {
                    setupViewPager()
                    observeCacheUpdates()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to unlock archive: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    showPasswordDialog()
                }
            }
        }
    }

    private fun setupViewPager() {
        adapter = ReelPlayerAdapter(
            items = videoItems,
            onLikeToggled = { item, isLiked ->
                toggleLikeState(item, isLiked)
            },
            onOpenLikedGrid = {
                openLikedReelsFragment()
            }
        )

        binding.viewPagerReels.adapter = adapter
        binding.viewPagerReels.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPagerReels.offscreenPageLimit = 1

        binding.viewPagerReels.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                if (previousPosition >= 0 && previousPosition != position) {
                    adapter?.getHolderForPosition(previousPosition)?.pause()
                }

                cacheManager?.onPageSelected(position, videoItems)

                val activeHolder = adapter?.getHolderForPosition(position)
                activeHolder?.play()

                previousPosition = position
            }
        })

        if (videoItems.isNotEmpty()) {
            cacheManager?.onPageSelected(0, videoItems)
        }
    }

    private fun observeCacheUpdates() {
        val manager = cacheManager ?: return
        lifecycleScope.launch {
            manager.cacheStatusFlow.collectLatest { (position, status) ->
                if (position in videoItems.indices) {
                    val item = videoItems[position]
                    val holder = adapter?.getHolderForPosition(position)
                    holder?.onCacheStatusChanged(status, item.cachePath)
                    if (status == CacheStatus.READY && position == binding.viewPagerReels.currentItem) {
                        holder?.play()
                    }
                }
            }
        }
    }

    private fun toggleLikeState(item: VideoItem, isLiked: Boolean) {
        val passphrase = SessionManager.rawPassword ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext, passphrase)
            if (isLiked) {
                db.likedReelDao().insertLiked(
                    LikedReelEntity(videoId = item.videoId, relativePath = item.relativePath)
                )
            } else {
                db.likedReelDao().deleteLiked(item.videoId)
            }
        }
    }

    private fun openLikedReelsFragment() {
        binding.fragmentContainer.visibility = View.VISIBLE
        val fragment = LikedReelsFragment.newInstance()
        fragment.onReelSelectListener = { targetVideoId ->
            val index = videoItems.indexOfFirst { it.videoId == targetVideoId }
            if (index >= 0) {
                binding.viewPagerReels.setCurrentItem(index, true)
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, LikedReelsFragment.TAG)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        cacheManager?.purgeAll(videoItems)
        SessionManager.clear()
        AppDatabase.clearInstance()
    }
}
