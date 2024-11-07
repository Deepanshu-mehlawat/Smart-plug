package com.example.smartcontroller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var buttonOn: Button
    private lateinit var buttonOff: Button
    private lateinit var buttonSetTimer: Button
    private lateinit var timerDurationInput: EditText
    private lateinit var batteryLevelText: TextView
    private lateinit var batteryThresholdInput: EditText
    private lateinit var setBatteryThresholdButton: Button

    private lateinit var mqttClient: MqttClient
    private val brokerUrl = "tcp://broker.hivemq.com:1883" // Replace with your MQTT broker URL
    private var batteryThreshold = 100 // Default battery threshold

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupMqttClient()
        setupButtonListeners()

        // Register receiver to monitor battery level
        registerReceiver(batteryLevelReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.status_text)
        buttonOn = findViewById(R.id.button_on)
        buttonOff = findViewById(R.id.button_off)
        buttonSetTimer = findViewById(R.id.button_set_timer)
        timerDurationInput = findViewById(R.id.timer_duration_input)
        batteryLevelText = findViewById(R.id.battery_level_text)
        batteryThresholdInput = findViewById(R.id.battery_threshold_input)
        setBatteryThresholdButton = findViewById(R.id.set_battery_threshold_button)
    }

    private fun setupMqttClient() {
        coroutineScope.launch {
            try {
                val persistence = MemoryPersistence()
                mqttClient = MqttClient(brokerUrl, MqttClient.generateClientId(), persistence)
                val options = MqttConnectOptions()
                options.isCleanSession = true
                mqttClient.connect(options)

                withContext(Dispatchers.Main) {
                    statusText.text = "MQTT Connected"
                }

                mqttClient.subscribe("deepanshu_esp32/relay/control") { _, message ->
                    val messageText = String(message.payload)
                    coroutineScope.launch(Dispatchers.Main) {
                        statusText.text = "Message received: $messageText"
                    }
                }
            } catch (e: MqttException) {
                withContext(Dispatchers.Main) {
                    statusText.text = "MQTT Connection error: ${e.message}"
                    Toast.makeText(this@MainActivity, "Failed to connect to MQTT broker", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupButtonListeners() {
        buttonOn.setOnClickListener {
            sendMqttMessage("deepanshu_esp32/relay/control", "TURN_ON")
        }

        buttonOff.setOnClickListener {
            sendMqttMessage("deepanshu_esp32/relay/control", "TURN_OFF")
        }

        buttonSetTimer.setOnClickListener {
            val duration = timerDurationInput.text.toString()
            if (duration.isNotEmpty()) {
                sendMqttMessage("deepanshu_esp32/relay/control", "TIMER:$duration")
            } else {
                statusText.text = "Please enter a timer duration."
            }
        }

        setBatteryThresholdButton.setOnClickListener {
            val threshold = batteryThresholdInput.text.toString().toIntOrNull()
            if (threshold != null && threshold in 1..100) {
                batteryThreshold = threshold
                statusText.text = "Battery threshold set to $batteryThreshold%"
            } else {
                statusText.text = "Invalid threshold. Enter a number between 1 and 100."
            }
        }
    }

    private fun sendMqttMessage(topic: String, message: String) {
        coroutineScope.launch {
            try {
                mqttClient.publish(topic, MqttMessage(message.toByteArray()))
                withContext(Dispatchers.Main) {
                    statusText.text = "Message sent: $message"
                }
            } catch (e: MqttException) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Error sending message: ${e.message}"
                    Toast.makeText(this@MainActivity, "Failed to send MQTT message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val batteryLevelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = level * 100 / scale.toFloat()

            batteryLevelText.text = "Battery Level: $batteryPct%"

            if (batteryPct >= batteryThreshold) {
                sendMqttMessage("deepanshu_esp32/relay/control", "TURN_OFF")
                statusText.text = "Battery threshold reached. Sending OFF signal."
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        try {
            unregisterReceiver(batteryLevelReceiver)
            mqttClient.disconnect()
        } catch (e: Exception) {
            // Log the error or handle it as needed
        }
    }
}