package com.arisoli.parcheggiscaleacheck

data class BotMessage(
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromBot: Boolean = true
)
