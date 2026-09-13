package dev.sahla.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store(context)
        if (!store.receiveSms || intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        Telephony.Sms.Intents.getMessagesFromIntent(intent).groupBy { it.originatingAddress ?: "Unknown" }.forEach { (address, parts) ->
            MessageLog.add(context, LocalMessage(address, parts.joinToString("") { it.messageBody }, "received", "received", System.currentTimeMillis()))
        }
    }
}
