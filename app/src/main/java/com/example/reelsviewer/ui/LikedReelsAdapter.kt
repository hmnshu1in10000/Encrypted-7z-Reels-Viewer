package com.example.reelsviewer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.reelsviewer.data.db.LikedReelEntity
import com.example.reelsviewer.databinding.ItemLikedReelBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LikedReelsAdapter(
    private val items: List<LikedReelEntity>,
    private val onItemClick: (LikedReelEntity) -> Unit
) : RecyclerView.Adapter<LikedReelsAdapter.LikedViewHolder>() {

    class LikedViewHolder(val binding: ItemLikedReelBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LikedViewHolder {
        val binding = ItemLikedReelBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LikedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LikedViewHolder, position: Int) {
        val item = items[position]
        holder.binding.textLikedTitle.text = File(item.relativePath).name
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        holder.binding.textLikedDate.text = dateFormat.format(Date(item.likedAt))

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}
