package com.mad.cc_campusconnect

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mad.cc_campusconnect.databinding.ActivityEventDetailBinding
import com.mad.cc_campusconnect.feedback.FeedbackBottomSheet
import com.mad.cc_campusconnect.models.Event
import com.mad.cc_campusconnect.utils.UserRepository
import com.mad.cc_campusconnect.utils.showError
import com.mad.cc_campusconnect.utils.showSuccess
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.core.app.ShareCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class EventDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventDetailBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var eventId: String
    private lateinit var collection: String
    private var event: Event? = null
    private var isRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: run { finish(); return }
        collection = intent.getStringExtra(EXTRA_COLLECTION) ?: "events"

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRegister.setOnClickListener { toggleRegistration() }
        binding.btnInstagram.setOnClickListener { openInstagram() }
        binding.btnShare.setOnClickListener { shareEvent() }

        // Feedback is now collected in a bottom sheet. Open it on tap…
        binding.btnLeaveFeedback.setOnClickListener {
            FeedbackBottomSheet
                .newInstance(collection = collection, eventId = eventId)
                .show(supportFragmentManager, "feedback")
        }

        // …and listen once for the sheet's result so we can refresh the summary.
        supportFragmentManager.setFragmentResultListener(
            FeedbackBottomSheet.RESULT_KEY, this
        ) { _, bundle ->
            val ok = bundle.getBoolean(FeedbackBottomSheet.RESULT_OK)
            val msg = bundle.getString(FeedbackBottomSheet.RESULT_MSG).orEmpty()
            if (ok) {
                refreshFeedbackSummary()
                binding.root.showSuccess(msg)
            } else {
                binding.root.showError(msg)
            }
        }

        loadEvent()
    }

    // ============ Load ============

    private fun loadEvent() {
        binding.contentScroll.visibility = View.GONE
        binding.loadingBar.visibility = View.VISIBLE

        db.collection(collection).document(eventId).get()
            .addOnSuccessListener { snap ->
                val e = snap.toObject(Event::class.java)?.copy(id = snap.id)
                if (e == null) {
                    Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }
                event = e
                binding.loadingBar.visibility = View.GONE
                binding.contentScroll.visibility = View.VISIBLE
                displayEvent(e)
                refreshRegistrationState()
                refreshFeedbackSummary()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Load failed: ${it.message}", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun shareEvent() {
        val e = event ?: return    // event is loaded by loadEvent(); guard against early taps

        // Formatted message exactly as specified
        val message = "Hey, check out ${e.title} by ${e.organizer} at BMSCE! " +
                "Download Campus Connect to register."

        ShareCompat.IntentBuilder(this)
            .setType("text/plain")
            .setChooserTitle("Share ${e.title}")
            .setText(message)
            .startChooser()        // shows the system share sheet
    }
    private fun displayEvent(event: Event) {
        binding.tvTitle.text = event.title
        binding.tvOrganizer.text = event.organizer
        binding.tvDate.text = event.date
        binding.tvLocation.text = event.location
        binding.tvPrice.text = event.price.ifBlank { "Free" }

        Glide.with(this)
            .load(event.imageUrl.ifBlank { null })
            .placeholder(R.drawable.ic_image_placeholder)
            .error(R.drawable.ic_image_placeholder)
            .centerCrop()
            .into(binding.ivCover)

        binding.btnInstagram.visibility =
            if (event.instagramUrl.isNotBlank()) View.VISIBLE else View.GONE
    }

    // ============ Registration ============

    private fun refreshRegistrationState() {
        val uid = auth.currentUser?.uid ?: return
        db.collection(collection).document(eventId)
            .collection("registrations").get()
            .addOnSuccessListener { snap ->
                val count = snap.size()
                binding.tvRegistrationCount.text =
                    if (count == 1) "1 person registered" else "$count people registered"
                isRegistered = snap.documents.any { it.id == uid }
                renderRegisterButton()
            }
    }

    private fun renderRegisterButton() {
        if (isRegistered) {
            binding.btnRegister.text = "REGISTERED — TAP TO CANCEL"
            binding.btnRegister.icon = ContextCompat.getDrawable(this, R.drawable.ic_check)
            binding.btnRegister.iconTint =
                ContextCompat.getColorStateList(this, R.color.accent_teal)
            binding.btnRegister.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.accent_teal_dim)
            binding.btnRegister.setTextColor(
                ContextCompat.getColor(this, R.color.accent_teal)
            )
        } else {
            binding.btnRegister.text = "REGISTER NOW"
            binding.btnRegister.icon = null
            binding.btnRegister.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.accent_teal)
            binding.btnRegister.setTextColor(
                ContextCompat.getColor(this, R.color.bg_dark)
            )
        }
    }

    private fun toggleRegistration() {
        val uid = auth.currentUser?.uid ?: return
        binding.btnRegister.isEnabled = false

        val ref = db.collection(collection).document(eventId)
            .collection("registrations").document(uid)

        if (isRegistered) {
            ref.delete()
                .addOnSuccessListener {
                    binding.btnRegister.isEnabled = true
                    Toast.makeText(this, "Registration cancelled", Toast.LENGTH_SHORT).show()
                    refreshRegistrationState()
                }
                .addOnFailureListener { e ->
                    binding.btnRegister.isEnabled = true
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            lifecycleScope.launch {
                val user = UserRepository.getCurrentUser()
                val data = hashMapOf(
                    "userName" to (user?.name ?: ""),
                    "email" to (user?.email ?: auth.currentUser?.email ?: ""),
                    "timestamp" to System.currentTimeMillis()
                )
                ref.set(data)
                    .addOnSuccessListener {
                        binding.btnRegister.isEnabled = true
                        Toast.makeText(this@EventDetailActivity, "You're in!", Toast.LENGTH_SHORT).show()
                        refreshRegistrationState()
                    }
                    .addOnFailureListener { e ->
                        binding.btnRegister.isEnabled = true
                        Toast.makeText(this@EventDetailActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    // ============ Instagram ============

    private fun openInstagram() {
        val url = event?.instagramUrl?.trim().orEmpty()
        if (url.isEmpty()) return
        val normalized = if (url.startsWith("http")) url else "https://$url"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open link", Toast.LENGTH_SHORT).show()
        }
    }

    // ============ Feedback (summary only — input lives in FeedbackBottomSheet) ============

    private fun refreshFeedbackSummary() {
        db.collection(collection).document(eventId).collection("feedback").get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    binding.tvAvgRating.text = "No reviews yet"
                    return@addOnSuccessListener
                }
                val ratings = snap.documents.mapNotNull { it.getDouble("rating") }
                if (ratings.isEmpty()) {
                    binding.tvAvgRating.text = "No reviews yet"
                    return@addOnSuccessListener
                }
                val avg = ratings.average()
                val count = ratings.size
                binding.tvAvgRating.text = String.format(
                    Locale.getDefault(),
                    "%.1f ★ from %d %s",
                    avg, count, if (count == 1) "review" else "reviews"
                )
            }
    }

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_COLLECTION = "extra_collection"
    }
}