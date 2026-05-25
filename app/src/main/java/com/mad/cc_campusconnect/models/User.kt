package com.mad.cc_campusconnect.models

/**
 * Represents a CampusConnect user (student or admin).
 * Default values required so Firestore can deserialize via no-arg constructor.
 */
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "student"   // "student" or "admin"
) {
    fun isAdmin(): Boolean = role.equals("admin", ignoreCase = true)
}