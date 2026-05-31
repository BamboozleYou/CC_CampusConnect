package com.mad.cc_campusconnect.feedback

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mad.cc_campusconnect.R
import com.mad.cc_campusconnect.databinding.SheetFeedbackBinding
import com.mad.cc_campusconnect.utils.UserRepository
import kotlinx.coroutines.launch

/**
 * Modern feedback sheet. Writes to {collection}/{eventId}/feedback/{uid}.
 * Reports completion to the host via the Fragment Result API (config-change safe).
 */
class FeedbackBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetFeedbackBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val collection: String by lazy { requireArguments().getString(ARG_COLLECTION, "events") }
    private val eventId: String by lazy { requireArguments().getString(ARG_EVENT_ID, "") }

    // Apply the dark, round-cornered bottom-sheet theme.
    override fun getTheme(): Int = R.style.ThemeOverlay_CC_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SheetFeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefillExistingFeedback()
        binding.btnSubmitFeedback.setOnClickListener { submit() }
    }

    /** If the user already reviewed, show their current rating/comment so this becomes an edit. */
    private fun prefillExistingFeedback() {
        val uid = auth.currentUser?.uid ?: return
        db.collection(collection).document(eventId)
            .collection("feedback").document(uid).get()
            .addOnSuccessListener { snap ->
                if (!isAdded || snap == null || !snap.exists()) return@addOnSuccessListener
                binding.rbFeedback.rating = (snap.getDouble("rating") ?: 0.0).toFloat()
                binding.etComment.setText(snap.getString("comment").orEmpty())
                binding.btnSubmitFeedback.text = "UPDATE FEEDBACK"
            }
    }

    private fun submit() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            // Not signed in: nothing to write to. Surface it rather than failing silently.
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                bundleOf(RESULT_OK to false, RESULT_MSG to "Please sign in to leave feedback")
            )
            dismiss()
            return
        }

        val rating = binding.rbFeedback.rating.toDouble()
        val comment = binding.etComment.text?.toString()?.trim().orEmpty()

        if (rating <= 0.0) {
            binding.rbFeedback.requestFocus()
            // light inline nudge; the host shows the success/error snackbar
            binding.tvSheetSubtitle.text = "Please tap a star rating first"
            return
        }

        binding.btnSubmitFeedback.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val user = UserRepository.getCurrentUser()
            val data = hashMapOf(
                "userName" to (user?.name ?: ""),
                "rating" to rating,            // stored as a Double
                "comment" to comment,
                "timestamp" to System.currentTimeMillis()
            )
            db.collection(collection).document(eventId)
                .collection("feedback").document(uid)
                .set(data)                      // doc id = uid → one review per user, updatable
                .addOnSuccessListener {
                    parentFragmentManager.setFragmentResult(
                        RESULT_KEY,
                        bundleOf(RESULT_OK to true, RESULT_MSG to "Thanks for your feedback!")
                    )
                    dismiss()
                }
                .addOnFailureListener { e ->
                    Log.e("Feedback", "write failed (check Firestore rules for /feedback)", e)
                    if (_binding != null) binding.btnSubmitFeedback.isEnabled = true
                    parentFragmentManager.setFragmentResult(
                        RESULT_KEY,
                        bundleOf(RESULT_OK to false, RESULT_MSG to "Couldn't save: ${e.message}")
                    )
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null   // avoid the classic fragment ViewBinding leak
    }

    companion object {
        const val RESULT_KEY = "feedback_result"
        const val RESULT_OK = "ok"
        const val RESULT_MSG = "msg"

        private const val ARG_COLLECTION = "arg_collection"
        private const val ARG_EVENT_ID = "arg_event_id"

        fun newInstance(collection: String, eventId: String) = FeedbackBottomSheet().apply {
            arguments = bundleOf(ARG_COLLECTION to collection, ARG_EVENT_ID to eventId)
        }
    }
}