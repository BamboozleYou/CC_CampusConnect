package com.mad.cc_campusconnect.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mad.cc_campusconnect.models.User
import kotlinx.coroutines.tasks.await

/**
 * Fetches and caches the currently-logged-in user's profile from Firestore.
 * Use UserRepository.getCurrentUser() from a coroutine.
 */
object UserRepository {

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    /** @return profile of the currently authenticated user, or null if not logged in / not found. */
    suspend fun getCurrentUser(): User? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val snap = db.collection("users").document(uid).get().await()
            snap.toObject(User::class.java)?.copy(uid = uid)
        } catch (e: Exception) {
            null
        }
    }

    fun signOut() {
        auth.signOut()
    }
}