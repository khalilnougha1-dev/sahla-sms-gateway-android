package dev.sahla.gateway

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.sahla.gateway.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

/** Pairing screen plus a live view of the messages this phone has sent. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: Store
    private val log = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = Store(this)

        binding.baseUrl.setText(store.baseUrl)
        render()
        requestPermissions()

        binding.pairButton.setOnClickListener { pair() }
        binding.startButton.setOnClickListener {
            startForegroundService(Intent(this, PollService::class.java))
            append("Gateway service started — polling for messages.")
        }
        binding.stopButton.setOnClickListener {
            stopService(Intent(this, PollService::class.java))
            append("Gateway service stopped.")
        }
        binding.unpairButton.setOnClickListener {
            stopService(Intent(this, PollService::class.java))
            store.clear()
            append("Device unpaired.")
            render()
        }
    }

    private fun requestPermissions() {
        val needed = mutableListOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
    }

    private fun simCount(): Int = try {
        getSystemService(SubscriptionManager::class.java)
            ?.activeSubscriptionInfoList?.size ?: 1
    } catch (_: SecurityException) {
        1
    }

    private fun pair() {
        val code = binding.pairingCode.text.toString().trim()
        if (code.isEmpty()) {
            append("Enter the pairing code shown in the dashboard.")
            return
        }
        store.baseUrl = binding.baseUrl.text.toString().trim().ifEmpty { Store.DEFAULT_BASE }
        binding.pairButton.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                Api.pair(
                    base = store.baseUrl,
                    code = code,
                    model = "${Build.MANUFACTURER} ${Build.MODEL}",
                    androidVersion = Build.VERSION.RELEASE ?: "unknown",
                    simCount = simCount(),
                )
            }
            binding.pairButton.isEnabled = true
            result
                .onSuccess { (deviceId, apiKey) ->
                    store.deviceId = deviceId
                    store.apiKey = apiKey
                    append("Paired successfully. Device $deviceId")
                    render()
                    startForegroundService(Intent(this@MainActivity, PollService::class.java))
                }
                .onFailure { append(it.message ?: "Pairing failed") }
        }
    }

    private fun render() {
        val paired = store.paired
        binding.status.text = if (paired) "Paired — device ${store.deviceId}" else "Not paired"
        binding.pairingCode.isEnabled = !paired
        binding.pairButton.isEnabled = !paired
        binding.startButton.isEnabled = paired
        binding.stopButton.isEnabled = paired
        binding.unpairButton.isEnabled = paired
    }

    private fun append(line: String) {
        log.insert(0, line + "\n")
        binding.log.text = log.toString()
    }
}
