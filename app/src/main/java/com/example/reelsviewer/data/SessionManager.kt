package com.example.reelsviewer.data

/**
 * In-Memory Session Manager storing credentials in RAM only.
 * Password is stored as a CharArray to allow explicit zeroing in memory when cleared.
 */
object SessionManager {
    var archiveFilePath: String? = null
    var rawPassword: CharArray? = null // Stored in RAM, erased on app kill/lock
    var isAuthenticated: Boolean = false

    fun clear() {
        rawPassword?.fill('\u0000')
        rawPassword = null
        isAuthenticated = false
        archiveFilePath = null
    }
}
