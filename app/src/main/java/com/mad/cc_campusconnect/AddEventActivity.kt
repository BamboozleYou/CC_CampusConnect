package com.mad.cc_campusconnect

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.firestore.FirebaseFirestore
import com.mad.cc_campusconnect.databinding.ActivityAddEventBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


class AddEventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEventBinding
    private val db = FirebaseFirestore.getInstance()
    private var collectionName: String = "events"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        collectionName = intent.getStringExtra(EXTRA_COLLECTION) ?: "events"

        if (collectionName == "opportunities") {
            binding.tvTitle.text = "New Opportunity"
            binding.btnSubmit.text = "POST OPPORTUNITY"
            binding.tilTitle.hint = "Opportunity title"
        }

        setupClickListeners()
        setupImagePreview()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.etDate.setOnClickListener { showDatePicker() }
        binding.tilDate.setEndIconOnClickListener { showDatePicker() }
        binding.btnSubmit.setOnClickListener { submitEvent() }
    }

    // Live-preview: load whatever URL the admin types into the preview card.
    private fun setupImagePreview() {
        binding.etImageUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val url = s?.toString()?.trim().orEmpty()
                if (url.isEmpty()) {
                    binding.ivCover.visibility = View.GONE
                    binding.placeholderLayout.visibility = View.VISIBLE
                } else {
                    binding.placeholderLayout.visibility = View.GONE
                    binding.ivCover.visibility = View.VISIBLE
                    Glide.with(this@AddEventActivity)
                        .load(url)
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .centerCrop()
                        .into(binding.ivCover)
                }
            }
        })
    }
    /** Returns the category label for the currently-checked chip. */
    private fun selectedCategory(): String = when (binding.chipGroupCategory.checkedChipId) {
        R.id.chipCatCultural   -> "Cultural"
        R.id.chipCatPlacements -> "Placements"
        R.id.chipCatOther      -> "Other"
        else                   -> "Technical"   // chipCatTechnical / default
    }
    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select event date")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        picker.addOnPositiveButtonClickListener { millisUtc ->
            val formatter = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            binding.etDate.setText(formatter.format(Date(millisUtc)))
            binding.tilDate.error = null
        }

        picker.show(supportFragmentManager, "date_picker")
    }

    private fun submitEvent() {
        val title = binding.etTitle.text.toString().trim()
        val organizer = binding.etOrganizer.text.toString().trim()
        val date = binding.etDate.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()
        val price = binding.etPrice.text.toString().trim()
        val imageUrl = binding.etImageUrl.text.toString().trim()
        val instagramUrl = binding.etInstagram.text.toString().trim()

        if (!validateForm(title, organizer, date, location)) return

        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "SAVING…"

        val event = hashMapOf(
            "title" to title,
            "organizer" to organizer,
            "date" to date,
            "location" to location,
            "price" to price,
            "imageUrl" to imageUrl,
            "instagramUrl" to instagramUrl,
            "category" to selectedCategory(),
            "timestamp" to System.currentTimeMillis()
        )

        db.collection(collectionName).add(event)
            .addOnSuccessListener {
                Toast.makeText(this, "Published!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.btnSubmit.isEnabled = true
                binding.btnSubmit.text =
                    if (collectionName == "opportunities") "POST OPPORTUNITY" else "CREATE EVENT"
                Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun validateForm(
        title: String, organizer: String, date: String, location: String
    ): Boolean {
        binding.tilTitle.error = null
        binding.tilOrganizer.error = null
        binding.tilDate.error = null
        binding.tilLocation.error = null

        var valid = true
        if (title.isEmpty())     { binding.tilTitle.error = "Required";     valid = false }
        if (organizer.isEmpty()) { binding.tilOrganizer.error = "Required"; valid = false }
        if (date.isEmpty())      { binding.tilDate.error = "Required";      valid = false }
        if (location.isEmpty())  { binding.tilLocation.error = "Required";  valid = false }
        return valid
    }

    companion object {
        const val EXTRA_COLLECTION = "extra_collection"
    }
}