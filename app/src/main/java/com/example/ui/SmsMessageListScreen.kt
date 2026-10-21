package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.SmsManagerHelper
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.MessageEntity
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class SmsFilterCategory(val label: String, val icon: ImageVector) {
    ALL("All Messages", Icons.Default.Inbox),
    OTP("OTPs & Codes", Icons.Default.Security),
    FINANCE("Transactions", Icons.Default.AccountBalance),
    PERSONAL("Personal", Icons.Default.ChatBubble),
    VAULT("Vault", Icons.Default.Lock)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsMessageListScreen(
    viewModel: MessageViewModel = viewModel(),
    onNavigateToThread: (threadId: String, sender: String) -> Unit = { _, _ -> },
    onOpenInbox: (() -> Unit)? = null,
    onEnterVault: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) ObsidianBgDark else ObsidianBgLight
    val surfaceColor = if (isDark) ObsidianSurfaceDark else ObsidianSurfaceLight
    val borderColor = if (isDark) ObsidianBorderDark else ObsidianBorderLight
    val primaryText = if (isDark) LightTextDark else LightTextLight
    val secondaryText = MutedTextDark

    val allMessages by viewModel.allMessages.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var selectedCategory by remember { mutableStateOf(SmsFilterCategory.ALL) }
    val expandedMessageIds = remember { mutableStateListOf<Long>() }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showGitHubReleaseDialog by remember { mutableStateOf(false) }
    var showNewMessageDialog by remember { mutableStateOf(false) }

    val isSmsPermissionGranted by viewModel.isSmsPermissionGranted.collectAsState()
    val isSyncingSms by viewModel.isSyncingSms.collectAsState()

    val smsPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_SMS] == true
        viewModel.onSmsPermissionsResult(readGranted, context) { count ->
            coroutineScope.launch {
                if (readGranted) {
                    val msg = if (count > 0) "Synced $count messages from SMS history" else "SMS permission granted! Ready to sync."
                    snackbarHostState.showSnackbar(msg)
                } else {
                    snackbarHostState.showSnackbar("SMS permission denied. Cannot access message history.")
                }
            }
        }
    }

    val requestOrSyncSmsHistory: () -> Unit = {
        if (SmsManagerHelper.hasReadSmsPermission(context)) {
            viewModel.syncDeviceSmsHistory(context) { count ->
                coroutineScope.launch {
                    val msg = if (count > 0) "Synced $count messages from SMS history" else "Device SMS history is up to date"
                    snackbarHostState.showSnackbar(msg)
                }
            }
        } else {
            smsPermissionsLauncher.launch(SmsManagerHelper.REQUIRED_SMS_PERMISSIONS)
        }
    }

    // Filter messages according to selected category
    val filteredMessages = remember(allMessages, selectedCategory) {
        when (selectedCategory) {
            SmsFilterCategory.ALL -> allMessages
            SmsFilterCategory.OTP -> allMessages.filter { it.isOtp || it.otpCode != null }
            SmsFilterCategory.FINANCE -> allMessages.filter { message ->
                val body = message.body.lowercase()
                body.contains("rs.") || body.contains("inr") || body.contains("bank") ||
                        body.contains("debited") || body.contains("credited") || body.contains("bal:") ||
                        body.contains("card") || body.contains("spent")
            }
            SmsFilterCategory.PERSONAL -> allMessages.filter { message ->
                val body = message.body.lowercase()
                !message.isOtp && message.otpCode == null &&
                        !body.contains("debited") && !body.contains("credited") &&
                        !body.contains("bank") && !message.isVaultMessage
            }
            SmsFilterCategory.VAULT -> allMessages.filter { it.isVaultMessage }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("sms_message_list_screen"),
        containerColor = bgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .background(surfaceColor)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("sms_list_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = primaryText
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SMS Messages",
                                style = MaterialTheme.typography.titleLarge,
                                color = primaryText,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SafePrimary.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, SafePrimary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "${filteredMessages.size}",
                                    color = SafePrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Card-based message explorer",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryText
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Access Device SMS History / Sync Button
                        IconButton(
                            onClick = requestOrSyncSmsHistory,
                            enabled = !isSyncingSms,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SafePrimary.copy(alpha = 0.12f))
                                .border(1.dp, SafePrimary.copy(alpha = 0.3f), CircleShape)
                                .testTag("sms_list_sync_history_button")
                        ) {
                            if (isSyncingSms) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = SafePrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Access Device SMS History",
                                    tint = SafePrimary
                                )
                            }
                        }

                        // GitHub Releases Download Link Button
                        IconButton(
                            onClick = { showGitHubReleaseDialog = true },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SafePrimary.copy(alpha = 0.12f))
                                .border(1.dp, SafePrimary.copy(alpha = 0.3f), CircleShape)
                                .testTag("sms_list_github_releases_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "GitHub Releases",
                                tint = SafePrimary
                            )
                        }

                        // Compose New Message Button
                        IconButton(
                            onClick = { showNewMessageDialog = true },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SafePrimary.copy(alpha = 0.12f))
                                .border(1.dp, SafePrimary.copy(alpha = 0.3f), CircleShape)
                                .testTag("sms_list_new_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "New Message",
                                tint = SafePrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .testTag("sms_list_search_input"),
                    placeholder = { Text("Search by sender, keyword, or OTP...", color = secondaryText) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = secondaryText
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = secondaryText
                                )
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = bgColor,
                        unfocusedContainerColor = bgColor,
                        disabledContainerColor = bgColor,
                        focusedTextColor = primaryText,
                        unfocusedTextColor = primaryText,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Horizontal Filter Chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sms_filter_chips_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SmsFilterCategory.values()) { category ->
                        val isSelected = selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = { Text(category.label, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SafePrimary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White,
                                containerColor = surfaceColor,
                                labelColor = secondaryText,
                                iconColor = secondaryText
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) SafePrimary else borderColor
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("filter_chip_${category.name.lowercase()}")
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (onOpenInbox != null) {
                NavigationBar(
                    containerColor = surfaceColor,
                    tonalElevation = 4.dp
                ) {
                    NavigationBarItem(
                        selected = true,
                        onClick = { /* already on sms cards */ },
                        icon = { Icon(Icons.Default.ViewAgenda, contentDescription = "SMS Cards") },
                        label = { Text("SMS Cards") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SafePrimary,
                            selectedTextColor = SafePrimary,
                            indicatorColor = SafePrimary.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_sms_cards")
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onOpenInbox,
                        icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Conversations") },
                        label = { Text("Conversations") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SafePrimary,
                            indicatorColor = SafePrimary.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_inbox")
                    )
                    if (onEnterVault != null) {
                        NavigationBarItem(
                            selected = false,
                            onClick = onEnterVault,
                            icon = { Icon(Icons.Default.Lock, contentDescription = "Vault") },
                            label = { Text("Vault") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SecureEmerald,
                                indicatorColor = SecureEmerald.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_vault")
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    showNewMessageDialog = true
                },
                modifier = Modifier
                    .then(if (onOpenInbox == null) Modifier.navigationBarsPadding() else Modifier)
                    .testTag("compose_sms_fab"),
                containerColor = SafePrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "New Message"
                    )
                },
                text = {
                    Text("New Message", fontWeight = FontWeight.Bold)
                }
            )
        }
    ) { innerPadding ->
        if (filteredMessages.isEmpty()) {
            EmptySmsMessagesState(
                searchQuery = searchQuery,
                category = selectedCategory,
                isPermissionGranted = isSmsPermissionGranted,
                onRequestPermission = requestOrSyncSmsHistory,
                onClearFilter = {
                    viewModel.setSearchQuery("")
                    selectedCategory = SmsFilterCategory.ALL
                },
                onNewMessage = {
                    showNewMessageDialog = true
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("sms_lazy_column"),
                state = rememberLazyListState(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isSmsPermissionGranted) {
                    item(key = "permission_banner") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("access_sms_history_banner"),
                            shape = RoundedCornerShape(16.dp),
                            color = SafePrimary.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, SafePrimary.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = SafePrimary.copy(alpha = 0.2f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Sms,
                                                contentDescription = null,
                                                tint = SafePrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Access Device SMS History",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = primaryText
                                        )
                                        Text(
                                            text = "Grant SMS permissions to import & categorize your device text messages.",
                                            fontSize = 12.sp,
                                            color = secondaryText
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = requestOrSyncSmsHistory,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("banner_grant_sms_permission_btn"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SafePrimary)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Grant Permission & Sync SMS", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                items(
                    items = filteredMessages,
                    key = { it.id }
                ) { message ->
                    val isExpanded = expandedMessageIds.contains(message.id)

                    SmsMessageCard(
                        message = message,
                        isExpanded = isExpanded,
                        onCardClick = {
                            if (isExpanded) {
                                expandedMessageIds.remove(message.id)
                            } else {
                                expandedMessageIds.add(message.id)
                            }
                        },
                        onCopyCode = { code ->
                            clipboardManager.setText(AnnotatedString(code))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("OTP code copied: $code")
                            }
                        },
                        onCopyBody = {
                            clipboardManager.setText(AnnotatedString(message.body))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Message copied to clipboard")
                            }
                        },
                        onOpenThread = {
                            onNavigateToThread(message.threadId, message.sender)
                        },
                        onDelete = {
                            viewModel.deleteMessage(message.id)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Message deleted")
                            }
                        },
                        onShare = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "SMS from ${message.sender}")
                                putExtra(Intent.EXTRA_TEXT, message.body)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share SMS via"))
                        }
                    )
                }
            }
        }
    }

    if (showGitHubReleaseDialog) {
        GitHubReleaseDownloadDialog(
            onDismissRequest = { showGitHubReleaseDialog = false }
        )
    }

    if (showNewMessageDialog) {
        NewMessageDialog(
            onDismissRequest = { showNewMessageDialog = false },
            onSendMessage = { recipient, body ->
                showNewMessageDialog = false
                viewModel.startNewConversationAndSend(recipient, body, context)
                onNavigateToThread(
                    recipient.trim().replace(" ", "_").lowercase(),
                    recipient
                )
            }
        )
    }
}

@Composable
fun SmsMessageCard(
    message: MessageEntity,
    isExpanded: Boolean,
    onCardClick: () -> Unit,
    onCopyCode: (String) -> Unit,
    onCopyBody: () -> Unit,
    onOpenThread: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val surfaceColor = if (isDark) ObsidianSurfaceDark else ObsidianSurfaceLight
    val borderColor = if (isDark) ObsidianBorderDark else ObsidianBorderLight
    val primaryText = if (isDark) LightTextDark else LightTextLight
    val secondaryText = MutedTextDark

    val timeFormatter = remember { SimpleDateFormat("MMM d • hh:mm a", Locale.getDefault()) }
    val formattedTime = timeFormatter.format(Date(message.timestamp))

    // Determine category style & icon
    val isBank = remember(message.body) {
        val lower = message.body.lowercase()
        lower.contains("rs.") || lower.contains("inr") || lower.contains("bank") ||
                lower.contains("debited") || lower.contains("credited") || lower.contains("card")
    }

    val (avatarColor, badgeColor, badgeLabel, icon) = when {
        message.isOtp -> Quad(SecureEmerald, SecureEmerald, "OTP CODE", Icons.Default.Security)
        isBank -> Quad(WarningAmber, WarningAmber, "BANKING", Icons.Default.AccountBalance)
        message.isFromMe -> Quad(SafePrimary, SafePrimary, "SENT", Icons.Default.ArrowOutward)
        message.isVaultMessage -> Quad(Color(0xFF8B5CF6), Color(0xFF8B5CF6), "VAULT", Icons.Default.Lock)
        else -> Quad(Color(0xFF0284C7), Color(0xFF0284C7), "SMS", Icons.Default.ChatBubbleOutline)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCardClick() }
            .testTag("sms_card_${message.id}"),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(1.dp, if (message.isOtp) SecureEmerald.copy(alpha = 0.4f) else borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Avatar, Sender, Timestamp, Category Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar Circle
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(avatarColor.copy(alpha = 0.15f))
                        .border(1.dp, avatarColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (message.isOtp || isBank) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = avatarColor,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        val initial = message.sender.firstOrNull()?.toString()?.uppercase() ?: "S"
                        Text(
                            text = initial,
                            color = avatarColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Sender Name and Timestamp
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Type Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = badgeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeColor,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Highlighted OTP Box (if applicable)
            if (message.isOtp && !message.otpCode.isNullOrEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SecureEmerald.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, SecureEmerald.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "VERIFICATION CODE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SecureEmerald,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = message.otpCode,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = primaryText,
                                letterSpacing = 3.sp
                            )
                        }

                        Button(
                            onClick = { onCopyCode(message.otpCode) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SecureEmerald,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("copy_otp_button_${message.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Code",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Message Body Text
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = primaryText.copy(alpha = 0.9f),
                lineHeight = 20.sp,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )

            // Expand / Collapse affordance if text is lengthy
            if (message.body.length > 100) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isExpanded) "Show less" else "Read full message",
                    color = SafePrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onCardClick() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(
                color = borderColor,
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Actions: Copy text & Share
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCopyBody,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("copy_text_button_${message.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy message text",
                            tint = secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("share_button_${message.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share message",
                            tint = secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_sms_button_${message.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete SMS",
                            tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Right Action: Open Thread
                OutlinedButton(
                    onClick = onOpenThread,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, borderColor),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("open_thread_button_${message.id}")
                ) {
                    Text(
                        text = "Open Thread",
                        fontSize = 12.sp,
                        color = SafePrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = SafePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptySmsMessagesState(
    searchQuery: String,
    category: SmsFilterCategory,
    isPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onClearFilter: () -> Unit,
    onNewMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) LightTextDark else LightTextLight
    val secondaryText = MutedTextDark

    Column(
        modifier = modifier
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = SafePrimary.copy(alpha = 0.1f),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (!isPermissionGranted) Icons.Default.Lock else Icons.Default.MarkChatUnread,
                    contentDescription = null,
                    tint = SafePrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (!isPermissionGranted) {
                "SMS History Access Required"
            } else if (searchQuery.isNotEmpty()) {
                "No matching messages"
            } else {
                "No ${category.label} found"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = primaryText
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (!isPermissionGranted) {
                "Grant SMS permissions to access device message history and automatically detect verification OTPs."
            } else if (searchQuery.isNotEmpty()) {
                "Try searching for another keyword or check another filter category."
            } else {
                "Incoming SMS messages in this category will be displayed in cards here."
            },
            style = MaterialTheme.typography.bodySmall,
            color = secondaryText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!isPermissionGranted) {
                Button(
                    onClick = onRequestPermission,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafePrimary),
                    modifier = Modifier.testTag("empty_state_grant_permission_btn")
                ) {
                    Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Grant Permission & Load History")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (searchQuery.isNotEmpty() || category != SmsFilterCategory.ALL) {
                    OutlinedButton(
                        onClick = onClearFilter,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clear Filter")
                    }
                }

                Button(
                    onClick = onNewMessage,
                    shape = RoundedCornerShape(12.dp),
                    colors = if (!isPermissionGranted) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors(containerColor = SafePrimary),
                    modifier = Modifier.testTag("empty_state_compose_btn")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Message")
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
