package com.example.talungcommandsender

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID
import android.util.Log

class BleNusManager(private val context: Context) {
    private val NUS_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val NUS_RX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    private val NUS_TX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var nusTxCharacteristic: BluetoothGattCharacteristic? = null
    private var nusRxCharacteristic: BluetoothGattCharacteristic? = null
    private var scanCallback: ScanCallback? = null
    private var onConnected: (() -> Unit)? = null
    private var onDataReceived: ((ByteArray) -> Unit)? = null

    fun startScan(deviceName: String, onDeviceFound: (BluetoothDevice) -> Unit) {
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.device.name == deviceName) {
                    bluetoothAdapter.bluetoothLeScanner.stopScan(this)
                    onDeviceFound(result.device)
                }
            }
        }
        bluetoothAdapter.bluetoothLeScanner.startScan(scanCallback)
        Handler(Looper.getMainLooper()).postDelayed({
            bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
        }, 10000)
    }

    fun connect(device: BluetoothDevice, onConnected: () -> Unit, onDataReceived: (ByteArray) -> Unit) {
        this.onConnected = onConnected
        this.onDataReceived = onDataReceived
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun send(data: ByteArray) {
        if (nusRxCharacteristic == null || bluetoothGatt == null) {
            Log.e("BleNusManager", "Cannot send: RX characteristic or GATT is null")
            return
        }
        nusRxCharacteristic?.let {
            it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            it.value = data
            val result = bluetoothGatt?.writeCharacteristic(it)
            Log.i("BleNusManager", "writeCharacteristic result: $result, data: ${data.joinToString(" ") { String.format("%02X", it) }}")
        }
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i("BleNusManager", "onConnectionStateChange: status=$status, newState=$newState")
            if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.i("BleNusManager", "onServicesDiscovered: status=$status")
            val nusService: BluetoothGattService? = gatt.getService(NUS_SERVICE_UUID)
            nusRxCharacteristic = nusService?.getCharacteristic(NUS_RX_UUID)
            nusTxCharacteristic = nusService?.getCharacteristic(NUS_TX_UUID)
            Log.i("BleNusManager", "nusRxCharacteristic: $nusRxCharacteristic, nusTxCharacteristic: $nusTxCharacteristic")
            nusTxCharacteristic?.let {
                gatt.setCharacteristicNotification(it, true)
            }
            Handler(Looper.getMainLooper()).post {
                onConnected?.invoke()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.i("BleNusManager", "onCharacteristicChanged: uuid=${characteristic.uuid}, value=${characteristic.value?.joinToString(" ") { String.format("%02X", it) }}")
            if (characteristic.uuid == NUS_TX_UUID) {
                onDataReceived?.invoke(characteristic.value)
            }
        }
    }
}
