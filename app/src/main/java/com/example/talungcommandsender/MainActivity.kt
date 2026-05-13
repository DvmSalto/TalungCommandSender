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
import com.example.talungcommandsender.BleNusManager
import com.example.talungcommandsender.DataFrame

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
    private lateinit var bleNusManager: BleNusManager
    private var connectedDevice: BluetoothDevice? = null
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        bleNusManager = BleNusManager(this)
        logTextView = findViewById(R.id.logTextView)
        logScrollView = findViewById(R.id.logScrollView)
        appendLog("App started. Waiting for user action...")

        // Request BLE and location permissions if needed
        if (!hasPermissions()) {
            requestPermissions()
        }
        val commands = listOf("Command 1", "Command 2", "Command 3")
        val commandListView = findViewById<ListView>(R.id.commandListView)
        val sendButton = findViewById<Button>(R.id.sendButton)
        var selectedCommand: String? = null

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, commands)
        commandListView.adapter = adapter
        commandListView.choiceMode = ListView.CHOICE_MODE_SINGLE

        commandListView.setOnItemClickListener { _, _, position, _ ->
            selectedCommand = commands[position]
            appendLog("Selected command: ${commands[position]}")
        }

        sendButton.setOnClickListener {
            if (!hasPermissions()) {
                requestPermissions()
                Toast.makeText(this, "Grant permissions first", Toast.LENGTH_SHORT).show()
                appendLog("Permission denied or not granted yet.")
                return@setOnClickListener
            }
            if (selectedCommand != null) {
                if (!isConnected) {
                    appendLog("Scanning for BLE device...")
                    bleNusManager.startScan("NUS_Device") { device ->
                        connectedDevice = device
                        appendLog("BLE device found: ${device.name ?: device.address}")
                        bleNusManager.connect(device, {
                            isConnected = true
                            Toast.makeText(this, "Connected to BLE device", Toast.LENGTH_SHORT).show()
                            appendLog("Connected to BLE device: ${device.name ?: device.address}")
                            sendSelectedCommand(selectedCommand!!)
                        }, { data ->
                            val msg = "Received: ${data.joinToString()}"
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                            appendLog(msg)
                        })
                    }
                } else {
                    sendSelectedCommand(selectedCommand!!)
                }
            } else {
                Toast.makeText(this, "Select a command first", Toast.LENGTH_SHORT).show()
                appendLog("No command selected.")
            }
        }
    }

    private fun hasPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
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
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                // Check if any permission is permanently denied
                var permanentlyDenied = false
                permissions.forEachIndexed { index, perm ->
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
                        permanentlyDenied = true
                    }
                }
                if (permanentlyDenied) {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Permission denied")
                        .setMessage("Some permissions are permanently denied. Please enable them in app settings.")
                        .setPositiveButton("Settings") { _, _ ->
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = android.net.Uri.fromParts("package", packageName, null)
                            startActivity(intent)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendSelectedCommand(command: String) {
        // Example: map command string to byte and payload
        val commandByte: Byte = when (command) {
            "Command 1" -> 0x01
            "Command 2" -> 0x02
            "Command 3" -> 0x03
            else -> 0x00
        }
        val payload = byteArrayOf() // Add payload if needed
        val frame = DataFrame(commandByte, payload)
        val frameBytes = DataFrame.toBytes(frame)
        bleNusManager.send(frameBytes)
        val logMsg = "Command sent over BLE: $command (0x%02X)".format(commandByte)
        Toast.makeText(this, "Command sent over BLE", Toast.LENGTH_SHORT).show()
        appendLog(logMsg)
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.let { adapter ->
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
            val filters = arrayOf<IntentFilter>()
            adapter.enableForegroundDispatch(this, pendingIntent, filters, null)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    // Handles NFC tag discovery for OOB BLE pairing
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.action
        appendLog("NFC intent received: $action")
        if (action == NfcAdapter.ACTION_TAG_DISCOVERED || action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            try {
                val ndefMessage: NdefMessage? = when {
                    intent.hasExtra(NfcAdapter.EXTRA_NDEF_MESSAGES) -> {
                        val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                        if (rawMsgs != null && rawMsgs.isNotEmpty()) rawMsgs[0] as? NdefMessage else null
                    }
                    else -> {
                        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                        if (tag != null) {
                            val ndef = Ndef.get(tag)
                            ndef?.connect()
                            val msg = ndef?.ndefMessage
                            ndef?.close()
                            msg
                        } else null
                    }
                }
                if (ndefMessage != null) {
                    for (record in ndefMessage.records) {
                        val payload = record.payload
                        val mac = extractMacFromPayload(payload)
                        if (mac != null) {
                            appendLog("Extracted BLE MAC from NFC: $mac")
                            connectToBleDevice(mac)
                            return
                        }
                    }
                    appendLog("No valid BLE MAC found in NFC tag payload.")
                } else {
                    appendLog("No NDEF message found on NFC tag.")
                }
            } catch (e: Exception) {
                appendLog("Error handling NFC intent: ${e.message}")
            }
        } else {
            appendLog("NFC intent not handled: $action")
        }
    }

    // Helper: Try to extract MAC address from NFC payload (text or bytes)
    private fun extractMacFromPayload(payload: ByteArray): String? {
        // Try to parse as text (skip language code if present)
        return try {
            val text = if (payload.size > 1 && payload[0].toInt() < payload.size) {
                String(payload.copyOfRange(1 + payload[0], payload.size), Charsets.UTF_8)
            } else {
                String(payload, Charsets.UTF_8)
            }
            val macRegex = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
            macRegex.find(text)?.value
                ?: payloadToMac(payload)
        } catch (e: Exception) {
            payloadToMac(payload)
        }
    }

    // Helper: Try to parse MAC from raw bytes
    private fun payloadToMac(payload: ByteArray): String? {
        if (payload.size >= 6) {
            val mac = payload.takeLast(6).joinToString(":") { String.format("%02X", it) }
            if (mac.matches(Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}"))) return mac
        }
        return null
    }

    // Helper: Connect to BLE device by MAC address
    private fun connectToBleDevice(mac: String) {
        appendLog("Attempting BLE connection to $mac from NFC tag...")
        val device = (getSystemService(BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager)
            .adapter.getRemoteDevice(mac)
        bleNusManager.connect(device, {
            isConnected = true
            Toast.makeText(this, "Connected to BLE device (NFC)", Toast.LENGTH_SHORT).show()
            appendLog("Connected to BLE device (NFC): $mac")
        }, { data ->
            val msg = "Received: ${data.joinToString()}"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            appendLog(msg)
        })
    }
}
