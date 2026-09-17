package com.xbertz.onsite.backend

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Holds the signed-in session (the JWT plus who it belongs to) in the platform's encrypted
 * storage, matching the phone's Keystore-backed treatment of secrets rather than a plain
 * SharedPreferences file.
 */
class SessionStore(context: Context) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "onsite_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val token: String? get() = prefs.getString(KEY_TOKEN, null)
    val email: String? get() = prefs.getString(KEY_EMAIL, null)
    val accountId: String? get() = prefs.getString(KEY_ACCOUNT_ID, null)
    val hasMigratedLocalData: Boolean get() = prefs.getBoolean(KEY_MIGRATED, false)

    fun save(token: String, email: String, accountId: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EMAIL, email)
            .putString(KEY_ACCOUNT_ID, accountId)
            .apply()
    }

    fun markMigrated() {
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_EMAIL = "email"
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_MIGRATED = "migrated_local_data"
    }
}
