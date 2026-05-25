package com.mad.cc_campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.mad.cc_campusconnect.R
import com.mad.cc_campusconnect.databinding.ItemEventBinding
import com.mad.cc_campusconnect.models.Event

class EventAdapter(
    private val onItemClick: (Event) -> Unit = {}
) : ListAdapter<Event, EventAdapter.EventViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EventViewHolder(
        private val binding: ItemEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(event: Event) = with(binding) {
            tvTitle.text = event.title
            tvOrganizer.text = event.organizer
            tvDate.text = event.date
            tvLocation.text = event.location
            tvPrice.text = if (event.price.isBlank()) "Free" else event.price

            Glide.with(ivCover)
                .load(event.imageUrl.ifBlank { null })
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .centerCrop()
                .into(ivCover)

            root.setOnClickListener { onItemClick(event) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Event>() {
            override fun areItemsTheSame(old: Event, new: Event) = old.id == new.id
            override fun areContentsTheSame(old: Event, new: Event) = old == new
        }
    }
}