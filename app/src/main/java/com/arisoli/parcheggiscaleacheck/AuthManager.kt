package com.arisoli.parcheggiscaleacheck

import android.content.Context
import android.content.SharedPreferences

class AuthManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "telegram_auth_prefs",
        Context.MODE_PRIVATE
    )
    
    private val KEY_IS_AUTHENTICATED = "is_authenticated"
    private val KEY_USER_ID = "user_id"
    private val KEY_PHONE_NUMBER = "phone_number"
    private val KEY_FIRST_NAME = "first_name"
    private val KEY_LAST_NAME = "last_name"
    private val KEY_USERNAME = "username"
    private val KEY_START_SENT = "start_sent"
    
    fun isAuthenticated(): Boolean {
        return prefs.getBoolean(KEY_IS_AUTHENTICATED, false)
    }
    
    fun saveUser(user: TelegramUser) {
        prefs.edit().apply {
            putBoolean(KEY_IS_AUTHENTICATED, true)
            putLong(KEY_USER_ID, user.id)
            putString(KEY_PHONE_NUMBER, user.phoneNumber)
            putString(KEY_FIRST_NAME, user.firstName)
            putString(KEY_LAST_NAME, user.lastName)
            putString(KEY_USERNAME, user.username)
            apply()
        }
    }
    
    fun getUser(): TelegramUser? {
        if (!isAuthenticated()) return null
        
        return TelegramUser(
            id = prefs.getLong(KEY_USER_ID, 0),
            firstName = prefs.getString(KEY_FIRST_NAME, "") ?: "",
            lastName = prefs.getString(KEY_LAST_NAME, null),
            username = prefs.getString(KEY_USERNAME, null),
            phoneNumber = prefs.getString(KEY_PHONE_NUMBER, null)
        )
    }
    
    fun logout() {
        prefs.edit().clear().apply()
    }
    
    fun getPhoneNumber(): String? {
        return prefs.getString(KEY_PHONE_NUMBER, null)
    }

    fun hasSentStart(): Boolean {
        return prefs.getBoolean(KEY_START_SENT, false)
    }

    fun setStartSent(sent: Boolean = true) {
        prefs.edit().putBoolean(KEY_START_SENT, sent).apply()
    }
}
