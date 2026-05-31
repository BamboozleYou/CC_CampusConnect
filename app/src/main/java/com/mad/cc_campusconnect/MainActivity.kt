package com.mad.cc_campusconnect
import android.graphics.Color
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import androidx.core.content.ContextCompat
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.mad.cc_campusconnect.adapters.EventAdapter
import com.mad.cc_campusconnect.databinding.ActivityMainBinding
import com.mad.cc_campusconnect.models.Event
import com.mad.cc_campusconnect.utils.UserRepository
import com.mad.cc_campusconnect.utils.showError
import com.mad.cc_campusconnect.utils.showSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.awaitAll
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var eventAdapter: EventAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private enum class Tab { POSTERBOARD, OPPORTUNITIES, ADMIN }
    private var currentTab = Tab.POSTERBOARD

    private var currentCategory: String? = null   // null = "All"
    private var isCurrentUserAdmin = false
    // Master copy of what Firestore returned for the current tab/category.
    private var fullEventList: List<Event> = emptyList()
    // Current search text (lowercased).
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets   // return the insets so children can still consume them if needed
        }

        setupRecyclerView()
        setupSwipeRefresh()
        setupChips()
        setupSearch()
        setupTabs()
        setupClickListeners()
        setupScrollBehavior()
        updateFabLabel()

        lifecycleScope.launch { initUserState() }   // admin flag + saved bookmarks
        loadEvents()
    }
    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchQuery = s?.toString().orEmpty()
                applySearchFilter()   // instant, no Firestore call
            }
        })
    }
    override fun onResume() {
        super.onResume()
        if (currentTab != Tab.ADMIN) loadEvents()
        lifecycleScope.launch { refreshSavedBookmarks() }
    }

    override fun onPause() {
        super.onPause()
        binding.shimmerLayout.stopShimmer()
    }

    /**
     * STEP A: pull real data from Firestore, then hand it to renderChart().
     * Charts the most recent events and how many registrations each has.
     */
    private fun loadRegistrationsChart() {
        lifecycleScope.launch {
            try {
                // 1) Get the events to chart (latest 6 by timestamp).
                val events = db.collection("events")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(6)
                    .get().await()
                    .toObjects(Event::class.java)

                if (events.isEmpty()) {
                    binding.adminPanel.registrationsChart.clear()
                    return@launch
                }

                // 2) For each event, count its registrations subcollection — in PARALLEL.
                //    async { } launches all the counts at once; awaitAll() waits for all.
                val counts: List<Int> = events.map { event ->
                    async {
                        db.collection("events").document(event.id)
                            .collection("registrations")
                            .count().get(AggregateSource.SERVER).await().count.toInt()
                    }
                }.awaitAll()

                // 3) Build chart entries (x = bar index, y = count) and labels (event titles).
                val entries = counts.mapIndexed { index, c -> BarEntry(index.toFloat(), c.toFloat()) }
                val labels = events.map { it.title }

                // 4) Render.
                renderChart(entries, labels)
            } catch (e: Exception) {
                binding.root.showError("Couldn't load chart: ${e.message}")
            }
        }
    }

    /**
     * STEP B: style + draw. Pure rendering — no data fetching here.
     */
    private fun renderChart(entries: List<BarEntry>, labels: List<String>) {
        val chart: BarChart = binding.adminPanel.registrationsChart
        val accent = ContextCompat.getColor(this, R.color.accent_teal)
        val axisTextColor = Color.parseColor("#CCCCCC")

        val dataSet = BarDataSet(entries, "Registrations").apply {
            color = accent
            valueTextColor = Color.WHITE
            valueTextSize = 12f
            setDrawValues(true)
        }

        chart.data = BarData(dataSet).apply { barWidth = 0.5f }

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textColor = axisTextColor
            setDrawGridLines(false)
            setDrawAxisLine(false)
            granularity = 1f
            valueFormatter = IndexAxisValueFormatter(labels)
            labelCount = labels.size
            labelRotationAngle = -20f          // angle long event titles so they don't overlap
        }
        chart.axisLeft.apply {
            textColor = axisTextColor
            setDrawGridLines(false)
            setDrawAxisLine(false)
            axisMinimum = 0f
        }
        chart.axisRight.isEnabled = false
        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setFitBars(true)
            setScaleEnabled(false)
            setExtraOffsets(8f, 8f, 8f, 16f)
            animateY(700)
            invalidate()
        }
    }

    /**
     * Builds and styles the "Registrations per Event" bar chart for the dark theme.
     * Currently fed with dummy data; see the marked block to swap in Firestore counts.
     */
    private fun setupRegistrationsChart() {
        val chart: BarChart = binding.adminPanel.registrationsChart
        val labels = listOf("Event A", "Event B", "Event C")
        val entries = listOf(
            BarEntry(0f, 45f),
            BarEntry(1f, 120f),
            BarEntry(2f, 80f)
        )
        // --------------------------------

        val accent = ContextCompat.getColor(this, R.color.accent_teal)
        val axisTextColor = Color.parseColor("#CCCCCC")  // light gray for dark theme

        val dataSet = BarDataSet(entries, "Registrations").apply {
            color = accent                       // cyan/teal bars to match the app accent
            valueTextColor = Color.WHITE         // count labels above each bar
            valueTextSize = 12f
            setDrawValues(true)
        }

        chart.data = BarData(dataSet).apply { barWidth = 0.5f }

        // ---- X axis: event names along the bottom ----
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textColor = axisTextColor
            setDrawGridLines(false)              // remove vertical grid lines
            setDrawAxisLine(false)
            granularity = 1f
            valueFormatter = IndexAxisValueFormatter(labels)
            labelCount = labels.size
        }

        // ---- Left axis: values; no grid lines, light text ----
        chart.axisLeft.apply {
            textColor = axisTextColor
            setDrawGridLines(false)              // remove horizontal grid lines
            setDrawAxisLine(false)
            axisMinimum = 0f                     // bars start from zero
        }

        // ---- Right axis: hidden ----
        chart.axisRight.isEnabled = false

        // ---- Chart-level cosmetics for the dark card ----
        chart.apply {
            description.isEnabled = false        // no "Description Label" in the corner
            legend.isEnabled = false             // single series — legend is noise
            setFitBars(true)                     // bars fit neatly within the view
            setScaleEnabled(false)               // no pinch-zoom; it's a summary, not explorable
            setExtraOffsets(8f, 8f, 8f, 12f)     // breathing room so labels aren't clipped
            animateY(700)                        // subtle grow-up animation on load
            invalidate()                         // redraw with the new data + styling
        }
    }
    // ============ One-time user state ============

    private suspend fun initUserState() {
        val user = UserRepository.getCurrentUser()
        isCurrentUserAdmin = user?.hasAdminAccess() == true   // was: user?.isAdmin()

        binding.tabAdmin.visibility = if (isCurrentUserAdmin) View.VISIBLE else View.GONE
        binding.fabAddEvent.visibility = if (isCurrentUserAdmin) View.VISIBLE else View.GONE

        eventAdapter.setSaved(user?.savedEvents ?: emptyList())
    }

    private suspend fun refreshSavedBookmarks() {
        val user = UserRepository.getCurrentUser() ?: return
        eventAdapter.setSaved(user.savedEvents)
    }

    // ============ Setup ============

    private fun updateFabLabel() {
        binding.fabAddEvent.text = if (currentTab == Tab.OPPORTUNITIES) "Add Opportunity" else "Add Event"
    }

    private fun setupRecyclerView() {
        eventAdapter = EventAdapter(
            onItemClick = { event ->
                val intent = Intent(this, EventDetailActivity::class.java).apply {
                    putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id)
                    putExtra(EventDetailActivity.EXTRA_COLLECTION, collectionName())
                }
                startActivity(intent)
            },
            onBookmarkClick = { event, save -> onBookmarkToggled(event, save) }
        )
        binding.rvEvents.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = eventAdapter
            setHasFixedSize(false)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent_teal))
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.bg_card))
        binding.swipeRefresh.setOnRefreshListener { loadEvents(isManualRefresh = true) }
    }

    private fun setupChips() {
        binding.chipGroupCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            currentCategory = when (checkedIds.firstOrNull()) {
                R.id.chipTechnical  -> "Technical"
                R.id.chipCultural   -> "Cultural"
                R.id.chipPlacements -> "Placements"
                else                -> null
            }
            binding.etSearch.setText("")   // also clears searchQuery via the watcher
            loadEvents()
        }
    }

    private fun setupTabs() {
        binding.tabPosterboard.setOnClickListener { selectTab(Tab.POSTERBOARD) }
        binding.tabOpportunities.setOnClickListener { selectTab(Tab.OPPORTUNITIES) }
        binding.tabAdmin.setOnClickListener { selectTab(Tab.ADMIN) }
    }

    private fun selectTab(tab: Tab) {
        if (currentTab == tab) return
        currentTab = tab
        binding.tabPosterboard.isChecked = (tab == Tab.POSTERBOARD)
        binding.tabOpportunities.isChecked = (tab == Tab.OPPORTUNITIES)
        binding.tabAdmin.isChecked = (tab == Tab.ADMIN)

        if (tab == Tab.ADMIN) {
            showAdminPanel(true)
            lifecycleScope.launch { loadAdminStats() }
            loadRegistrationsChart()
        } else {
            showAdminPanel(false)
            binding.etSearch.setText("")   // also clears searchQuery via the watcher
            loadEvents()
            updateFabLabel()
        }
    }
    /** Toggle between the feed (chips + list) and the admin dashboard panel. */
    /** Toggle between the feed (search + chips + list) and the admin dashboard panel. */
    private fun showAdminPanel(show: Boolean) {
        binding.adminPanel.root.visibility = if (show) View.VISIBLE else View.GONE
        binding.tilSearch.visibility = if (show) View.GONE else View.VISIBLE   // <-- hide search on admin
        binding.chipScroll.visibility = if (show) View.GONE else View.VISIBLE
        binding.swipeRefresh.visibility = if (show) View.GONE else View.VISIBLE
        binding.fabAddEvent.visibility =
            if (show || !isCurrentUserAdmin) View.GONE else View.VISIBLE
        if (show) {
            binding.shimmerLayout.stopShimmer()
            binding.shimmerLayout.visibility = View.GONE
            binding.tvEmpty.visibility = View.GONE
            // Clear any active search so it isn't lingering when they switch back
            binding.etSearch.setText("")
        }
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
        binding.btnSaved.setOnClickListener {
            startActivity(Intent(this, SavedEventsActivity::class.java))
        }
    }

    private fun setupScrollBehavior() {
        binding.rvEvents.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (binding.fabAddEvent.visibility != View.VISIBLE) return
                if (dy > 8 && binding.fabAddEvent.isExtended) binding.fabAddEvent.shrink()
                else if (dy < -8 && !binding.fabAddEvent.isExtended) binding.fabAddEvent.extend()
            }
        })
    }

    // ============ Data ============

    private fun collectionName(): String =
        if (currentTab == Tab.OPPORTUNITIES) "opportunities" else "events"

    private fun loadEvents(isManualRefresh: Boolean = false) {
        if (currentTab == Tab.ADMIN) return        // no feed on the admin tab
        if (!isManualRefresh) setLoading(true)

        var query: Query = db.collection(collectionName())
            .orderBy("timestamp", Query.Direction.DESCENDING)
        currentCategory?.let { query = query.whereEqualTo("category", it) }

        query.get()
            .addOnSuccessListener { snapshot ->
                setLoading(false)
                binding.swipeRefresh.isRefreshing = false
                fullEventList = snapshot.toObjects(Event::class.java)   // <-- master copy
                applySearchFilter()                                      // <-- render filtered view
            }
            .addOnFailureListener { e ->
                setLoading(false)
                binding.swipeRefresh.isRefreshing = false
                renderEmptyState(true)
                binding.root.showError("Failed to load: ${e.message}")
            }
    }
    /** Filters the master list by the current search text and updates the adapter. */
    private fun applySearchFilter() {
        val q = searchQuery.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            fullEventList
        } else {
            fullEventList.filter { event ->
                event.title.lowercase().contains(q) ||
                        event.organizer.lowercase().contains(q) ||
                        event.location.lowercase().contains(q)
            }
        }
        eventAdapter.submitList(filtered)
        renderEmptyState(filtered.isEmpty())
    }
    /** Feature 1: server-side count() aggregations, run concurrently. */
    private suspend fun loadAdminStats() {
        try {
            val (users, events, opps) = coroutineScope {
                val u = async { db.collection("users").count().get(AggregateSource.SERVER).await().count }
                val e = async { db.collection("events").count().get(AggregateSource.SERVER).await().count }
                val o = async { db.collection("opportunities").count().get(AggregateSource.SERVER).await().count }
                Triple(u.await(), e.await(), o.await())
            }
            binding.adminPanel.tvUsersCount.text = users.toString()
            binding.adminPanel.tvEventsCount.text = events.toString()
            binding.adminPanel.tvOppsCount.text = opps.toString()
        } catch (e: Exception) {
            binding.root.showError("Couldn't load stats: ${e.message}")
        }
    }

    /** Feature 4: persist a bookmark add/remove atomically. */
    private fun onBookmarkToggled(event: Event, save: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val change =
                    if (save) FieldValue.arrayUnion(event.id) else FieldValue.arrayRemove(event.id)
                db.collection("users").document(uid).update("savedEvents", change).await()
                binding.root.showSuccess(if (save) "Saved for later" else "Removed")
            } catch (e: Exception) {
                binding.root.showError("Couldn't update: ${e.message}")
            }
        }
    }

    // ============ UI State ============

    private fun setLoading(loading: Boolean) {
        if (loading) {
            binding.tvEmpty.visibility = View.GONE
            binding.rvEvents.visibility = View.GONE
            binding.shimmerLayout.visibility = View.VISIBLE
            binding.shimmerLayout.startShimmer()
        } else {
            binding.shimmerLayout.stopShimmer()
            binding.shimmerLayout.visibility = View.GONE
            binding.rvEvents.visibility = View.VISIBLE
        }
    }

    private fun renderEmptyState(isEmpty: Boolean) {
        binding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvEvents.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
}