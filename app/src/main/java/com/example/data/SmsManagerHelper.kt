package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat

data class DeviceSms(
    val id: Long,
    val threadId: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val isFromMe: Boolean
)

object SmsManagerHelper {
    private const val TAG = "SmsManagerHelper"

    val REQUIRED_SMS_PERMISSIONS = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS
    )

    fun hasReadSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAllSmsPermissions(context: Context): Boolean {
        return REQUIRED_SMS_PERMISSIONS.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Reads SMS message history from Android system Telephony Content Provider.
     * Requires Manifest.permission.READ_SMS.
     */
    fun readDeviceSmsHistory(context: Context, limit: Int = 100): List<DeviceSms> {
        if (!hasReadSmsPermission(context)) {
            Log.w(TAG, "Cannot read SMS history: READ_SMS permission is not granted.")
            return emptyList()
        }

        val messages = mutableListOf<DeviceSms>()
        val uri: Uri = Telephony.Sms.CONTENT_URI

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.let { c ->
                val idIndex = c.getColumnIndex(Telephony.Sms._ID)
                val threadIdIndex = c.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIndex = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = c.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = c.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = c.getColumnIndex(Telephony.Sms.TYPE)

                while (c.moveToNext()) {
                    val id = if (idIndex != -1) c.getLong(idIndex) else 0L
                    val rawThreadId = if (threadIdIndex != -1) c.getLong(threadIdIndex) else 0L
                    val address = if (addressIndex != -1) c.getString(addressIndex) ?: "Unknown" else "Unknown"
                    val body = if (bodyIndex != -1) c.getString(bodyIndex) ?: "" else ""
                    val date = if (dateIndex != -1) c.getLong(dateIndex) else System.currentTimeMillis()
                    val type = if (typeIndex != -1) c.getInt(typeIndex) else Telephony.Sms.MESSAGE_TYPE_INBOX
                    val isFromMe = (type == Telephony.Sms.MESSAGE_TYPE_SENT)

                    val threadKey = if (rawThreadId > 0) {
                        "thread_$rawThreadId"
                    } else {
                        address.trim().replace(" ", "_").lowercase()
                    }

                    messages.add(
                        DeviceSms(
                            id = id,
                            threadId = threadKey,
                            sender = address,
                            body = body,
                            timestamp = date,
                            isFromMe = isFromMe
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while reading SMS history: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception while querying SMS content provider: ${e.message}", e)
        } finally {
            cursor?.close()
        }

        return messages
    }

    fun hasSendSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Dispatches real SMS message via Android Telephony SmsManager.
     * Requires Manifest.permission.SEND_SMS.
     */
    fun sendSmsMessage(context: Context, destinationAddress: String, messageText: String): Boolean {
        if (!hasSendSmsPermission(context)) {
            Log.w(TAG, "Cannot send SMS: SEND_SMS permission is not granted.")
            return false
        }
        return try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                context.getSystemService(android.telephony.SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(messageText)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(destinationAddress, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(destinationAddress, null, messageText, null, null)
            }
            Log.d(TAG, "SMS successfully dispatched to $destinationAddress")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch SMS to $destinationAddress: ${e.message}", e)
            false
        }
    }
}
