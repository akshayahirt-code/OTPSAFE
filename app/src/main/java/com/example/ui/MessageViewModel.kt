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

    // Real Device SMS Permission and Sync states
    private val _isSmsPermissionGranted = MutableStateFlow(false)
    val isSmsPermissionGranted: StateFlow<Boolean> = _isSmsPermissionGranted.asStateFlow()

    private val _isSyncingSms = MutableStateFlow(false)
    val isSyncingSms: StateFlow<Boolean> = _isSyncingSms.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow<String?>(null)
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()

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

            // Check if SMS permission is already granted on app start
            val hasPerm = SmsManagerHelper.hasReadSmsPermission(application)
            _isSmsPermissionGranted.value = hasPerm
            if (hasPerm) {
                _isSmsAuthorized.value = true
                repository.syncDeviceSmsHistory(application)
            }
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

    fun refreshPermissionState(context: android.content.Context) {
        val granted = SmsManagerHelper.hasReadSmsPermission(context)
        _isSmsPermissionGranted.value = granted
        if (granted && _isSmsAuthorized.value != true) {
            _isSmsAuthorized.value = true
            syncDeviceSmsHistory(context)
        }
    }

    fun onSmsPermissionsResult(granted: Boolean, context: android.content.Context, onComplete: ((Int) -> Unit)? = null) {
        _isSmsPermissionGranted.value = granted
        _isSmsAuthorized.value = granted
        if (granted) {
            syncDeviceSmsHistory(context, onComplete)
        } else {
            onComplete?.invoke(0)
        }
    }

    fun syncDeviceSmsHistory(context: android.content.Context, onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            _isSyncingSms.value = true
            try {
                val count = repository.syncDeviceSmsHistory(context)
                _isSyncingSms.value = false
                _syncStatusMessage.value = if (count > 0) {
                    "Synced $count messages from SMS history"
                } else {
                    "Device SMS history is up to date"
                }
                onComplete?.invoke(count)
            } catch (e: Exception) {
                _isSyncingSms.value = false
                _syncStatusMessage.value = "Failed to sync SMS: ${e.message}"
                onComplete?.invoke(0)
            }
        }
    }

    fun clearSyncStatusMessage() {
        _syncStatusMessage.value = null
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

    // Transmission Engine - Dispatches real SMS when SEND_SMS permission is granted and records in local secure store
    fun sendDirectMessage(body: String, context: android.content.Context? = null) {
        val threadId = _activeThreadId.value ?: return
        val sender = _activeThreadSender.value
        if (body.isBlank()) return

        val appContext = context ?: getApplication<Application>().applicationContext

        viewModelScope.launch {
            val isPhoneNumber = sender.any { it.isDigit() }
            if (isPhoneNumber && SmsManagerHelper.hasSendSmsPermission(appContext)) {
                SmsManagerHelper.sendSmsMessage(appContext, sender, body)
            }

            repository.sendMessage(threadId, sender, body)
        }
    }

    /**
     * Composes and sends a new SMS message to a recipient, creating or updating the conversation thread.
     */
    fun startNewConversationAndSend(recipient: String, body: String, context: android.content.Context? = null) {
        if (recipient.isBlank() || body.isBlank()) return
        val appContext = context ?: getApplication<Application>().applicationContext
        val threadId = recipient.trim().replace(" ", "_").lowercase()

        viewModelScope.launch {
            val isPhoneNumber = recipient.any { it.isDigit() }
            if (isPhoneNumber && SmsManagerHelper.hasSendSmsPermission(appContext)) {
                SmsManagerHelper.sendSmsMessage(appContext, recipient, body)
            }

            repository.sendMessage(threadId, recipient, body)
            selectThread(threadId, recipient)
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
