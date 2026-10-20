package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: String,
    val sender: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isOtp: Boolean = false,
    val otpCode: String? = null,
    val isFromMe: Boolean = false,
    val isVaultMessage: Boolean = false,
    val isSpam: Boolean = false
)
