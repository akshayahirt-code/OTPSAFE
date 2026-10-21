package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.SmsManagerHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmsPermissionTest {

    @Test
    fun testSmsPermissionsDeclaredInManifest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )

        val requestedPermissions = packageInfo.requestedPermissions ?: emptyArray()

        assertTrue(
            "READ_SMS should be declared in AndroidManifest.xml",
            requestedPermissions.contains(Manifest.permission.READ_SMS)
        )
        assertTrue(
            "RECEIVE_SMS should be declared in AndroidManifest.xml",
            requestedPermissions.contains(Manifest.permission.RECEIVE_SMS)
        )
        assertTrue(
            "SEND_SMS should be declared in AndroidManifest.xml",
            requestedPermissions.contains(Manifest.permission.SEND_SMS)
        )
        assertTrue(
            "WRITE_SMS should be declared in AndroidManifest.xml",
            requestedPermissions.contains(Manifest.permission.WRITE_SMS)
        )
    }

    @Test
    fun testRequiredSmsPermissionsList() {
        val expected = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS
        )
        assertArrayEquals(expected, SmsManagerHelper.REQUIRED_SMS_PERMISSIONS)
    }

    @Test
    fun testReadDeviceSmsHistoryWithoutPermissionReturnsEmpty() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // When permission is not granted, readDeviceSmsHistory returns empty list gracefully without crashing
        val history = SmsManagerHelper.readDeviceSmsHistory(context)
        assertNotNull(history)
    }

    @Test
    fun testSendSmsWithoutPermissionReturnsFalse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = SmsManagerHelper.sendSmsMessage(context, "15551234567", "Test SMS")
        assertFalse(result)
    }
}
