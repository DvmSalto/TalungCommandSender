package com.example.talungcommandsender

import android.bluetooth.BluetoothDevice
import android.content.Context
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.BleManagerCallbacks
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.callback.DataSentCallback
import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID

class NusBleManager(context: Context) : BleManager<NusBleManagerCallbacks>(context) {
    companion object {
        val NUS_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val NUS_RX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val NUS_TX_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    }

    private var nusRx: BluetoothGattCharacteristic? = null
    private var nusTx: BluetoothGattCharacteristic? = null

    override fun getGattCallback(): BleManagerGattCallback {
        return object : BleManagerGattCallback() {
            override fun isRequiredServiceSupported(gatt: android.bluetooth.BluetoothGatt): Boolean {
                val service = gatt.getService(NUS_SERVICE_UUID)
                nusRx = service?.getCharacteristic(NUS_RX_UUID)
                nusTx = service?.getCharacteristic(NUS_TX_UUID)
                return nusRx != null && nusTx != null
            }

            override fun initialize() {
                setNotificationCallback(nusTx).with { device, data ->
                    callbacks?.onDataReceived(device, data)
                }
                enableNotifications(nusTx).enqueue()
            }

            override fun onServicesInvalidated() {
                nusRx = null
                nusTx = null
            }
        }
    }

    fun send(data: ByteArray) {
        nusRx?.let {
            writeCharacteristic(it, data)
                .split()
                .enqueue()
        }
    }
}

interface NusBleManagerCallbacks : BleManagerCallbacks {
    fun onDataReceived(device: BluetoothDevice, data: Data)
}
