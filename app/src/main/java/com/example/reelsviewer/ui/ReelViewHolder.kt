package com.example.reelsviewer.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.RecyclerView
import com.example.reelsviewer.R
import com.example.reelsviewer.data.CacheStatus
import com.example.reelsviewer.data.VideoItem
import com.example.reelsviewer.databinding.ItemReelPlayerBinding
import java.io.File

class ReelViewHolder(
    private val binding: ItemReelPlayerBinding,
    private val onLikeToggled: (VideoItem, Boolean) -> Unit,
    private val onOpenLikedGrid: () -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    private var player: ExoPlayer? = null
    private var currentItem: VideoItem? = null

    fun bind(item: VideoItem) {
        currentItem = item
        binding.textVideoTitle.text = item.filename
        val sizeMb = String.format("%.1f MB", item.uncompressedSize / (1024.0 * 1024.0))
        binding.textVideoDetails.text = "AES-256 Encrypted | $sizeMb"

        updateLikeState(item.isLiked)
        updateStatusUi(item.status)

        if (item.status == CacheStatus.READY && item.cachePath != null) {
            setupPlayer(item.cachePath!!)
        }

        setupGestures(item)
        setupClickListeners(item)
    }

    private fun updateStatusUi(status: CacheStatus) {
        when (status) {
            CacheStatus.EXTRACTING -> {
                binding.layoutLoading.visibility = View.VISIBLE
                binding.textLoadingStatus.text = "Decrypting stream..."
            }
            CacheStatus.READY -> {
                binding.layoutLoading.visibility = View.GONE
            }
            else -> {
                binding.layoutLoading.visibility = View.VISIBLE
                binding.textLoadingStatus.text = "Preparing..."
            }
        }
    }

    fun onCacheStatusChanged(status: CacheStatus, cachePath: String?) {
        updateStatusUi(status)
        if (status == CacheStatus.READY && cachePath != null && player == null) {
            setupPlayer(cachePath)
        }
    }

    private fun setupPlayer(filePath: String) {
        val context = binding.root.context
        if (player == null) {
            player = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                val mediaItem = MediaItem.fromUri(Uri.fromFile(File(filePath)))
                setMediaItem(mediaItem)
                prepare()
            }
            binding.playerView.player = player
        }
    }

    fun play() {
        player?.playWhenReady = true
    }

    fun pause() {
        player?.playWhenReady = false
    }

    fun releasePlayer() {
        player?.let {
            it.release()
            player = null
            binding.playerView.player = null
        }
    }

    private fun setupClickListeners(item: VideoItem) {
        binding.btnLike.setOnClickListener {
            val newState = !item.isLiked
            item.isLiked = newState
            updateLikeState(newState)
            if (newState) {
                animateHeartPop()
            }
            onLikeToggled(item, newState)
        }

        binding.btnShare.setOnClickListener {
            val context = binding.root.context
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Reel Video")
                putExtra(Intent.EXTRA_TEXT, "Watching encrypted reel: ${item.filename}")
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Reel"))
        }

        binding.btnLikedList.setOnClickListener {
            onOpenLikedGrid()
        }
    }

    private fun setupGestures(item: VideoItem) {
        val context = binding.root.context
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                player?.let {
                    it.playWhenReady = !it.playWhenReady
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!item.isLiked) {
                    item.isLiked = true
                    updateLikeState(true)
                    onLikeToggled(item, true)
                }
                animateHeartPop()
                return true
            }
        })

        binding.touchOverlay.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun updateLikeState(isLiked: Boolean) {
        binding.btnLike.setImageResource(
            if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart
        )
        binding.textLikeCount.text = if (isLiked) "Liked" else "Like"
    }

    private fun animateHeartPop() {
        binding.imageAnimatedHeart.visibility = View.VISIBLE
        binding.imageAnimatedHeart.scaleX = 0.2f
        binding.imageAnimatedHeart.scaleY = 0.2f
        binding.imageAnimatedHeart.alpha = 1.0f

        val scaleX = ObjectAnimator.ofFloat(binding.imageAnimatedHeart, View.SCALE_X, 0.2f, 1.2f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(binding.imageAnimatedHeart, View.SCALE_Y, 0.2f, 1.2f, 1.0f)
        val fadeOut = ObjectAnimator.ofFloat(binding.imageAnimatedHeart, View.ALPHA, 1.0f, 0.0f)
        fadeOut.startDelay = 500
        fadeOut.duration = 300

        val animatorSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 300
        }

        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                fadeOut.start()
            }
        })

        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                binding.imageAnimatedHeart.visibility = View.GONE
            }
        })

        animatorSet.start()
    }
}
