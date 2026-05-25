package com.mad.cc_campusconnect

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.mad.cc_campusconnect.adapters.EventAdapter
import com.mad.cc_campusconnect.databinding.ActivityMainBinding
import com.mad.cc_campusconnect.models.Event
import com.mad.cc_campusconnect.utils.UserRepository
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var eventAdapter: EventAdapter
    private val db = FirebaseFirestore.getInstance()

    private enum class Tab { POSTERBOARD, OPPORTUNITIES }
    private var currentTab = Tab.POSTERBOARD


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupTabs()
        setupClickListeners()
        setupScrollBehavior()
        updateFabLabel()

        lifecycleScope.launch { updateFabVisibility() }
        loadEvents()
    }

    override fun onResume() {
        super.onResume()
        // Refresh on return from AddEventActivity (Phase 5)
        loadEvents()
    }

    // ============ Setup ============
    private fun updateFabLabel() {
        binding.fabAddEvent.text = if (currentTab == Tab.OPPORTUNITIES) {
            "Add Opportunity"
        } else {
            "Add Event"
        }
    }
    private fun setupRecyclerView() {
        eventAdapter = EventAdapter { event ->
            val intent = Intent(this, EventDetailActivity::class.java).apply {
                putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id)
                putExtra(EventDetailActivity.EXTRA_COLLECTION, collectionName())
            }
            startActivity(intent)
        }
        binding.rvEvents.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = eventAdapter
            setHasFixedSize(false)
        }
    }

    private fun setupTabs() {
        binding.tabPosterboard.setOnClickListener {
            selectTab(Tab.POSTERBOARD)
        }
        binding.tabOpportunities.setOnClickListener {
            selectTab(Tab.OPPORTUNITIES)
        }
    }

    private fun selectTab(tab: Tab) {
        if (currentTab == tab) return
        currentTab = tab
        binding.tabPosterboard.isChecked = (tab == Tab.POSTERBOARD)
        binding.tabOpportunities.isChecked = (tab == Tab.OPPORTUNITIES)
        loadEvents()
        updateFabLabel()
    }

    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            UserRepository.signOut()
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }

        binding.fabAddEvent.setOnClickListener {
            val intent = Intent(this, AddEventActivity::class.java)
            intent.putExtra(AddEventActivity.EXTRA_COLLECTION, collectionName())
            startActivity(intent)
        }
    }

    private fun setupScrollBehavior() {
        // ExtendedFAB shrinks on scroll-down, extends on scroll-up — Play Store style
        binding.rvEvents.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (binding.fabAddEvent.visibility != View.VISIBLE) return
                if (dy > 8 && binding.fabAddEvent.isExtended) {
                    binding.fabAddEvent.shrink()
                } else if (dy < -8 && !binding.fabAddEvent.isExtended) {
                    binding.fabAddEvent.extend()
                }
            }
        })
    }

    // ============ Data ============

    private suspend fun updateFabVisibility() {
        val user = UserRepository.getCurrentUser()
        binding.fabAddEvent.visibility =
            if (user?.isAdmin() == true) View.VISIBLE else View.GONE
    }

    private fun collectionName(): String =
        if (currentTab == Tab.POSTERBOARD) "events" else "opportunities"

    private fun loadEvents() {
        setLoading(true)
        db.collection(collectionName())
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                setLoading(false)
                val events = snapshot.toObjects(Event::class.java)
                eventAdapter.submitList(events)
                renderEmptyState(events.isEmpty())
            }
            .addOnFailureListener { e ->
                setLoading(false)
                renderEmptyState(true)
                Toast.makeText(
                    this,
                    "Failed to load: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============ UI State ============

    private fun setLoading(loading: Boolean) {
        binding.loadingBar.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            binding.tvEmpty.visibility = View.GONE
            binding.rvEvents.visibility = View.GONE
        } else {
            binding.rvEvents.visibility = View.VISIBLE
        }
    }

    private fun renderEmptyState(isEmpty: Boolean) {
        binding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvEvents.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
}