package com.github.tmo1.sms_ie.base

import android.app.Notification
import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Retrofit
import javax.inject.Inject

@AndroidEntryPoint
class MyNotificationListener : NotificationListenerService() {
    private val TAG = "NOTIFICATION_LOG"

    @Inject
    lateinit var retrofit: Retrofit

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: ${retrofit.baseUrl()}")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.also {
            Log.d(TAG, "PACKAGE_NAME: ${sbn.packageName}")
            Log.d(TAG, "TICKER_TEXT: ${sbn.notification.tickerText}")
            Log.d(TAG, "EXTRA_TITLE: ${sbn.notification.extras.getString(Notification.EXTRA_TITLE)}")
            Log.d(TAG, "EXTRA_TEXT: ${sbn.notification.extras.getString(Notification.EXTRA_TEXT)}")
            Log.d(TAG, "EXTRA_BIG_TEXT: ${sbn.notification.extras.getString(Notification.EXTRA_BIG_TEXT)}")
        }
    }
}