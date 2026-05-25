package com.mad.cc_campusconnect.models

import com.google.firebase.firestore.DocumentId

/**
 * Represents an event on Posterboard OR an entry under Opportunities.
 * Same schema for both — the collection name decides which tab it shows under.
 *
 * `id` is auto-populated by Firestore via @DocumentId; do NOT write it to the document.
 */
data class Event(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val organizer: String = "",
    val date: String = "",          // human-readable, e.g. "Sun, Jan 4, 2026"
    val location: String = "",
    val price: String = "",         // e.g. "₹500" or "Free"
    val imageUrl: String = "",
    val instagramUrl: String = "",
    val timestamp: Long = 0L        // used for ordering (newest first)
)