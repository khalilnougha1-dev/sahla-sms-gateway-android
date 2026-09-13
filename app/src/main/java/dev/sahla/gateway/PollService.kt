package dev.sahla.gateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service: polls /gateway-poll every few seconds, sends each queued
 * message through the phone's SIM, then reports the result to /message-status.
 */
class PollService : Service() {

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Waiting for messages…"))
        if (job?.isActive != true) job = scope.launch { loop() }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private suspend fun loop() {
        val store = Store(this)
        while (scope.isActive) {
            val key = store.apiKey
            if (key.isNullOrBlank()) {
                stopSelf()
                return
            }
            Api.poll(store.baseUrl, key)
                .onSuccess { pending ->
                    if (pending.isNotEmpty()) notify("Sending ${pending.size} message(s)…")
                    pending.forEach { msg -> deliver(store, key, msg) }
                    if (pending.isEmpty()) notify("Connected — waiting for messages")
                }
                .onFailure { notify("Connection problem: ${it.message}") }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun deliver(store: Store, key: String, msg: Api.Pending) {
        try {
            smsManager(msg.simSlot).sendMultipartTextMessage(
                msg.recipient,
                null,
                smsManager(msg.simSlot).divideMessage(msg.body),
                null,
                null,
            )
            Api.reportStatus(store.baseUrl, key, msg.id, "sent")
            Api.reportStatus(store.baseUrl, key, msg.id, "delivered")
        } catch (e: Exception) {
            Api.reportStatus(store.baseUrl, key, msg.id, "failed", e.message ?: "Send failed")
        }
    }

    /** Picks the SmsManager bound to the requested SIM slot, or the default SIM. */
    private fun smsManager(simSlot: Int?): SmsManager {
        if (simSlot == null) return defaultSmsManager()
        return try {
            val sm = getSystemService(SubscriptionManager::class.java)
            val sub = sm?.activeSubscriptionInfoList?.firstOrNull { it.simSlotIndex == simSlot - 1 }
            if (sub == null) defaultSmsManager()
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                getSystemService(SmsManager::class.java).createForSubscriptionId(sub.subscriptionId)
            else SmsManager.getSmsManagerForSubscriptionId(sub.subscriptionId)
        } catch (_: SecurityException) {
            defaultSmsManager()
        }
    }

    @Suppress("DEPRECATION")
    private fun defaultSmsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(SmsManager::class.java)
        else SmsManager.getDefault()

    private fun notify(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Sahla gateway", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Sahla SMS gateway")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "sahla_gateway"
        private const val NOTIF_ID = 42
        private const val POLL_INTERVAL_MS = 5000L
    }
}
