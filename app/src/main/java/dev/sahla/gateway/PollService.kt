package dev.sahla.gateway

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class PollService : Service() {
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val store=Store(this)
        if(!store.paired || !store.gatewayEnabled){ stopSelf(); return START_NOT_STICKY }
        startForeground(NOTIF_ID,notification("Connected — waiting for messages"))
        if(job?.isActive!=true) job=scope.launch { loop() }
        return START_STICKY
    }
    override fun onDestroy(){ job?.cancel(); super.onDestroy() }
    private suspend fun loop(){ val store=Store(this); while(currentCoroutineContext().isActive && store.gatewayEnabled){ val key=store.apiKey; val id=store.deviceId; if(key.isNullOrBlank()||id.isNullOrBlank()){stopSelf();return}; Api.poll(store.baseUrl,key,id).onSuccess { pending -> if(pending.isNotEmpty()) notify("Sending ${pending.size} message(s)…"); pending.forEach { deliver(store,key,it); delay(store.sendDelaySeconds*1000L) }; if(pending.isEmpty()) notify("Connected — waiting for messages") }.onFailure { notify("Connection problem — retrying") }; delay(5000) } }
    private fun deliver(store:Store,key:String,msg:Api.Pending){ try { val manager=smsManager(msg.simSlot ?: store.defaultSimSlot); val parts=manager.divideMessage(msg.body); val sent=ArrayList<PendingIntent>(); val delivered=ArrayList<PendingIntent>(); parts.indices.forEach { i -> val extras=Intent(this,SmsStatusReceiver::class.java).putExtra("messageId",msg.id).putExtra("address",msg.recipient).putExtra("body",msg.body); sent.add(PendingIntent.getBroadcast(this,(msg.id+"s"+i).hashCode(),Intent(extras).setAction("dev.sahla.gateway.SMS_SENT"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)); delivered.add(PendingIntent.getBroadcast(this,(msg.id+"d"+i).hashCode(),Intent(extras).setAction("dev.sahla.gateway.SMS_DELIVERED"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)) }; manager.sendMultipartTextMessage(msg.recipient,null,parts,sent,delivered) } catch(e:Exception){ Api.reportStatus(store.baseUrl,key,msg.id,"failed",e.message ?: "Send failed"); MessageLog.add(this,LocalMessage(msg.recipient,msg.body,"sent","failed",System.currentTimeMillis())) } }
    private fun smsManager(slot:Int):SmsManager=try { val sub=getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList?.firstOrNull{it.simSlotIndex==slot-1}; if(sub==null) defaultManager() else if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S)getSystemService(SmsManager::class.java).createForSubscriptionId(sub.subscriptionId) else SmsManager.getSmsManagerForSubscriptionId(sub.subscriptionId) }catch(_:Exception){defaultManager()}
    @Suppress("DEPRECATION") private fun defaultManager():SmsManager=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S)getSystemService(SmsManager::class.java) else SmsManager.getDefault()
    private fun notify(text:String)=getSystemService(NotificationManager::class.java).notify(NOTIF_ID,notification(text))
    private fun notification(text:String):Notification{ val manager=getSystemService(NotificationManager::class.java); if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) manager.createNotificationChannel(NotificationChannel(CHANNEL,"Sahla SMS gateway",NotificationManager.IMPORTANCE_LOW)); val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT); return NotificationCompat.Builder(this,CHANNEL).setContentTitle("Sahla SMS").setContentText(text).setSmallIcon(android.R.drawable.stat_notify_chat).setContentIntent(open).setOngoing(Store(this).stickyNotification).build() }
    companion object{ const val CHANNEL="sahla_gateway"; const val NOTIF_ID=42 }
}
