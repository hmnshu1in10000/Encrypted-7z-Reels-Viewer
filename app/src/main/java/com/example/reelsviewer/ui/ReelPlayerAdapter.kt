package com.example.reelsviewer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.reelsviewer.data.VideoItem
import com.example.reelsviewer.databinding.ItemReelPlayerBinding

/**
 * ViewPager2 Adapter feeding video reels into ExoPlayer ViewHolders.
 */
class ReelPlayerAdapter(
    private val items: List<VideoItem>,
    private val onLikeToggled: (VideoItem, Boolean) -> Unit,
    private val onOpenLikedGrid: () -> Unit
) : RecyclerView.Adapter<ReelViewHolder>() {

    private val attachedHolders = mutableMapOf<Int, ReelViewHolder>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReelViewHolder {
        val binding = ItemReelPlayerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ReelViewHolder(binding, onLikeToggled, onOpenLikedGrid)
    }

    override fun onBindViewHolder(holder: ReelViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun onViewAttachedToWindow(holder: ReelViewHolder) {
        super.onViewAttachedToWindow(holder)
        val pos = holder.bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
            attachedHolders[pos] = holder
        }
    }

    override fun onViewDetachedFromWindow(holder: ReelViewHolder) {
        super.onViewDetachedFromWindow(holder)
        val pos = holder.bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
            attachedHolders.remove(pos)
        }
        holder.pause()
        holder.releasePlayer()
    }

    fun getHolderForPosition(position: Int): ReelViewHolder? {
        return attachedHolders[position]
    }

    override fun getItemCount(): Int = items.size
}
