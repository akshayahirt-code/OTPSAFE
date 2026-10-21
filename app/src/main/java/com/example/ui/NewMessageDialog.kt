package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.SmsManagerHelper
import com.example.ui.theme.*

@Composable
fun NewMessageDialog(
    onDismissRequest: () -> Unit,
    onSendMessage: (recipient: String, message: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val surfaceColor = if (isDark) ObsidianSurfaceDark else ObsidianSurfaceLight
    val borderColor = if (isDark) ObsidianBorderDark else ObsidianBorderLight
    val primaryText = if (isDark) LightTextDark else LightTextLight
    val secondaryText = MutedTextDark

    var recipient by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    val hasSendPermission = remember { SmsManagerHelper.hasSendSmsPermission(context) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .testTag("new_message_dialog"),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            border = BorderStroke(1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(SafePrimary.copy(alpha = 0.15f))
                        .border(1.5.dp, SafePrimary.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "New Message",
                        tint = SafePrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "New Message",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = primaryText
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Compose and send SMS with OTP isolation",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Recipient Input
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("To (Phone number or contact)") },
                    placeholder = { Text("+1 (555) 000-0000 or Name") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Recipient",
                            tint = SafePrimary
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = primaryText,
                        unfocusedTextColor = primaryText,
                        focusedBorderColor = SafePrimary,
                        unfocusedBorderColor = borderColor,
                        focusedLabelColor = SafePrimary,
                        unfocusedLabelColor = secondaryText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recipient_input_field")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Message Text Input
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Message") },
                    placeholder = { Text("Type message text...") },
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = primaryText,
                        unfocusedTextColor = primaryText,
                        focusedBorderColor = SafePrimary,
                        unfocusedBorderColor = borderColor,
                        focusedLabelColor = SafePrimary,
                        unfocusedLabelColor = secondaryText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("message_input_field")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Status info chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasSendPermission) SecureEmerald.copy(alpha = 0.1f) else SafePrimary.copy(alpha = 0.1f),
                    border = BorderStroke(
                        0.5.dp,
                        if (hasSendPermission) SecureEmerald.copy(alpha = 0.3f) else SafePrimary.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasSendPermission) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (hasSendPermission) SecureEmerald else SafePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasSendPermission) "SMS transmission enabled" else "Local encrypted storage mode",
                            fontSize = 11.sp,
                            color = primaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cancel_new_message_btn"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Text("Cancel", color = secondaryText)
                    }

                    Button(
                        onClick = {
                            val cleanRecipient = recipient.trim()
                            val cleanBody = messageText.trim()
                            if (cleanRecipient.isBlank()) {
                                Toast.makeText(context, "Please enter a recipient", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (cleanBody.isBlank()) {
                                Toast.makeText(context, "Please enter a message", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onSendMessage(cleanRecipient, cleanBody)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("send_new_message_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = SafePrimary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = recipient.isNotBlank() && messageText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
