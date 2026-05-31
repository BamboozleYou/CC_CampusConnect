package com.mad.cc_campusconnect.models

import com.google.firebase.firestore.PropertyName

/**
 * Represents a CampusConnect user (student or admin).
 * Default values let Firestore deserialize via the no-arg constructor.
 */
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "student",                 // "student" or "admin"
    // Maps the Firestore boolean field "admin" explicitly, so the mapper never
    // confuses it with the helper method below.
    @get:PropertyName("admin") @set:PropertyName("admin")
    var admin: Boolean = false,
    val savedEvents: List<String> = emptyList()    // bookmarks (from the previous guide)
) {
    /**
     * Admin if EITHER the boolean flag is true OR the role string says "admin".
     * Named hasAdminAccess() (not isAdmin()) so it can't collide with the `admin`
     * field during Firestore (de)serialization.
     */
    fun hasAdminAccess(): Boolean = admin || role.equals("admin", ignoreCase = true)
}