package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val problemTitle: String,
    val problemDescription: String,
    val language: Int? = null,
    val status: String = "NOT_STARTED",
    val lastUpdated: Long = System.currentTimeMillis()
)
