package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.security.MessageDigest

class MessageRepository(private val messageDao: MessageDao) {

    val allMessages: Flow<List<MessageEntity>> = messageDao.getAllMessagesFlow()
    val normalMessages: Flow<List<MessageEntity>> = messageDao.getNormalMessagesFlow()
    val vaultMessages: Flow<List<MessageEntity>> = messageDao.getVaultMessagesFlow()
    val vaultConfig: Flow<VaultConfigEntity?> = messageDao.getVaultConfigFlow()

    fun getMessagesInThread(threadId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesInThreadFlow(threadId)
    }

    suspend fun deleteMessage(id: Long) {
        messageDao.deleteMessageById(id)
    }

    suspend fun sendMessage(threadId: String, sender: String, body: String) {
        val message = MessageEntity(
            threadId = threadId,
            sender = sender,
            body = body,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            isOtp = false,
            isVaultMessage = false
        )
        messageDao.insertMessage(message)
    }

    // Process incoming message with Auto-Categorization/Auto-routing engine
    suspend fun receiveSimulatedMessage(sender: String, body: String, customTime: Long = System.currentTimeMillis()): Long {
        val lowerBody = body.lowercase()
        
        // 1. Detect OTP
        val otpKeywords = listOf("otp", "code", "verification", "one-time", "verifikasi", "password", "pin")
        val hasOtpKeyword = otpKeywords.any { lowerBody.contains(it) }
        val digitsRegex = Regex("\\b\\d{4,8}\\b")
        val digitsMatch = digitsRegex.find(body)
        val isOtpDetected = hasOtpKeyword && digitsMatch != null
        val extractedOtp = if (isOtpDetected) digitsMatch?.value else null

        // 2. Detect Bank Transaction/Sensitive Accounts
        val bankKeywords = listOf("debited", "credited", "withdrawn", "spent", "bank", "transferred", "txid", "transaction", "bal:", "balance", "rs.", "inr")
        val isBankDetected = bankKeywords.any { lowerBody.contains(it) }

        // Rule: Route OTPs and Bank statements directly to Vault
        val isVaultMessage = isOtpDetected || isBankDetected

        // Deterministic Thread ID based on sender
        val threadId = sender.trim().replace(" ", "_").lowercase()

        val message = MessageEntity(
            threadId = threadId,
            sender = sender,
            body = body,
            timestamp = customTime,
            isFromMe = false,
            isOtp = isOtpDetected,
            otpCode = extractedOtp,
            isVaultMessage = isVaultMessage
        )
        return messageDao.insertMessage(message)
    }

    suspend fun markAsSpam(threadId: String) {
        messageDao.markThreadAsSpam(threadId)
    }

    suspend fun deleteThread(threadId: String) {
        messageDao.deleteThread(threadId)
    }

    suspend fun clearThread(threadId: String) {
        messageDao.clearThread(threadId)
    }

    // Auto-Deletion Rule: Delete OTP messages older than 72 hours (3 days)
    suspend fun purgeOldOtps(): Int {
        val threeDaysAgo = System.currentTimeMillis() - (72 * 60 * 60 * 1000L)
        return messageDao.deleteOtpsOlderThan(threeDaysAgo)
    }

    // Vault PIN setup & verification
    suspend fun setupVaultPin(pin: String): Boolean {
        if (pin.length != 6 || !pin.all { it.isDigit() }) return false
        val hashed = hashString(pin)
        val config = VaultConfigEntity(id = 1, isSetup = true, pinHash = hashed)
        messageDao.saveVaultConfig(config)
        return true
    }

    suspend fun verifyVaultPin(pin: String): Boolean {
        val config = messageDao.getVaultConfig() ?: return false
        if (!config.isSetup) return false
        return config.pinHash == hashString(pin)
    }

    suspend fun isVaultSetup(): Boolean {
        val config = messageDao.getVaultConfig()
        return config?.isSetup == true
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun prepopulateMockDataIfEmpty() {
        // Check if database has any messages
        val currentNormal = normalMessages.firstOrNull() ?: emptyList()
        val currentVault = vaultMessages.firstOrNull() ?: emptyList()
        
        if (currentNormal.isEmpty() && currentVault.isEmpty()) {
            val now = System.currentTimeMillis()
            
            // 1. Setup default vault config
            val defaultConfig = VaultConfigEntity(id = 1, isSetup = false, pinHash = "", biometricEnabled = true)
            messageDao.saveVaultConfig(defaultConfig)

            // 2. Insert Premium Mock Messages (Normal Inbox & Secure Vault)
            
            // --- Normal Dialogues ---
            // Thread: Kabir
            receiveSimulatedMessage("Kabir Sharma", "Hey! Are you coming to the tech seminar today?", now - 4 * 3600 * 1000)
            sendMessage("kabir_sharma", "Kabir Sharma", "Yes! Just finishing up some work. Save me a seat!")
            receiveSimulatedMessage("Kabir Sharma", "Sure! I will reserve one near the front row. They are serving good coffee too.", now - 2 * 3600 * 1000)

            // Thread: Sarah
            receiveSimulatedMessage("Sarah Jenkins", "Did you check out the new design specifications?", now - 6 * 3600 * 1000)
            
            // Thread: FoodPanda
            receiveSimulatedMessage("FooDeli", "Your lunch order is on its way with our premium rider. Track here: dynamic.deliv/x98", now - 1 * 3600 * 1000)

            // --- Secure Vault Messages (Auto-categorized by our scanner rules) ---
            // Google OTP
            receiveSimulatedMessage("GOOGLE", "Your Google Account recovery code is 491028. It expires in 10 minutes. Do not share this PIN.", now - 5 * 60 * 1000)
            
            // Swiggy OTP (recently arrived)
            receiveSimulatedMessage("SWIGGY", "Your verification login OTP code is 902834. Strictly confidential. Valid for 3 mins.", now - 2 * 60 * 1000)

            // HDFC Bank Statement
            receiveSimulatedMessage("HDFC-BANK", "HDFC Bank Alert: Rs. 4,500.00 debited from A/C XX8902 on 2026-05-27. Info: Pos Transaction at STARBUCKS. Avail Bal: Rs 46,230.12.", now - 12 * 3600 * 1000)
            
            // Amazon Pay OTP
            receiveSimulatedMessage("AMAZON", "Your Amazon Pay login OTP is 381023. Valid for 5 minutes. Do not share your OTP with anyone.", now - 10 * 3600 * 1000)
            
            // Expired/Temporal OTP to test Purging (e.g. 5 days ago)
            receiveSimulatedMessage("NETFLIX", "Your Netflix Login Code is 782012. Valid for 10 minutes.", now - 5 * 24 * 3600 * 1000)
        }
    }
}
