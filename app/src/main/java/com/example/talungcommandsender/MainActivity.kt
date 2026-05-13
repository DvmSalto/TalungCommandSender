package com.example.talungcommandsender


import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import android.widget.TextView
import android.widget.ScrollView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.bluetooth.BluetoothDevice
import com.example.talungcommandsender.NusBleManager
// Removed: NusBleManagerCallbacks does not exist
import no.nordicsemi.android.ble.data.Data


class MainActivity : Activity() {
        private lateinit var logTextView: TextView
        private lateinit var logScrollView: ScrollView
        private val logBuffer = StringBuilder()

        private fun appendLog(message: String) {
            logBuffer.append("\n").append(message)
            if (::logTextView.isInitialized) {
                logTextView.text = logBuffer.toString()
                logScrollView.post { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }
    private val PERMISSION_REQUEST_CODE = 1001
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var nusBleManager: NusBleManager
    private var connectedDevice: BluetoothDevice? = null
    private var isConnected = false


    override fun onCreate(savedInstanceState: Bundle?) {
        nusBleManager = NusBleManager(this)
        // Log all GATT services and characteristics after connection
        nusBleManager.setOnDataReceivedListener { device, data ->
            val hex = data.value?.joinToString(" ") { String.format("%02X", it) } ?: ""
            android.util.Log.d("MainActivity", "Received: $hex")
            runOnUiThread { appendLog("Received: $hex") }
        }

        // Connect to the first bonded device (if any) and log services
        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        val bondedDevices = bluetoothAdapter?.bondedDevices
        val device = bondedDevices?.firstOrNull()
        if (device != null) {
            appendLog("Connecting to: ${device.name} (${device.address})")
            android.util.Log.d("MainActivity", "Connecting to: ${device.name} (${device.address})")
            nusBleManager.connect(device)
                .retry(3, 100)
                .useAutoConnect(false)
                .done {
                    runOnUiThread {
                        appendLog("Connected to BLE device!")
                        android.util.Log.d("MainActivity", "Connected to BLE device!")
                        // Log all GATT services after connection
                        val gatt = nusBleManager.bluetoothGatt
                        if (gatt != null) {
                            nusBleManager.logAllServices(gatt) { msg ->
                                android.util.Log.d("MainActivity", msg)
                                runOnUiThread { appendLog(msg) }
                            }
                        } else {
                            appendLog("BluetoothGatt not available for service logging.")
                            android.util.Log.e("MainActivity", "BluetoothGatt not available for service logging.")
                        }
                    }
                }
                .fail { _, status ->
                    runOnUiThread { appendLog("BLE connection failed: $status") }
                    android.util.Log.e("MainActivity", "BLE connection failed: $status")
                }
                .enqueue()
        } else {
            appendLog("No bonded device found.")
            android.util.Log.e("MainActivity", "No bonded device found.")
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        nusBleManager = NusBleManager(this)
        logTextView = findViewById(R.id.logTextView)
        logScrollView = findViewById(R.id.logScrollView)
        appendLog("App started. Waiting for user action...")

        // Request BLE and location permissions if needed
        if (!hasPermissions()) {
            requestPermissions()
        }
        val commandEditText = findViewById<android.widget.EditText>(R.id.commandEditText)
        val dataEditText = findViewById<android.widget.EditText>(R.id.dataEditText)
        val sendButton = findViewById<Button>(R.id.sendButton)
        sendButton.isEnabled = true

        // Optionally, after connecting, log all GATT services of the connected device
        // This requires a connection and access to the BluetoothGatt object
        // You can call this after a successful connection if you have access to the gatt object

        sendButton.setOnClickListener {
            if (!hasPermissions()) {
                requestPermissions()
                Toast.makeText(this, "Grant permissions first", Toast.LENGTH_SHORT).show()
                appendLog("Permission denied or not granted yet.")
                return@setOnClickListener
            }
            val commandStr = commandEditText.text.toString().trim()
            val dataStr = dataEditText.text.toString().trim()
            if (commandStr.isEmpty()) {
                Toast.makeText(this, "Enter a command number", Toast.LENGTH_SHORT).show()
                appendLog("No command entered.")
                return@setOnClickListener
            }
            val commandNum = try { commandStr.toInt() } catch (e: Exception) {
                Toast.makeText(this, "Invalid command number", Toast.LENGTH_SHORT).show()
                appendLog("Invalid command number: $commandStr")
                return@setOnClickListener
            }
            val dataBytes = if (dataStr.isEmpty()) byteArrayOf() else try {
                dataStr.split(" ", ",", ";", "\t", "\n").filter { it.isNotBlank() }
                    .map { it.trim().removePrefix("0x").toInt(16).toByte() }
                    .toByteArray()
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid data bytes", Toast.LENGTH_SHORT).show()
                appendLog("Invalid data bytes: $dataStr")
                return@setOnClickListener
            }
            val frame = makeDataFrame(commandNum, 0, dataBytes)
            val hexString = frame.joinToString(" ") { String.format("%02X", it) }
            appendLog("Sending: $hexString")
            nusBleManager.send(frame) { msg ->
                android.util.Log.d("MainActivity", msg)
                runOnUiThread { appendLog(msg) }
            }
            Toast.makeText(this, "Command sent over BLE", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        return permissions
    }

    private fun hasPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = getRequiredPermissions()
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToRequest.isEmpty()) return

        var shouldShowRationale = false
        permissionsToRequest.forEach {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, it)) {
                shouldShowRationale = true
            }
        }
        if (shouldShowRationale) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Permission required")
                .setMessage("Bluetooth and location permissions are required for BLE communication. Please grant them.")
                .setPositiveButton("OK") { _, _ ->
                    ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val denied = mutableListOf<String>()
            val permanentlyDenied = mutableListOf<String>()
            permissions.forEachIndexed { index, perm ->
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    denied.add(perm)
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
                        permanentlyDenied.add(perm)
                    }
                }
            }
            if (denied.isEmpty()) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else if (permanentlyDenied.isNotEmpty()) {
                val msg = "Permanently denied: ${permanentlyDenied.joinToString()}"
                android.app.AlertDialog.Builder(this)
                    .setTitle("Permission denied")
                    .setMessage("Some permissions are permanently denied. Please enable them in app settings.\n$msg")
                    .setPositiveButton("Settings") { _, _ ->
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = android.net.Uri.fromParts("package", packageName, null)
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                val msg = "Denied: ${denied.joinToString()}"
                Toast.makeText(this, "Permissions denied. $msg", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendChameleonCommand(command: Int, payload: ByteArray) {
        val frameBytes = makeDataFrame(command, 0, payload)
        val hexString = frameBytes.joinToString(" ") { String.format("%02X", it) }
        appendLog("Sending: $hexString")
        nusBleManager.send(frameBytes)
        Toast.makeText(this, "Command sent over BLE", Toast.LENGTH_SHORT).show()
        // The BLE manager's onDataReceived callback will log the response
    }

}
