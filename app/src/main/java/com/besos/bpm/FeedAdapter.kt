package com.besos.bpm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class FeedAdapter(private val markerList: List<MarkerItem>) : RecyclerView.Adapter<FeedAdapter.FeedViewHolder>() {

    inner class FeedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val feedImage: ImageView = itemView.findViewById(R.id.feedImage)
        val feedDescription: TextView = itemView.findViewById(R.id.feedDescription)
        val feedDate: TextView = itemView.findViewById(R.id.feedDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_feed, parent, false)
        return FeedViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val marker = markerList[position]

        Glide.with(holder.itemView.context)
            .load(marker.imageUrl)
            .placeholder(R.drawable.ic_placeholder_image)
            .error(R.drawable.ic_placeholder_image)
            .into(holder.feedImage)

        holder.feedDescription.text = marker.description
        holder.feedDate.text = marker.date
    }

    override fun getItemCount(): Int = markerList.size
}
