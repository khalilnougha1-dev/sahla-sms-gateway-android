package dev.sahla.gateway

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.sahla.gateway.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var store: Store
    private val orange = Color.rgb(206, 90, 0)
    private val white = Color.rgb(246, 247, 250)
    private val muted = Color.rgb(170, 178, 195)
    private val surface = Color.rgb(40, 45, 57)
    private var currentTab = "dashboard"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = Store(this)
        requestPermissions()
        render()
    }

    override fun onResume() { super.onResume(); if (::store.isInitialized && store.paired) render() }

    private fun requestPermissions() {
        val needed = mutableListOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) needed += Manifest.permission.READ_PHONE_NUMBERS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) needed += Manifest.permission.POST_NOTIFICATIONS
        val missing = needed.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 7)
    }

    private fun render() {
        binding.root.removeAllViews()
        if (!store.paired) renderPairing() else renderShell()
    }

    private fun renderPairing() {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val col = column(24).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(26), dp(42), dp(26), dp(32)) }
        col.addView(ImageView(this).apply { setImageResource(dev.sahla.gateway.R.drawable.sahla_logo); scaleType = ImageView.ScaleType.CENTER_CROP }, LinearLayout.LayoutParams(dp(180), dp(180)))
        col.addView(title("Connect your phone", 28).apply { gravity = Gravity.CENTER })
        col.addView(text("Enter the pairing code shown in Devices on the Sahla SMS dashboard.", 15, muted).apply { gravity = Gravity.CENTER; setPadding(0,dp(8),0,dp(22)) })
        val code = edit("Pairing code", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        val base = edit("Server URL", InputType.TYPE_TEXT_VARIATION_URI).apply { setText(store.baseUrl) }
        col.addView(code, match(56, 0, 10)); col.addView(base, match(56, 0, 10))
        val pair = action("Pair device")
        col.addView(pair, match(54, 6, 0))
        col.addView(text("The app sends and receives SMS through the SIM cards in this phone.", 13, muted).apply { gravity=Gravity.CENTER; setPadding(0,dp(18),0,0) })
        pair.setOnClickListener {
            val value = code.text.toString().trim()
            if (value.length < 4) { toast("Enter a valid pairing code"); return@setOnClickListener }
            pair.isEnabled = false; pair.text = "Connecting…"; store.baseUrl = base.text.toString().trim().ifBlank { Store.DEFAULT_BASE }
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { Api.pair(store.baseUrl, value, "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.RELEASE ?: "unknown", sims().size.coerceAtLeast(1)) }
                result.onSuccess { (id,key) -> store.deviceId=id; store.apiKey=key; store.deviceName="${Build.MANUFACTURER} ${Build.MODEL}"; startGateway(); currentTab="dashboard"; render() }
                    .onFailure { pair.isEnabled=true; pair.text="Pair device"; toast(it.message ?: "Pairing failed") }
            }
        }
        scroll.addView(col); binding.root.addView(scroll)
    }

    private fun renderShell() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(16,24,39)) }
        val content = FrameLayout(this).apply { id=View.generateViewId() }
        root.addView(content, LinearLayout.LayoutParams(-1,0,1f))
        val nav = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; setPadding(dp(8),dp(8),dp(8),dp(8)); setBackgroundColor(Color.rgb(47,47,54)) }
        listOf("settings" to "⚙\nSettings", "messages" to "▤\nMessages", "dashboard" to "▦\nDashboard").forEach { (id,label) ->
            val button = TextView(this).apply { text=label; gravity=Gravity.CENTER; textSize=14f; setTextColor(if(currentTab==id) orange else muted); setTypeface(null, if(currentTab==id) Typeface.BOLD else Typeface.NORMAL); setPadding(dp(6),dp(7),dp(6),dp(7)); background=if(currentTab==id) getDrawable(R.drawable.pill) else null; setOnClickListener { currentTab=id; render() } }
            nav.addView(button, LinearLayout.LayoutParams(0,dp(66),1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1,dp(78)))
        binding.root.addView(root)
        when(currentTab) { "messages" -> renderMessages(content); "settings" -> renderSettings(content); else -> renderDashboard(content) }
    }

    private fun page(title: String, icon: String): Pair<ScrollView,LinearLayout> {
        val scroll=ScrollView(this); val col=column(18)
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL or Gravity.END; setPadding(dp(22),dp(20),dp(22),dp(18)); setBackgroundColor(Color.rgb(29,41,59)) }
        header.addView(title(title,26), LinearLayout.LayoutParams(0,-2,1f)); header.addView(title(icon,28))
        col.addView(header, match(-2,0,12)); return scroll to col
    }

    private fun renderDashboard(host: FrameLayout) {
        val (scroll,col)=page("Sahla SMS", "✉")
        col.addView(text("Hello, ${store.deviceName}",15,muted).apply { gravity=Gravity.END; setPadding(dp(20),0,dp(20),0) })
        val card=column(14).apply { background=getDrawable(R.drawable.panel); setPadding(dp(22),dp(22),dp(22),dp(22)) }
        val top=row(); val toggle=switch("Gateway",store.gatewayEnabled) { store.gatewayEnabled=it; if(it) startGateway() else stopGateway() }
        top.addView(toggle,LinearLayout.LayoutParams(0,-2,1f)); top.addView(title(store.deviceName,24),LinearLayout.LayoutParams(0,-2,2f)); card.addView(top)
        card.addView(text("Device ID  ${store.deviceId?.take(20)}…",13,muted).apply { gravity=Gravity.END })
        card.addView(switch("Receive SMS",store.receiveSms) { store.receiveSms=it },match(58,18,8))
        val simTitle=title("SIM Cards",18).apply { gravity=Gravity.END }; card.addView(simTitle)
        val simList=sims(); if(simList.isEmpty()) card.addView(text("SIM information requires phone permission",13,muted).apply { gravity=Gravity.END })
        simList.forEach { card.addView(text("SIM ${it.simSlotIndex+1} · ${it.carrierName}     ID: ${it.subscriptionId}",15,white).apply { gravity=Gravity.END; setPadding(0,dp(7),0,0) }) }
        col.addView(card,match(-2,12,12))
        val subscription=column(10).apply { background=getDrawable(R.drawable.panel); setPadding(dp(22),dp(20),dp(22),dp(20)) }
        subscription.addView(title("Subscription",21).apply { gravity=Gravity.END }); subscription.addView(text("FREE",14,muted).apply { gravity=Gravity.END }); subscription.addView(text("Upgrade for higher limits, more devices and priority support.",14,muted).apply { gravity=Gravity.END })
        col.addView(subscription,match(-2,12,12))
        col.addView(title("Quick Actions",22).apply { gravity=Gravity.END; setPadding(dp(20),dp(10),dp(20),0) })
        val actions=row(); actions.addView(outline("Explore Docs") { openUrl("${store.baseUrl}/docs") },LinearLayout.LayoutParams(0,dp(58),1f).apply { marginEnd=dp(6) }); actions.addView(outline("Dashboard") { openUrl("${store.baseUrl}/dashboard") },LinearLayout.LayoutParams(0,dp(58),1f).apply { marginStart=dp(6) }); col.addView(actions,match(58,12,26))
        scroll.addView(col); host.addView(scroll)
    }

    private fun renderMessages(host: FrameLayout) {
        val (scroll,col)=page("Messages", "▰")
        val controls=row(); var filter="all"
        val list=column(10)
        fun refresh() { list.removeAllViews(); val messages=MessageLog.read(this).filter { filter=="all" || it.direction==filter }; if(messages.isEmpty()) list.addView(text("No messages yet",16,muted).apply { gravity=Gravity.CENTER; setPadding(0,dp(60),0,0) }); messages.forEach { m -> val card=column(7).apply { background=getDrawable(if(m.direction=="received") R.drawable.message_in else R.drawable.message_out); setPadding(dp(18),dp(15),dp(18),dp(15)) }; val line=row(); line.addView(text(SimpleDateFormat("MMM d, HH:mm",Locale.getDefault()).format(Date(m.time)),12,muted),LinearLayout.LayoutParams(0,-2,1f)); line.addView(title(m.address,17)); card.addView(line); card.addView(text(m.body,15,muted)); card.addView(text(m.status.uppercase(),11,if(m.direction=="received") Color.rgb(67,180,92) else orange).apply { gravity=Gravity.END }); list.addView(card,match(-2,0,10)) } }
        listOf("received" to "Received","sent" to "Sent","all" to "All").forEach { (id,label) -> controls.addView(outline(label) { filter=id; refresh() },LinearLayout.LayoutParams(0,dp(50),1f).apply { setMargins(dp(4),0,dp(4),0) }) }
        col.addView(controls,match(50,16,16)); col.addView(list,match(-2,12,24)); refresh(); scroll.addView(col); host.addView(scroll)
    }

    private fun renderSettings(host: FrameLayout) {
        val (scroll,col)=page("Settings", "⚙")
        col.addView(section("ACCOUNT"))
        col.addView(copySetting("Device ID", store.deviceId ?: "—", store.deviceId)); col.addView(copySetting("API Key", maskKey(store.apiKey), store.apiKey))
        col.addView(copySetting("Server URL", store.baseUrl, store.baseUrl))
        val name=setting("Device Name",store.deviceName); name.setOnClickListener { editName() }; col.addView(name)
        col.addView(outline("Disconnect Device") { stopGateway(); store.clear(); currentTab="dashboard"; render() }.apply { setTextColor(Color.rgb(255,82,82)) },match(54,16,10))
        col.addView(section("GATEWAY")); col.addView(switch("Gateway Enabled",store.gatewayEnabled) { store.gatewayEnabled=it; if(it) startGateway() else stopGateway() },match(60,8,5))
        col.addView(simSelector())
        col.addView(section("SMS")); col.addView(switch("Receive SMS",store.receiveSms) { store.receiveSms=it },match(60,8,4)); col.addView(setting("Send Delay","${store.sendDelaySeconds}s between each SMS").apply { setOnClickListener { chooseDelay() } })
        col.addView(section("SYSTEM")); col.addView(switch("Sticky Notification",store.stickyNotification) { store.stickyNotification=it; if(store.gatewayEnabled) startGateway() },match(60,8,4)); col.addView(setting("App Version","2.1 (Build 4)")); col.addView(setting("About","sahla-sms-gateway").apply { setOnClickListener { openUrl("https://github.com/khalilnougha1-dev/sahla-sms-gateway-android") } }); col.addView(setting("Check for Updates","GitHub Releases").apply { setOnClickListener { openUrl("https://github.com/khalilnougha1-dev/sahla-sms-gateway-android/releases/latest") } }); col.addView(section("LEGAL")); col.addView(setting("Privacy Policy",store.baseUrl)); col.addView(space(30))
        scroll.addView(col); host.addView(scroll)
    }

    private fun simSelector(): View {
        val box=column(7).apply { setPadding(dp(18),dp(12),dp(18),dp(12)) }; box.addView(title("Default SIM",18).apply { gravity=Gravity.END })
        val spinner=Spinner(this); val items=sims().map { "${it.carrierName} (SIM ${it.simSlotIndex+1}) · ID: ${it.subscriptionId}" }.ifEmpty { listOf("System default SIM") }; spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,items); spinner.setSelection(store.defaultSimSlot.minus(1).coerceIn(0,items.lastIndex)); spinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}; override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){ store.defaultSimSlot=sims().getOrNull(position)?.simSlotIndex?.plus(1) ?: 1 } }; box.addView(spinner,match(56,4,0)); box.addView(text("API simSlot overrides this setting",13,muted).apply { gravity=Gravity.CENTER }); return box
    }

    private fun sims(): List<SubscriptionInfo> = try { if(ActivityCompat.checkSelfPermission(this,Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED) getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList ?: emptyList() else emptyList() } catch(_:Exception){ emptyList() }
    private fun startGateway() { if(store.paired && store.gatewayEnabled) ContextCompat.startForegroundService(this,Intent(this,PollService::class.java)) }
    private fun stopGateway() = stopService(Intent(this,PollService::class.java))
    private fun openUrl(url:String)=startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    private fun maskKey(key:String?) = key?.let { it.take(8)+"••••••••" } ?: "—"
    private fun editName(){ val input=edit("Device name",InputType.TYPE_CLASS_TEXT).apply { setText(store.deviceName) }; android.app.AlertDialog.Builder(this).setTitle("Device Name").setView(input).setPositiveButton("Save") { _,_-> store.deviceName=input.text.toString().trim().ifBlank { Build.MODEL }; render() }.setNegativeButton("Cancel",null).show() }
    private fun chooseDelay(){ val options=arrayOf("0 seconds","2 seconds","5 seconds","10 seconds","30 seconds"); val values=intArrayOf(0,2,5,10,30); android.app.AlertDialog.Builder(this).setTitle("Send Delay").setItems(options){_,which->store.sendDelaySeconds=values[which];render()}.show() }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun column(spacing:Int=0)=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; if(spacing>0) setPadding(dp(16),dp(spacing),dp(16),0) }
    private fun row()=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    private fun title(s:String,size:Int)=text(s,size,white).apply { setTypeface(null,Typeface.BOLD) }
    private fun text(s:String,size:Int,color:Int)=TextView(this).apply { text=s; textSize=size.toFloat(); setTextColor(color); letterSpacing=0f; setLineSpacing(0f,1.12f) }
    private fun edit(hint:String,type:Int)=EditText(this).apply { this.hint=hint; inputType=type; setTextColor(white); setHintTextColor(muted); background=getDrawable(R.drawable.field); setPadding(dp(14),0,dp(14),0) }
    private fun action(label:String)=TextView(this).apply { text=label; gravity=Gravity.CENTER; textSize=16f; setTextColor(white); setTypeface(null,Typeface.BOLD); background=getDrawable(R.drawable.pill_active) }
    private fun outline(label:String,click:()->Unit)=TextView(this).apply { text=label; gravity=Gravity.CENTER; textSize=15f; setTextColor(orange); setTypeface(null,Typeface.BOLD); background=getDrawable(R.drawable.field); setOnClickListener{click()} }
    private fun switch(label:String,checked:Boolean,onChange:(Boolean)->Unit)=Switch(this).apply { text=label; textSize=18f; setTextColor(white); isChecked=checked; setPadding(dp(4),0,dp(4),0); setOnCheckedChangeListener{_,v->onChange(v)} }
    private fun section(label:String)=text(label,14,orange).apply { gravity=Gravity.END; setTypeface(null,Typeface.BOLD); setPadding(dp(18),dp(22),dp(18),dp(4)) }
    private fun setting(label:String,value:String)=column(2).apply { setPadding(dp(20),dp(13),dp(20),dp(13)); addView(title(label,19).apply { gravity=Gravity.END }); addView(text(value,14,muted).apply { gravity=Gravity.END; maxLines=1 }) }
    private fun copySetting(label:String,display:String,value:String?)=column(2).apply {
        setPadding(dp(20),dp(13),dp(20),dp(13))
        val head=row()
        val btn=text("\u29C9 Copy",14,orange).apply { setTypeface(null,Typeface.BOLD); setPadding(dp(10),dp(6),dp(10),dp(6)); background=getDrawable(R.drawable.field); setOnClickListener { copyValue(label,value) } }
        head.addView(btn,LinearLayout.LayoutParams(-2,-2))
        head.addView(title(label,19).apply { gravity=Gravity.END },LinearLayout.LayoutParams(0,-2,1f))
        addView(head,LinearLayout.LayoutParams(-1,-2))
        addView(text(display,14,muted).apply { gravity=Gravity.END; maxLines=2 })
        setOnLongClickListener { copyValue(label,value); true }
    }
    private fun copyValue(label:String,value:String?) {
        if(value.isNullOrBlank()) { toast("Nothing to copy yet"); return }
        val clipboard=getSystemService(android.content.ClipboardManager::class.java)
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(label,value))
        toast("$label copied")
    }
    private fun space(h:Int)=Space(this).apply { minimumHeight=dp(h) }
    private fun match(height:Int,top:Int=0,bottom:Int=0)=LinearLayout.LayoutParams(-1,if(height<0)-2 else dp(height)).apply { topMargin=dp(top); bottomMargin=dp(bottom) }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
