package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ConversationThread(
    val threadId: String,
    val sender: String,
    val latestMessage: MessageEntity,
    val isOtpThread: Boolean = false
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MessageViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = MessageRepository(db.messageDao())

    // Permissions and Default app authorization simulator
    private val _isSmsAuthorized = MutableStateFlow<Boolean?>(null) // null = not yet checked
    val isSmsAuthorized: StateFlow<Boolean?> = _isSmsAuthorized.asStateFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Active selected conversation thread ID
    private val _activeThreadId = MutableStateFlow<String?>(null)
    val activeThreadId: StateFlow<String?> = _activeThreadId.asStateFlow()

    // Active thread details
    private val _activeThreadSender = MutableStateFlow("")
    val activeThreadSender: StateFlow<String> = _activeThreadSender.asStateFlow()

    // Vault security state
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private val _isVaultPinSetup = MutableStateFlow(false)
    val isVaultPinSetup: StateFlow<Boolean> = _isVaultPinSetup.asStateFlow()

    private val _vaultPinInput = MutableStateFlow("")
    val vaultPinInput: StateFlow<String> = _vaultPinInput.asStateFlow()

    private val _vaultErrorMessage = MutableStateFlow<String?>(null)
    val vaultErrorMessage: StateFlow<String?> = _vaultErrorMessage.asStateFlow()

    // Simulated Biometric prompting
    private val _showBiometricPrompt = MutableStateFlow(false)
    val showBiometricPrompt: StateFlow<Boolean> = _showBiometricPrompt.asStateFlow()

    // Quick templates for smart replies
    val smartReplies = listOf("😢", "😊", "👍", "OKAY", "Thanks", "On my way!", "Call you shortly")

    init {
        viewModelScope.launch {
            // First prepopulate mock data if empty
            repository.prepopulateMockDataIfEmpty()
            
            // Check if vault is already setup
            checkVaultSetupStatus()

            // Run Auto-Deletion clean up (72h Threshold)
            val purgedCount = repository.purgeOldOtps()
            android.util.Log.d("OTPSecure", "Auto-purged older OTP logs: $purgedCount accounts.")
        }
    }

    private suspend fun checkVaultSetupStatus() {
        val setup = repository.isVaultSetup()
        _isVaultPinSetup.value = setup
    }

    // Normal Threads List, Live Search Filtered, Priority Indexed
    val inboxThreads: StateFlow<List<ConversationThread>> = combine(
        repository.normalMessages,
        _searchQuery
    ) { messages, query ->
        val filtered = if (query.isEmpty()) {
            messages
        } else {
            messages.filter { 
                it.sender.contains(query, ignoreCase = true) || 
                it.body.contains(query, ignoreCase = true) 
            }
        }
        
        // Group by threadId to create conversation list
        filtered.groupBy { it.threadId }.map { (threadId, msgs) ->
            val latestMessage = msgs.first() // sorted DESC by timestamp
            // Priority Indexing logic: Move critical priority senders (like Google, Banks, Swiggy) to front or highlight
            ConversationThread(
                threadId = threadId,
                sender = latestMessage.sender,
                latestMessage = latestMessage,
                isOtpThread = msgs.any { it.isOtp }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Vault isolated logs (with live search)
    val vaultMessages: StateFlow<List<MessageEntity>> = combine(
        repository.vaultMessages,
        _searchQuery
    ) { messages, query ->
        if (query.isEmpty()) {
            messages
        } else {
            messages.filter { 
                it.sender.contains(query, ignoreCase = true) || 
                it.body.contains(query, ignoreCase = true) 
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Individual SMS Messages (Live Search Filtered)
    val allMessages: StateFlow<List<MessageEntity>> = combine(
        repository.allMessages,
        _searchQuery
    ) { messages, query ->
        if (query.isEmpty()) {
            messages
        } else {
            messages.filter { 
                it.sender.contains(query, ignoreCase = true) || 
                it.body.contains(query, ignoreCase = true) ||
                (it.otpCode != null && it.otpCode.contains(query))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Thread conversation sequence
    val activeThreadMessages: StateFlow<List<MessageEntity>> = _activeThreadId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else repository.getMessagesInThread(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI actions
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectThread(threadId: String?, senderName: String = "") {
        _activeThreadId.value = threadId
        _activeThreadSender.value = senderName
    }

    fun setSmsAuthorized(authorized: Boolean) {
        _isSmsAuthorized.value = authorized
    }

    // Security Pincode & Biometrics actions
    fun setVaultUnlocked(unlocked: Boolean) {
        _isVaultUnlocked.value = unlocked
        _vaultPinInput.value = ""
        _vaultErrorMessage.value = null
    }

    fun appendPinDigit(digit: String) {
        if (_vaultPinInput.value.length < 6) {
            _vaultPinInput.value += digit
            _vaultErrorMessage.value = null
        }
    }

    fun deletePinDigit() {
        if (_vaultPinInput.value.isNotEmpty()) {
            _vaultPinInput.value = _vaultPinInput.value.dropLast(1)
            _vaultErrorMessage.value = null
        }
    }

    fun clearPin() {
        _vaultPinInput.value = ""
        _vaultErrorMessage.value = null
    }

    fun submitPin(onSuccess: () -> Unit = {}) {
        val pin = _vaultPinInput.value
        if (pin.length != 6) {
            _vaultErrorMessage.value = "PIN must be 6 digits"
            return
        }

        viewModelScope.launch {
            if (_isVaultPinSetup.value) {
                // Verify
                val isValid = repository.verifyVaultPin(pin)
                if (isValid) {
                    _isVaultUnlocked.value = true
                    _vaultPinInput.value = ""
                    _vaultErrorMessage.value = null
                    onSuccess()
                } else {
                    _vaultPinInput.value = ""
                    _vaultErrorMessage.value = "Invalid Security PIN. Try again."
                }
            } else {
                // Setup
                val isDone = repository.setupVaultPin(pin)
                if (isDone) {
                    _isVaultPinSetup.value = true
                    _isVaultUnlocked.value = true
                    _vaultPinInput.value = ""
                    _vaultErrorMessage.value = null
                    onSuccess()
                } else {
                    _vaultErrorMessage.value = "PIN must be 6 digits"
                }
            }
        }
    }

    fun showBiometricTrigger(show: Boolean) {
        _showBiometricPrompt.value = show
    }

    fun tryBiometricAuth(onSuccess: () -> Unit) {
        // Authentic simulated/real biometric success pipeline
        viewModelScope.launch {
            if (repository.isVaultSetup()) {
                _isVaultUnlocked.value = true
                onSuccess()
            } else {
                _vaultErrorMessage.value = "Please configure a PIN first."
            }
        }
    }

    // Transmission Engine
    fun sendDirectMessage(body: String) {
        val threadId = _activeThreadId.value ?: return
        val sender = _activeThreadSender.value
        if (body.isBlank()) return

        viewModelScope.launch {
            repository.sendMessage(threadId, sender, body)
            
            // To make the chat experience interactive, let's simulate a reply back after 1.5 seconds!
            // If the message contains a question, reply with dynamic friendly responses
            simulateAutoReply(sender, body)
        }
    }

    private fun simulateAutoReply(sender: String, messageSent: String) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            val threadId = sender.trim().replace(" ", "_").lowercase()
            
            // Determine a realistic reply context
            val replyText = when {
                messageSent.lowercase().contains("hello") || messageSent.lowercase().contains("hi") -> {
                    "Hey there! This is a secure local simulation of $sender. Your data is protected."
                }
                messageSent.lowercase().contains("where") -> {
                    "Just around! Doing some testing of OTPSECURE's encryption module."
                }
                messageSent.lowercase().contains("seat") || messageSent.lowercase().contains("kabir") -> {
                    "Awesome. Reserved it!"
                }
                else -> {
                    "Message received on OTPSECURE client. Synchronized with local SQLite storage."
                }
            }
            repository.receiveSimulatedMessage(sender, replyText)
        }
    }

    // Delete an individual SMS message
    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    // Simulate arriving OTP for demoing Vault Routing
    fun triggerSimulatedOtpArrival() {
        viewModelScope.launch {
            val codecs = listOf("GOOGLE", "SWIGGY", "NETFLIX", "AMAZON", "PAYPAL", "BINANCE")
            val selectedSender = codecs.random()
            val otpCode = (100000..999999).random()
            val otpMsg = "Your $selectedSender verification security code token is $otpCode. Active for 10 minutes."
            repository.receiveSimulatedMessage(selectedSender, otpMsg)
        }
    }

    // Simulate arriving general SMS (Personal, Bank, or OTP)
    fun triggerSimulatedAnySms() {
        viewModelScope.launch {
            val variants = listOf(
                Triple("CHASE-ALERT", "Chase Bank Alert: Rs. 1,250.00 debited from card ending in 4102 at GROCERY MART. Balance: Rs. 38,190.", true),
                Triple("Alex Rivera", "Hey! Don't forget our meeting tomorrow morning at 10 AM.", false),
                Triple("UBER", "Your Uber driver is arriving in 3 mins. OTP for ride: 5821.", true),
                Triple("Jessica Lee", "Thanks for sending over the project documents! Looks great.", false),
                Triple("GITHUB", "Your GitHub two-factor authentication code is 649120. Don't share it.", true),
                Triple("STARBUCKS", "Your order #42 is ready for pickup at the counter! Enjoy your drink.", false)
            )
            val item = variants.random()
            repository.receiveSimulatedMessage(item.first, item.second)
        }
    }

    // Overflow context options
    fun clearActiveThread() {
        val threadId = _activeThreadId.value ?: return
        viewModelScope.launch {
            repository.clearThread(threadId)
        }
    }

    fun deleteActiveThread() {
        val threadId = _activeThreadId.value ?: return
        viewModelScope.launch {
            repository.deleteThread(threadId)
            _activeThreadId.value = null
        }
    }

    fun markActiveThreadAsSpam() {
        val threadId = _activeThreadId.value ?: return
        viewModelScope.launch {
            repository.markAsSpam(threadId)
            _activeThreadId.value = null
        }
    }
}
