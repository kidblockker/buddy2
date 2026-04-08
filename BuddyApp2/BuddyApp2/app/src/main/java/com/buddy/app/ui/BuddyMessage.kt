package com.buddy.app.ui

data class BuddyMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val category: String = "",   // for proactive messages: "news","tech",etc
    val timestamp: Long = System.currentTimeMillis()
)
