package com.example.talungcommandsender

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.data.Data
import java.util.UUID

class NusBleManager(context: Context) : BleManager(context) {

        fun logAllServices(gatt: BluetoothGatt, log: (String) -> Unit) {
            log("Discovered GATT Services:")
            for (service in gatt.services) {
                log("Service: ${service.uuid}")
                for (characteristic in service.characteristics) {
                    log("  Characteristic: ${characteristic.uuid}")
                }
            }
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
            val service = gatt.getService(NUS_SERVICE_UUID)
            nusRx = service?.getCharacteristic(NUS_RX_UUID)
            nusTx = service?.getCharacteristic(NUS_TX_UUID)
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
        }
    }

    fun send(data: ByteArray, log: ((String) -> Unit)? = null) {
        nusRx?.let { characteristic ->
            val props = characteristic.properties
            val canWrite = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
            val canWriteNoResp = (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            if (!canWrite && !canWriteNoResp) {
                log?.invoke("NUS RX characteristic does not support write operations!")
                return
            }
            writeCharacteristic(characteristic, data)
                .split()
                .done { log?.invoke("Write to NUS RX successful.") }
                .fail { device, status -> log?.invoke("Write to NUS RX failed: $status") }
                .enqueue()
        } ?: log?.invoke("NUS RX characteristic not found!")
    }
}
