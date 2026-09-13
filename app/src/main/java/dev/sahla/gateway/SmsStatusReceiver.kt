package dev.sahla.gateway

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id=intent.getStringExtra("messageId") ?: return
        val address=intent.getStringExtra("address") ?: "Unknown"
        val body=intent.getStringExtra("body") ?: ""
        val store=Store(context); val key=store.apiKey ?: return
        val status=when(intent.action){ "dev.sahla.gateway.SMS_DELIVERED" -> if(resultCode==Activity.RESULT_OK) "delivered" else "failed"; else -> if(resultCode==Activity.RESULT_OK) "sent" else "failed" }
        Thread { Api.reportStatus(store.baseUrl,key,id,status,if(status=="failed") "Android SMS result: $resultCode" else null) }.start()
        if(intent.action=="dev.sahla.gateway.SMS_SENT") MessageLog.add(context, LocalMessage(address,body,"sent",status,System.currentTimeMillis()))
    }
}
