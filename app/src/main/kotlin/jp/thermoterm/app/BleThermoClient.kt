/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import jp.thermoterm.core.THERMO_SERVICE_UUID
import jp.thermoterm.core.THERMO_TEMPERATURE_UUID
import jp.thermoterm.core.ThermoReading
import jp.thermoterm.core.parseThermoReading
import java.util.UUID

/** ノードを探す時間 (ミリ秒). これを過ぎたら、見つからなかったとする. */
private const val SCAN_TIMEOUT_MILLIS = 15_000L

/** 通知の設定 (CCC) の UUID. */
private val CCC_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/** BLE の接続の状態. */
enum class BleState {
    /** 何もしていない. */
    IDLE,

    /** Bluetooth が切れている. */
    BLUETOOTH_OFF,

    /** ノードを探している. */
    SCANNING,

    /** 探した時間内に、ノードが見つからなかった. */
    NOT_FOUND,

    /** 見つけたノードにつないでいる. */
    CONNECTING,

    /** つながって、通知を受け取っている. */
    CONNECTED,

    /** つながったあとで、切れた. */
    DISCONNECTED,

    /** ノードのサービスが見つからないなど、通信に失敗した. */
    FAILED,
}

/**
 * BLE の中央機器 (Central) として、Thermo のノードを探して、温度と湿度の通知を受け取る.
 *
 * ノードを探し (Thermo サービスの UUID)、最初に見つかったノードにつなぎ、温度の特性の通知を
 * 購読する. ノードは 5 秒ごとに通知する. 呼び出す前に、Bluetooth の権限
 * (BLUETOOTH_SCAN と BLUETOOTH_CONNECT) を得ておくこと. 結果は、画面のスレッドで通知する.
 *
 * @property context コンテキスト
 * @property onState 状態が変わったときの処理
 * @property onReading 温度と湿度を受け取ったときの処理
 */
@SuppressLint("MissingPermission")
class BleThermoClient(
    private val context: Context,
    private val onState: (BleState) -> Unit,
    private val onReading: (ThermoReading) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private val timeout = Runnable { stopScan(BleState.NOT_FOUND) }

    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) {
                stopScan(BleState.CONNECTING)
                connect(result.device)
            }

            override fun onScanFailed(errorCode: Int) = stopScan(BleState.FAILED)
        }

    private val gattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                } else {
                    close(
                        if (status ==
                            BluetoothGatt.GATT_SUCCESS
                        ) {
                            BleState.IDLE
                        } else {
                            BleState.DISCONNECTED
                        },
                    )
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                val characteristic =
                    gatt
                        .getService(UUID.fromString(THERMO_SERVICE_UUID))
                        ?.getCharacteristic(UUID.fromString(THERMO_TEMPERATURE_UUID))
                if (characteristic == null || !subscribe(gatt, characteristic)) {
                    close(BleState.FAILED)
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    post { onState(BleState.CONNECTED) }
                } else {
                    close(BleState.FAILED)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                parseThermoReading(value)?.let { reading -> post { onReading(reading) } }
            }
        }

    /** ノードを探して、見つかったらつなぐ. すでに動いているときは、何もしない. */
    fun start() {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            onState(BleState.BLUETOOTH_OFF)
        } else if (!scanning && gatt == null) {
            val filter =
                ScanFilter
                    .Builder()
                    .setServiceUuid(ParcelUuid(UUID.fromString(THERMO_SERVICE_UUID)))
                    .build()
            val settings =
                ScanSettings
                    .Builder()
                    .setScanMode(
                        ScanSettings.SCAN_MODE_LOW_LATENCY,
                    ).build()
            scanning = true
            scanner.startScan(listOf(filter), settings, scanCallback)
            handler.postDelayed(timeout, SCAN_TIMEOUT_MILLIS)
            onState(BleState.SCANNING)
        }
    }

    /** 探すのも、つなぐのも、やめる. */
    fun stop() {
        if (scanning) {
            stopScan(BleState.IDLE)
        } else if (gatt != null) {
            close(BleState.IDLE)
        } else {
            onState(BleState.IDLE)
        }
    }

    /**
     * 探すのをやめて、状態を通知する.
     *
     * @param state 通知する状態
     */
    private fun stopScan(state: BleState) {
        handler.removeCallbacks(timeout)
        if (scanning) {
            scanning = false
            context
                .getSystemService(BluetoothManager::class.java)
                ?.adapter
                ?.bluetoothLeScanner
                ?.stopScan(scanCallback)
        }
        onState(state)
    }

    /**
     * 見つけたノードにつなぐ.
     *
     * @param device ノード
     */
    @Suppress("DEPRECATION") // 新しい API は、minSdk (33) より新しい版にしかない
    private fun connect(device: BluetoothDevice) {
        gatt =
            device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE,
                BluetoothDevice.PHY_LE_1M_MASK,
                handler,
            )
    }

    /**
     * 温度の特性の通知を購読する (通知を受け取る設定と、ノードの CCC への書き込み).
     *
     * @param gatt 接続
     * @param characteristic 温度の特性
     * @return 書き込みを始められれば true
     */
    private fun subscribe(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ): Boolean {
        val ccc = characteristic.getDescriptor(CCC_UUID)
        return ccc != null &&
            gatt.setCharacteristicNotification(characteristic, true) &&
            gatt.writeDescriptor(ccc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
            BluetoothStatusCodes.SUCCESS
    }

    /**
     * 接続を閉じて、状態を通知する.
     *
     * @param state 通知する状態
     */
    private fun close(state: BleState) {
        gatt?.close()
        gatt = null
        post { onState(state) }
    }

    /**
     * 画面のスレッドで実行する (GATT のコールバックは、別のスレッドで呼ばれる).
     *
     * @param action 実行する処理
     */
    private fun post(action: () -> Unit) {
        handler.post(action)
    }
}
