package com.mad.cc_campusconnect

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.mad.cc_campusconnect.adapters.EventAdapter
import com.mad.cc_campusconnect.databinding.ActivitySavedEventsBinding
import com.mad.cc_campusconnect.models.Event
import com.mad.cc_campusconnect.utils.UserRepository
import com.mad.cc_campusconnect.utils.showSuccess
import com.mad.cc_campusconnect.utils.showError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SavedEventsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySavedEventsBinding
    private lateinit var adapter: EventAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Remember which collection each event came from, so taps open the right detail.
    private val collectionOf = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySavedEventsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        setupRecyclerView()
        loadSavedEvents()
    }

    private fun setupRecyclerView() {
        adapter = EventAdapter(
            onItemClick = { event ->
                val intent = Intent(this, EventDetailActivity::class.java).apply {
                    putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id)
                    putExtra(EventDetailActivity.EXTRA_COLLECTION, collectionOf[event.id] ?: "events")
                }
                startActivity(intent)
            },
            onBookmarkClick = { event, save -> onBookmarkToggled(event, save) }
        )
        binding.rvSaved.apply {
            layoutManager = LinearLayoutManager(this@SavedEventsActivity)
            adapter = this@SavedEventsActivity.adapter
        }
    }

    private fun loadSavedEvents() {
        binding.loadingBar.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val user = UserRepository.getCurrentUser()
                val savedIds = user?.savedEvents.orEmpty()

                if (savedIds.isEmpty()) {
                    showEmpty()
                    return@launch
                }

                // All saved ids are currently flagged saved → fill the heart.
                adapter.setSaved(savedIds)

                // Look in both collections; whereIn allows max 10 ids, so chunk.
                val found = mutableListOf<Event>()
                for (collection in listOf("events", "opportunities")) {
                    val chunks = savedIds.chunked(10).map { chunk ->
                        async {
                            db.collection(collection)
                                .whereIn(FieldPath.documentId(), chunk)
                                .get().await()
                                .documents.mapNotNull { d ->
                                    d.toObject(Event::class.java)?.copy(id = d.id)?.also {
                                        collectionOf[d.id] = collection
                                    }
                                }
                        }
                    }
                    chunks.awaitAll().forEach { found.addAll(it) }
                }

                binding.loadingBar.visibility = View.GONE
                if (found.isEmpty()) {
                    showEmpty()
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.rvSaved.visibility = View.VISIBLE
                    adapter.submitList(found)
                }
            } catch (e: Exception) {
                binding.loadingBar.visibility = View.GONE
                binding.root.showError("Couldn't load saved: ${e.message}")
                showEmpty()
            }
        }
    }

    /** Un-saving from this screen removes the id and drops the row. */
    private fun onBookmarkToggled(event: Event, save: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val change =
                    if (save) FieldValue.arrayUnion(event.id) else FieldValue.arrayRemove(event.id)
                db.collection("users").document(uid).update("savedEvents", change).await()
                binding.root.showSuccess(if (save) "Saved" else "Removed")
                if (!save) {
                    // Remove from the visible list immediately
                    val newList = adapter.currentList.filterNot { it.id == event.id }
                    adapter.submitList(newList)
                    if (newList.isEmpty()) showEmpty()
                }
            } catch (e: Exception) {
                binding.root.showError("Couldn't update: ${e.message}")
            }
        }
    }

    private fun showEmpty() {
        binding.loadingBar.visibility = View.GONE
        binding.rvSaved.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
    }
}