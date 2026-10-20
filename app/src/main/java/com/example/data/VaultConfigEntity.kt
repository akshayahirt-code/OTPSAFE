package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_config")
data class VaultConfigEntity(
    @PrimaryKey val id: Int = 1,
    val isSetup: Boolean = false,
    val pinHash: String = "",
    val biometricEnabled: Boolean = true
)
