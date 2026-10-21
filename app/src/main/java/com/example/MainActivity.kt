package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.SmsManagerHelper
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.MessageEntity
import com.example.ui.ConversationThread
import com.example.ui.MessageViewModel
import com.example.ui.SmsMessageListScreen
import com.example.ui.GitHubReleaseDownloadDialog
import com.example.ui.NewMessageDialog
import com.example.ui.theme.OTPSecureTheme
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OTPSecureTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OtpSecureApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OtpSecureApp(viewModel: MessageViewModel = viewModel()) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isSmsAuthorized by viewModel.isSmsAuthorized.collectAsState()
    val activeThreadId by viewModel.activeThreadId.collectAsState()
    val activeThreadSender by viewModel.activeThreadSender.collectAsState()
    
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsState()
    val isVaultPinSetup by viewModel.isVaultPinSetup.collectAsState()
    val showBiometricPrompt by viewModel.showBiometricPrompt.collectAsState()

    var showSystemDialogSim by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("sms_cards") } // sms_cards, inbox, vault, chat
    var showAttachmentSheet by remember { mutableStateOf(false) }

    // Synchronize currentScreen based on UI actions in ViewModel
    LaunchedEffect(activeThreadId) {
        if (activeThreadId != null) {
            currentScreen = "chat"
        } else if (currentScreen == "chat") {
            currentScreen = "sms_cards"
        }
    }

    LaunchedEffect(isVaultUnlocked) {
        if (isVaultUnlocked && currentScreen == "vault_lock") {
            currentScreen = "vault"
        }
    }

    // Real SMS Permission Request Flow via ActivityResultContracts
    val smsPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_SMS] == true
        viewModel.onSmsPermissionsResult(readGranted, context) { count ->
            if (readGranted) {
                Toast.makeText(
                    context,
                    if (count > 0) "SMS permissions granted! Synced $count messages from history." else "SMS permissions granted! Message history ready.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "SMS read permission denied. Real message history requires permission.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Step 1: App Launch -> SMS Authorization Check
    if (isSmsAuthorized == null) {
        SmsAuthGateScreen(
            onChooseDefault = {
                smsPermissionsLauncher.launch(SmsManagerHelper.REQUIRED_SMS_PERMISSIONS)
            },
            onChooseSandbox = {
                viewModel.setSmsAuthorized(true)
                Toast.makeText(context, "Continuing in Secure Sandbox Mode", Toast.LENGTH_SHORT).show()
            }
        )
    } else if (!isSmsAuthorized!!) {
        // Handle Denied Path -> Displays customized state where user can retry or adjust settings
        SmsDeniedScreen(
            onRetry = {
                smsPermissionsLauncher.launch(SmsManagerHelper.REQUIRED_SMS_PERMISSIONS)
            },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            onContinueSandbox = {
                viewModel.setSmsAuthorized(true)
                Toast.makeText(context, "Continuing in Secure Sandbox Mode", Toast.LENGTH_SHORT).show()
            }
        )
    } else {
        // Main Stack View Switcher
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                "sms_cards" -> {
                    SmsMessageListScreen(
                        viewModel = viewModel,
                        onNavigateToThread = { threadId, sender ->
                            viewModel.selectThread(threadId, sender)
                            currentScreen = "chat"
                        },
                        onOpenInbox = { currentScreen = "inbox" },
                        onEnterVault = {
                            if (isVaultUnlocked) {
                                currentScreen = "vault"
                            } else {
                                currentScreen = "vault_lock"
                            }
                        }
                    )
                }
                "inbox" -> {
                    InboxScreen(
                        viewModel = viewModel,
                        onEnterVault = {
                            if (isVaultUnlocked) {
                                currentScreen = "vault"
                            } else {
                                currentScreen = "vault_lock"
                            }
                        },
                        onOpenSmsCards = {
                            currentScreen = "sms_cards"
                        },
                        onSelectThread = { threadId, sender ->
                            viewModel.selectThread(threadId, sender)
                        }
                    )
                }
                "vault_lock" -> {
                    VaultLockScreen(
                        viewModel = viewModel,
                        onBackPressed = { currentScreen = "sms_cards" },
                        onSuccess = {
                            currentScreen = "vault"
                        }
                    )
                }
                "vault" -> {
                    VaultScreen(
                        viewModel = viewModel,
                        onBackPressed = {
                            viewModel.setVaultUnlocked(false)
                            currentScreen = "sms_cards"
                        }
                    )
                }
                "chat" -> {
                    ChatScreen(
                        viewModel = viewModel,
                        senderName = activeThreadSender,
                        onBackPressed = {
                            viewModel.selectThread(null)
                            currentScreen = "sms_cards"
                        },
                        onToggleAttachment = { showAttachmentSheet = !showAttachmentSheet }
                    )
                }
            }

            // Real-time Attachment Bottom Panel Sheet with Slide and Soft Corners
            AnimatedVisibility(
                visible = showAttachmentSheet && currentScreen == "chat",
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                ),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                AttachmentBottomBar(
                    onClose = { showAttachmentSheet = false }
                )
            }
        }
    }
}

// ==========================================
// 1. DEFAULT SMS AUTHORIZATION SCREEN & DIALOG
// ==========================================

@Composable
fun SmsAuthGateScreen(
    onChooseDefault: () -> Unit,
    onChooseSandbox: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val ObsidianBg = if (isDark) ObsidianBgDark else ObsidianBgLight
    val ObsidianSurface = if (isDark) ObsidianSurfaceDark else ObsidianSurfaceLight
    val ObsidianBorder = if (isDark) ObsidianBorderDark else ObsidianBorderLight
    val LightText = if (isDark) LightTextDark else LightTextLight
    val MutedText = MutedTextDark

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(ObsidianBg, ObsidianSurface)
                )
            )
            .padding(24.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Security Guardian Icon
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(SecureEmerald.copy(alpha = 0.15f))
                .border(2.dp, SecureEmerald, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Shield Guard",
                tint = SecureEmerald,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "OTPSECURE",
            style = MaterialTheme.typography.headlineLarge,
            color = LightText,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Automated Security Filter & Private OTP Vault",
            style = MaterialTheme.typography.bodyLarge,
            color = MutedText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Isolation Logic",
                        tint = SecureEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Secured Envelope Isolation",
                        fontWeight = FontWeight.Bold,
                        color = LightText
                    )
                }
                Text(
                    text = "OTPSecure identifies financial statement logs, alerts and OTPs locally, routing them into an encrypted biometrics-locked container.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onChooseDefault,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("auth_default_btn"),
            colors = ButtonDefaults.buttonColors(containerColor = SafePrimary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(imageVector = Icons.Default.Sms, contentDescription = "SMS Permission")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Grant SMS Permissions & Access History", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onChooseSandbox,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("auth_sandbox_btn")
        ) {
            Text("Continue with Offline Sandbox", color = SecureEmerald, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SystemSmsDialogSimulator(
    onSetAsDefault: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .animateContentSize(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2129))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Default App",
                    tint = SafePrimary,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Change SMS App?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Set OTPSecure as your default SMS app? OTPSecure will replace Messages. This is required to intercept incoming authentication tokens on the internal receiver system.",
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = onSetAsDefault,
                        colors = ButtonDefaults.buttonColors(containerColor = SafePrimary)
                    ) {
                        Text("Set as Default", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SmsDeniedScreen(
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onContinueSandbox: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val ObsidianBg = if (isDark) ObsidianBgDark else ObsidianBgLight
    val ObsidianSurface = if (isDark) ObsidianSurfaceDark else ObsidianSurfaceLight
    val ObsidianBorder = if (isDark) ObsidianBorderDark else ObsidianBorderLight
    val LightText = if (isDark) LightTextDark else LightTextLight
    val MutedText = MutedTextDark

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(24.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Warning",
            tint = WarningAmber,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "SMS Permission Required",
            style = MaterialTheme.typography.titleLarge,
            color = LightText,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "To access device message history, auto-detect verification OTPs, and protect your accounts in the local secure vault, OTPSecure needs SMS read and receive permissions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("sms_denied_grant_permission_btn"),
            colors = ButtonDefaults.buttonColors(containerColor = SafePrimary),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(imageVector = Icons.Default.Sms, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Grant SMS Permissions", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("sms_denied_open_settings_btn"),
            border = BorderStroke(1.dp, ObsidianBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = LightText)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open App Settings", color = LightText, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onContinueSandbox,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("sms_denied_sandbox_btn")
        ) {
            Text("Continue in Offline Sandbox Mode", color = SecureEmerald, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// 2. CONVERSATIONAL MAIN INBOX SCREEN
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: MessageViewModel,
    onEnterVault: () -> Unit,
    onOpenSmsCards: () -> Unit,
    onSelectThread: (String, String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val ObsidianBg = if (isDark) ObsidianBgDark else ObsidianBgLight
    val ObsidianSurface = if (isDark) ObsidianSurfaceDark else ObsidianSurfaceLight
    val ObsidianBorder = if (isDark) ObsidianBorderDark else ObsidianBorderLight
    val LightText = if (isDark) LightTextDark else LightTextLight
    val MutedText = MutedTextDark

    val threads by viewModel.inboxThreads.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current

    var showGitHubReleaseDialog by remember { mutableStateOf(false) }
    var showNewMessageDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        topBar = {
            Column(
                modifier = Modifier
                    .background(ObsidianSurface)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "OTPSecure",
                        style = MaterialTheme.typography.titleLarge,
                        color = LightText,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Sync Device SMS History
                        IconButton(
                            onClick = {
                                if (SmsManagerHelper.hasReadSmsPermission(context)) {
                                    viewModel.syncDeviceSmsHistory(context) { count ->
                                        Toast.makeText(
                                            context,
                                            if (count > 0) "Synced $count messages from history" else "SMS history is up to date",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Please grant SMS permission from the Cards view", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SafePrimary.copy(alpha = 0.1f))
                                .border(1.dp, SafePrimary.copy(alpha = 0.3f), CircleShape)
                                .testTag("inbox_sync_sms_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync SMS History",
                                tint = SafePrimary
                            )
                        }

                        // GitHub Releases Download Link Button
                        IconButton(
                            onClick = { showGitHubReleaseDialog = true },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SafePrimary.copy(alpha = 0.1f))
                                .border(1.dp, SafePrimary.copy(alpha = 0.3f), CircleShape)
                                .testTag("inbox_github_releases_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "GitHub Releases",
                                tint = SafePrimary
                            )
                        }

                        // Switch to SMS Cards View
                        IconButton(
                            onClick = onOpenSmsCards,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SafePrimary.copy(alpha = 0.1f))
                                .border(1.dp, SafePrimary.copy(alpha = 0.3f), CircleShape)
                                .testTag("open_sms_cards_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewAgenda,
                                contentDescription = "SMS Message Cards",
                                tint = SafePrimary
                            )
                        }

                        // Vault Shield Button Indicator
                        IconButton(
                            onClick = onEnterVault,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SecureEmerald.copy(alpha = 0.1f))
                                .border(1.dp, SecureEmerald.copy(alpha = 0.3f), CircleShape)
                                .testTag("vault_gate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Vault Gate",
                                tint = SecureEmerald
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Modern Search Field with Custom Design
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .testTag("inbox_search_query"),
                    placeholder = { Text("Search safe messages & logs...", color = MutedText) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MutedText) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MutedText)
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = ObsidianBg,
                        unfocusedContainerColor = ObsidianBg,
                        disabledContainerColor = ObsidianBg,
                        focusedTextColor = LightText,
                        unfocusedTextColor = LightText,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = ObsidianSurface,
                tonalElevation = 4.dp
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenSmsCards,
                    icon = { Icon(Icons.Default.ViewAgenda, contentDescription = "SMS Cards") },
                    label = { Text("SMS Cards") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SafePrimary,
                        indicatorColor = SafePrimary.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_sms_cards")
                )
                NavigationBarItem(
                    selected = true,
                    onClick = { /* already on inbox */ },
                    icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Conversations") },
                    label = { Text("Conversations") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SafePrimary,
                        selectedTextColor = SafePrimary,
                        indicatorColor = SafePrimary.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_inbox")
                )
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    showNewMessageDialog = true
                },
                modifier = Modifier.testTag("inbox_compose_sms_fab"),
                containerColor = SafePrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = "New Message")
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Message", fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live status panel
            SecurityStatusBar(onVaultClick = onEnterVault)

            if (threads.isEmpty()) {
                EmptyStateInbox(isFiltering = searchQuery.isNotEmpty())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = rememberLazyListState()
                ) {
                    items(threads, key = { it.threadId }) { thread ->
                        ConversationItem(
                            thread = thread,
                            onClick = { onSelectThread(thread.threadId, thread.sender) }
                        )
                    }
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
                onSelectThread(
                    recipient.trim().replace(" ", "_").lowercase(),
                    recipient
                )
            }
        )
    }
}

@Composable
fun SecurityStatusBar(onVaultClick: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val ObsidianBg = if (isDark) ObsidianBgDark else ObsidianBgLight
    val ObsidianSurface = if (isDark) ObsidianSurfaceDark else ObsidianSurfaceLight
    val ObsidianBorder = if (isDark) ObsidianBorderDark else ObsidianBorderLight
    val LightText = if (isDark) LightTextDark else LightTextLight
    val MutedText = MutedTextDark

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SecureEmerald.copy(alpha = 0.05f))
            .clickable { onVaultClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(SecureEmerald)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Advanced filter shielding armed & active.",
            color = SecureEmerald,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "OPEN VAULT",
            color = SecureEmerald,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = "Go",
            tint = SecureEmerald,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
fun ConversationItem(
    thread: ConversationThread,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val ObsidianBg = if (isDark) ObsidianBgDark else ObsidianBgLight
    val ObsidianSurface = if (isDark) ObsidianSurfaceDark else ObsidianSurfaceLight
    val ObsidianBorder = if (isDark) ObsidianBorderDark else ObsidianBorderLight
    val LightText = if (isDark) LightTextDark else LightTextLight
    val MutedText = MutedTextDark

    val formatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = formatter.format(Date(thread.latestMessage.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("thread_card_${thread.threadId}"),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle Avatars
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SafePrimary.copy(alpha = 0.1f))
                    .border(1.dp, SafePrimary.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val initialLetter = thread.sender.firstOrNull()?.toString() ?: "U"
                Text(
                    text = initialLetter.uppercase(),
                    color = SafePrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = thread.sender,
                        fontWeight = FontWeight.Bold,
                        color = LightText,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = thread.latestMessage.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Indicator badge for priority classification
                    if (thread.isOtpThread) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SecureEmerald.copy(alpha = 0.15f))
                                .border(0.5.dp, SecureEmerald, RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "OTP PROTECT",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SecureEmerald
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateInbox(isFiltering: Boolean) {
    val isDark = isSystemInDarkTheme()
    val ObsidianBg = if (isDark) ObsidianBgDark else ObsidianBgLight
    val ObsidianSurface = if (isDark) ObsidianSurfaceDark else ObsidianSurfaceLight
    val ObsidianBorder = if (isDark) ObsidianBorderDark else ObsidianBorderLight
    val LightText = if (isDark) LightTextDark else LightTextLight
    val MutedText = MutedTextDark

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isFiltering) Icons.Default.Search else Icons.Default.Mail,
            contentDescription = "Empty",
            tint = MutedText,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isFiltering) "No results matches search criteria" else "Your inbox is secured & clean",
            fontWeight = FontWeight.Bold,
            color = LightText,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isFiltering) "Try adjusting some query descriptors." else "No messages found. Sync with your device SMS history or start a new conversation.",
            color = MutedText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ==========================================
// 3. SECURE VAULT AUTHORIZATION CARD / LOCK SCREEN
// ==========================================

@Composable
fun VaultLockScreen(
    viewModel: MessageViewModel,
    onBackPressed: () -> Unit,
    onSuccess: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val ObsidianBg = if (isDark) ObsidianBgDark else ObsidianBgLight
    val ObsidianSurface = if (isDark) ObsidianSurfaceDark else ObsidianSurfaceLight
    val ObsidianBorder = if (isDark) ObsidianBorderDark else ObsidianBorderLight
    val LightText = if (isDark) LightTextDark else LightTextLight
    val MutedText = MutedTextDark

    val isVaultPinSetup by viewModel.isVaultPinSetup.collectAsState()
    val pinInput by viewModel.vaultPinInput.collectAsState()
    val errorMessage by viewModel.vaultErrorMessage.collectAsState()
    val scope = rememberCoroutineScope()

    // Simulated Biometric Scanner Touch States
    val fingerprintInteractionSource = remember { MutableInteractionSource() }
    val isPressed by fingerprintInteractionSource.collectIsPressedAsState()

    var biometricProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            biometricProgress = 0f
            val animateJob = scope.launch {
                while (biometricProgress < 1f) {
                    delay(30)
                    biometricProgress += 0.05f
                }
                biometricProgress = 1f
                // Auth Success!
                viewModel.tryBiometricAuth {
                    onSuccess()
                }
            }
            animateJob.invokeOnCompletion {
                if (!isVaultPinSetup) {
                    biometricProgress = 0f
                }
            }
        } else {
            biometricProgress = 0f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Vault Navigation Top row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = LightText
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Secure Cryptographic Gate",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = LightText
            )
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Shield Status Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(SecureEmerald.copy(alpha = 0.05f))
                .border(2.dp, SecureEmerald.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = SecureEmerald,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isVaultPinSetup) "Enter Security PIN" else "Configure Secure 6-Digit PIN",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = LightText
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isVaultPinSetup) "Access isolate financial transcripts & transaction verification OTPs." else "Setup a master security PIN key. This unlocks your local security container.",
            fontSize = 13.sp,
            color = MutedText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Tactile PIN indicator dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(6) { index ->
                val filled = index < pinInput.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) SecureEmerald else ObsidianSurface
                        )
                        .border(
                            2.dp,
                            if (filled) SecureEmerald else ObsidianBorder,
                            CircleShape
                        )
                )
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage!!,
                color = Color.Red,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.weight(0.15f))

        // Real-time simulated Biometric Fingerprint pad
        if (isVaultPinSetup) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(ObsidianSurface)
                    .border(2.dp, ObsidianBorder, CircleShape)
                    .clickable(
                        interactionSource = fingerprintInteractionSource,
                        indication = LocalIndication.current,
                        onClick = {}
                    )
                    .testTag("biometric_scanner_spot"),
                contentAlignment = Alignment.Center
            ) {
                // Biometrics Scan Ripple Indicator based on holding progress
                if (isPressed) {
                    CircularProgressIndicator(
                        progress = { biometricProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = SecureEmerald,
                        strokeWidth = 3.dp
                    )
                }
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Sensor",
                    tint = if (isPressed) SecureEmerald else MutedText,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isPressed) "Scanning Biometrics..." else "Press & hold for Biometric scan",
                fontSize = 11.sp,
                color = if (isPressed) SecureEmerald else MutedText,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Professional cryptographic keypad Custom Grid UI
        Column(
            modifier = Modifier.fillMaxWidth(0.85f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("CLEAR", "0", "DEL")
            )

            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEach { button ->
                        TextButton(
                            onClick = {
                                when (button) {
                                    "CLEAR" -> viewModel.clearPin()
                                    "DEL" -> viewModel.deletePinDigit()
                                    else -> {
                                        viewModel.appendPinDigit(button)
                                        // Automated validation submission when reaches 6 digest
                                        if (pinInput.length == 5) {
                                            // The append adds the 6th, so launch verification delay auto submit
                                            scope.launch {
                                                delay(80)
                                                viewModel.submitPin(onSuccess)
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("pinpad_$button")
                        ) {
                            Text(
                                text = button,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (button == "CLEAR" || button == "DEL") SecureEmerald else LightText
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. VAULT INSIDE DETAIL PAGE (SECURE VAULT)
// ==========================================

@Composable
fun VaultScreen(
    viewModel: MessageViewModel,
    onBackPressed: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val ObsidianBg = if (isDark) ObsidianBgDark else ObsidianBgLight
    val ObsidianSurface = if (isDark) ObsidianSurfaceDark else ObsidianSurfaceLight
    val ObsidianBorder = if (isDark) ObsidianBorderDark else ObsidianBorderLight
    val LightText = if (isDark) LightTextDark else LightTextLight
    val MutedText = MutedTextDark

    val messages by viewModel.vaultMessages.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        topBar = {
            Column(
                modifier = Modifier
                    .background(ObsidianSurface)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackPressed) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = LightText
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Shield",
                            tint = SecureEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Security Vault",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = LightText
                        )
                    }

                    // Lock Button
                    Button(
                        onClick = onBackPressed,
                        colors = ButtonDefaults.buttonColors(containerColor = SecureEmerald.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, SecureEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("lock_vault_btn")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock", tint = SecureEmerald, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lock Vault", color = SecureEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Vault Info Header Alert box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = SecureEmerald.copy(alpha = 0.05f)),
                border = BorderStroke(0.5.dp, SecureEmerald)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = SecureEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "72 Hour Purge Policy Secured",
                            fontWeight = FontWeight.Bold,
                            color = LightText,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Expired security OTP credentials are auto-purged on interval triggers sequentially to prevent storage leaks.",
                            color = MutedText,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secure",
                            tint = MutedText,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Vault messages identified yet.",
                            color = LightText,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Secure OTPs and Statements will route here.",
                            color = MutedText,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        VaultLogItem(
                            message = msg,
                            onCopyClick = {
                                msg.otpCode?.let { code ->
                                    clipboardManager.setText(AnnotatedString(code))
                                    Toast.makeText(context, "${msg.sender} OTP $code copied to clipboard", Toast.LENGTH_SHORT).show()
                                } ?: run {
                                    clipboardManager.setText(AnnotatedString(msg.body))
                                    Toast.makeText(context, "Copied log text info", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VaultLogItem(
    message: MessageEntity,
    onCopyClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val ObsidianBg = if (isDark) ObsidianBgDark else ObsidianBgLight
    val ObsidianSurface = if (isDark) ObsidianSurfaceDark else ObsidianSurfaceLight
    val ObsidianBorder = if (isDark) ObsidianBorderDark else ObsidianBorderLight
    val LightText = if (isDark) LightTextDark else LightTextLight
    val MutedText = MutedTextDark

    val formatter = remember { SimpleDateFormat("hh:mm a - d MMM", Locale.getDefault()) }
    val timeLabel = formatter.format(Date(message.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("vault_message_${message.id}"),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (message.isOtp) SecureEmerald.copy(alpha = 0.1f) else WarningAmber.copy(alpha = 0.1f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (message.isOtp) Icons.Default.Lock else Icons.Default.Lock,
                            contentDescription = "Identifier icon",
                            tint = if (message.isOtp) SecureEmerald else WarningAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = message.sender,
                            fontWeight = FontWeight.Bold,
                            color = LightText,
                            fontSize = 15.sp
                        )
                        Text(
                            text = timeLabel,
                            fontSize = 10.sp,
                            color = MutedText
                        )
                    }
                }

                if (message.isOtp) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SecureEmerald.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "OTP REGISTRY",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = SecureEmerald
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(WarningAmber.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "FINANCIAL STATEMENT",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarningAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = LightText,
                lineHeight = 20.sp
            )

            // Dynamic Token Action block
            if (message.isOtp && message.otpCode != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianBg)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "EXTRACTED PIN: ",
                            fontSize = 11.sp,
                            color = MutedText,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = message.otpCode,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SecureEmerald,
                            letterSpacing = 2.sp
                        )
                    }

                    Button(
                        onClick = onCopyClick,
                        colors = ButtonDefaults.buttonColors(containerColor = SecureEmerald),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("copy_token_btn_${message.id}")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Copy", modifier = Modifier.size(12.dp), tint = ObsidianBg)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", color = ObsidianBg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. CONVERSATIONAL ENGINE (CHAT MODE VIEW)
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MessageViewModel,
    senderName: String,
    onBackPressed: () -> Unit,
    onToggleAttachment: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val ObsidianBg = if (isDark) ObsidianBgDark else ObsidianBgLight
    val ObsidianSurface = if (isDark) ObsidianSurfaceDark else ObsidianSurfaceLight
    val ObsidianBorder = if (isDark) ObsidianBorderDark else ObsidianBorderLight
    val LightText = if (isDark) LightTextDark else LightTextLight
    val MutedText = MutedTextDark

    val messages by viewModel.activeThreadMessages.collectAsState()
    val smartReplies = viewModel.smartReplies
    
    var replyText by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Scroll to latest message on open or when count updates
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SafePrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = senderName.firstOrNull()?.uppercase() ?: "C",
                                color = SafePrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = senderName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = LightText)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflowMenu = !showOverflowMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = LightText)
                        }
                        
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            modifier = Modifier.background(ObsidianSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy Number", color = LightText) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = "Copy", tint = MutedText) },
                                onClick = {
                                    showOverflowMenu = false
                                    // Sim copy actions
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear View Context", color = LightText) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Clear", tint = MutedText) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.clearActiveThread()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Thread", color = LightText) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MutedText) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.deleteActiveThread()
                                    onBackPressed()
                                }
                            )
                            Divider(color = ObsidianBorder)
                            DropdownMenuItem(
                                text = { Text("Mark as Spam", color = Color.Red) },
                                leadingIcon = { Icon(Icons.Default.Warning, contentDescription = "Spam", tint = Color.Red) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.markActiveThreadAsSpam()
                                    onBackPressed()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianSurface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Continuous array logs rendering
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                state = listState
            ) {
                item { Spacer(modifier = Modifier.height(12.dp)) }
                
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }

            // Input Toolbar and extensions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            ) {
                // Emoji & Smart Replies chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    smartReplies.forEach { chip ->
                        SuggestionChipCustom(
                            text = chip,
                            onClick = {
                                replyText += if (replyText.isEmpty()) chip else " $chip"
                            }
                        )
                    }
                }

                // Chat Input box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attachment trigger
                    IconButton(
                        onClick = onToggleAttachment,
                        modifier = Modifier
                            .testTag("attachment_sheet_btn")
                            .clip(CircleShape)
                            .background(ObsidianBg)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Attach Options", tint = SafePrimary)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .testTag("chat_input_text"),
                        placeholder = { Text("Secure message text...", color = MutedText, fontSize = 14.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = ObsidianBg,
                            unfocusedContainerColor = ObsidianBg,
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = false,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Transmission execution
                    IconButton(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                viewModel.sendDirectMessage(replyText, context)
                                replyText = ""
                            }
                        },
                        modifier = Modifier
                            .testTag("send_btn")
                            .clip(CircleShape)
                            .background(SafePrimary),
                        enabled = replyText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: MessageEntity) {
    val isDark = isSystemInDarkTheme()
    val ObsidianBg = if (isDark) ObsidianBgDark else ObsidianBgLight
    val ObsidianSurface = if (isDark) ObsidianSurfaceDark else ObsidianSurfaceLight
    val ObsidianBorder = if (isDark) ObsidianBorderDark else ObsidianBorderLight
    val LightText = if (isDark) LightTextDark else LightTextLight
    val MutedText = MutedTextDark
    val BubbleFromMe = if (isDark) BubbleFromMeDark else BubbleFromMeLight
    val BubbleToMe = if (isDark) BubbleToMeDark else BubbleToMeLight

    val alignEnd = message.isFromMe
    val formatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val bubbleTime = formatter.format(Date(message.timestamp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            if (alignEnd) {
                Text(
                    text = bubbleTime,
                    fontSize = 9.sp,
                    color = MutedText,
                    modifier = Modifier.padding(end = 6.dp, bottom = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (alignEnd) 16.dp else 2.dp,
                            bottomEnd = if (alignEnd) 2.dp else 16.dp
                        )
                    )
                    .background(
                        if (alignEnd) BubbleToMe else BubbleFromMe
                    )
                    .border(
                        1.dp,
                        if (alignEnd) SafePrimary.copy(alpha = 0.3f) else ObsidianBorder,
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (alignEnd) 16.dp else 2.dp,
                            bottomEnd = if (alignEnd) 2.dp else 16.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.body,
                    color = LightText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }

            if (!alignEnd) {
                Text(
                    text = bubbleTime,
                    fontSize = 9.sp,
                    color = MutedText,
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
                )
            }
        }
    }
}

// ==========================================
// 6. ATTACHMENT BOTTOM SHEET COMPONENT (MODERN MATERIAL SHEET STYLE)
// ==========================================

@Composable
fun SuggestionChipCustom(
    text: String,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val ObsidianBg = if (isDark) ObsidianBgDark else ObsidianBgLight
    val ObsidianSurface = if (isDark) ObsidianSurfaceDark else ObsidianSurfaceLight
    val ObsidianBorder = if (isDark) ObsidianBorderDark else ObsidianBorderLight
    val LightText = if (isDark) LightTextDark else LightTextLight
    val MutedText = MutedTextDark

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianBg)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = LightText,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
fun AttachmentBottomBar(onClose: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val ObsidianBg = if (isDark) ObsidianBgDark else ObsidianBgLight
    val ObsidianSurface = if (isDark) ObsidianSurfaceDark else ObsidianSurfaceLight
    val ObsidianBorder = if (isDark) ObsidianBorderDark else ObsidianBorderLight
    val LightText = if (isDark) LightTextDark else LightTextLight
    val MutedText = MutedTextDark

    val items = listOf(
        AttachmentOption("Gallery", Icons.Default.Image, Color(0xFFE91E63)),
        AttachmentOption("Camera", Icons.Default.Edit, Color(0xFF00BCD4)),
        AttachmentOption("GIFs", Icons.Default.PlayArrow, Color(0xFFFF9800)),
        AttachmentOption("Stickers", Icons.Default.Face, Color(0xFF4CAF50)),
        AttachmentOption("Files", Icons.Default.Share, Color(0xFF9C27B0)),
        AttachmentOption("Location", Icons.Default.Place, Color(0xFFE53935))
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
        ) {
            // Drag Indicator Handle
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(ObsidianBorder)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Grid Layout
            val chunked = items.chunked(3)
            chunked.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    row.forEach { option ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { onClose() }
                                .width(90.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(option.color.copy(alpha = 0.15f))
                                    .border(1.dp, option.color.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = option.name,
                                    tint = option.color,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = option.name,
                                color = LightText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

data class AttachmentOption(val name: String, val icon: ImageVector, val color: Color)
