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
    private val onItemClick: (Event) -> Unit = {},
    // Reports the new desired state: true = just saved, false = just removed
    private val onBookmarkClick: (Event, Boolean) -> Unit = { _, _ -> }
) : ListAdapter<Event, EventAdapter.EventViewHolder>(DIFF) {

    // Set of currently-bookmarked event ids; drives outlined vs filled heart.
    private val savedIds = mutableSetOf<String>()

    /** Replace the saved set (e.g. after loading the user's profile) and refresh hearts. */
    fun setSaved(ids: Collection<String>) {
        savedIds.clear()
        savedIds.addAll(ids)
        notifyDataSetChanged()   // small lists: fine. Hearts aren't part of Event equality,
        // so DiffUtil wouldn't catch this change otherwise.
    }

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

            // --- Bookmark state ---
            val isSaved = savedIds.contains(event.id)
            ivBookmark.setImageResource(
                if (isSaved) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
            ivBookmark.setOnClickListener {
                val nowSaved = !savedIds.contains(event.id)
                // Optimistic local update so the UI flips instantly
                if (nowSaved) savedIds.add(event.id) else savedIds.remove(event.id)
                notifyItemChanged(bindingAdapterPosition)
                onBookmarkClick(event, nowSaved)   // host persists to Firestore
            }

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