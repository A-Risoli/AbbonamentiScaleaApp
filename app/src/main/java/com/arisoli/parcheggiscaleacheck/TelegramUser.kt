package com.arisoli.parcheggiscaleacheck

data class TelegramUser(
    val id: Long,
    val firstName: String,
    val lastName: String? = null,
    val username: String? = null,
    val phoneNumber: String? = null
)
