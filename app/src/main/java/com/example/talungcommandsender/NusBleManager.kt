package com.example.talungcommandsender

import android.util.Log

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.data.Data
import java.util.UUID

class NusBleManager(context: Context) : BleManager(context) {
        private val TAG = "NusBleManager"
    private var lastBluetoothGatt: BluetoothGatt? = null
    val bluetoothGatt: BluetoothGatt?
        get() = lastBluetoothGatt

    fun logAllServices(gatt: BluetoothGatt, log: (String) -> Unit) {
            log("Discovered GATT Services:")
            Log.d(TAG, "Discovered GATT Services:")
            for (service in gatt.services) {
                log("Service: ${service.uuid}")
                Log.d(TAG, "Service: ${service.uuid}")
                for (characteristic in service.characteristics) {
                    log("  Characteristic: ${characteristic.uuid}")
                    Log.d(TAG, "  Characteristic: ${characteristic.uuid}")
                }
            }
                override fun onDeviceReady() {
                    // Request MTU and Data Length when device is ready
                    Log.d(TAG, "onDeviceReady: requesting MTU 247 and data length 251")
                    requestMtu(247).enqueue()
                    requestConnectionPriority(CONNECTION_PRIORITY_HIGH).enqueue()
                    // If requestDataLength is not available in your library version, comment out the next line
                    // requestDataLength(251).enqueue()
                }
        }
            override fun onDeviceReady() {
                // Request MTU and Data Length when device is ready
                Log.d(TAG, "onDeviceReady: requesting MTU 247 and data length 251")
                requestMtu(247).enqueue()
                requestConnectionPriority(CONNECTION_PRIORITY_HIGH).enqueue()
                requestDataLength(251).enqueue()
            }
    companion object {
        val NUS_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val NUS_RX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val NUS_TX_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    }

    private var nusRx: BluetoothGattCharacteristic? = null
    private var nusTx: BluetoothGattCharacteristic? = null
    private var onDataReceived: ((BluetoothDevice, Data) -> Unit)? = null

    fun setOnDataReceivedListener(listener: (BluetoothDevice, Data) -> Unit) {
        onDataReceived = listener
    }

    override fun getGattCallback(): BleManagerGattCallback = NusBleManagerGattCallback()

    private inner class NusBleManagerGattCallback : BleManagerGattCallback() {
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            Log.d(TAG, "isRequiredServiceSupported: called")
            logAllServices(gatt) { Log.d(TAG, it) }
            lastBluetoothGatt = gatt
            val service = gatt.getService(NUS_SERVICE_UUID)
            nusRx = service?.getCharacteristic(NUS_RX_UUID)
            nusTx = service?.getCharacteristic(NUS_TX_UUID)
            Log.d(TAG, "NUS RX found: ${nusRx != null}, NUS TX found: ${nusTx != null}")
            return nusRx != null && nusTx != null
        }

        override fun initialize() {
            setNotificationCallback(nusTx).with(DataReceivedCallback { device, data ->
                onDataReceived?.invoke(device, data)
            })
            enableNotifications(nusTx).enqueue()
        }

        override fun onServicesInvalidated() {
            nusRx = null
            nusTx = null
            lastBluetoothGatt = null
        }
    }

    fun send(data: ByteArray, log: ((String) -> Unit)? = null) {
        nusRx?.let { characteristic ->
            val props = characteristic.properties
            val canWrite = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
            val canWriteNoResp = (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            if (!canWrite && !canWriteNoResp) {
                log?.invoke("NUS RX characteristic does not support write operations!")
                Log.e(TAG, "NUS RX characteristic does not support write operations!")
                return
            }
            Log.d(TAG, "Writing to NUS RX characteristic: ${characteristic.uuid}, data: ${data.joinToString { String.format("%02X", it) }}")
            writeCharacteristic(characteristic, data)
                .split()
                .done {
                    log?.invoke("Write to NUS RX successful.")
                    Log.d(TAG, "Write to NUS RX successful.")
                }
                .fail { device, status ->
                    log?.invoke("Write to NUS RX failed: $status")
                    Log.e(TAG, "Write to NUS RX failed: $status")
                }
                .enqueue()
        } ?: run {
            log?.invoke("NUS RX characteristic not found!")
            Log.e(TAG, "NUS RX characteristic not found!")
        }
    }
}
