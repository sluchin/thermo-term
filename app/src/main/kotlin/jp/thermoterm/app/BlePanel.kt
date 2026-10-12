/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import jp.thermoterm.core.ThermoReading
import jp.thermoterm.core.formatHumidity
import jp.thermoterm.core.formatTemperature

/** 温度と湿度の文字の大きさ (sp). */
private const val READING_TEXT_SIZE_SP = 32f

/** Bluetooth の権限を求めるときの、要求の番号. */
private const val REQUEST_BLUETOOTH = 1

/**
 * Bluetooth でノードにつないで、温度と湿度を表示する部分 (「BLE」のタブ).
 *
 * 「Connect」を押すと、Thermo のノードを探してつなぎ、ノードが 5 秒ごとに通知する温度と湿度を
 * 表示する. USB のホストにならない機種でも使える.
 *
 * @property activity 画面を表示する Activity
 */
class BlePanel(
    private val activity: Activity,
) {
    private val client = BleThermoClient(activity, ::showState, ::showReading)
    private val stateView = TextView(activity)
    private val readingView =
        TextView(activity).apply { textSize = READING_TEXT_SIZE_SP }
    private var running = false
    private val toggleButton = activity.button(R.string.ble_connect) { toggle() }

    /** タブの中身. */
    val view: View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(activity).apply { text = activity.getString(R.string.ble_hint) })
            addView(toggleButton)
            addView(stateView)
            addView(readingView)
        }

    init {
        showState(BleState.IDLE)
        showReading(null)
    }

    /**
     * 権限を求めた結果を受け取って, 許可されていれば, 始める.
     *
     * @param requestCode 要求の番号
     * @param grantResults 権限ごとの結果
     */
    fun onPermissionResult(
        requestCode: Int,
        grantResults: IntArray,
    ) {
        if (requestCode == REQUEST_BLUETOOTH) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                client.start()
            } else {
                showState(BleState.FAILED)
                stateView.text = activity.getString(R.string.ble_permission_denied)
            }
        }
    }

    /** 画面を閉じるときに、探すのも、つなぐのも、やめる. */
    fun release() = client.stop()

    /** 押すたびに、始める (権限がなければ、求める) か、止める. */
    private fun toggle() {
        if (running) {
            client.stop()
        } else if (PERMISSIONS.all {
                activity.checkSelfPermission(it) ==
                    PackageManager.PERMISSION_GRANTED
            }
        ) {
            client.start()
        } else {
            activity.requestPermissions(PERMISSIONS, REQUEST_BLUETOOTH)
        }
    }

    /**
     * 状態を表示する.
     *
     * @param state 状態
     */
    private fun showState(state: BleState) {
        running =
            state == BleState.SCANNING ||
            state == BleState.CONNECTING ||
            state == BleState.CONNECTED
        toggleButton.text =
            activity.getString(if (running) R.string.ble_disconnect else R.string.ble_connect)
        stateView.text = activity.getString(stateMessage(state))
        if (state != BleState.CONNECTED) {
            showReading(null)
        }
    }

    /**
     * 温度と湿度を表示する.
     *
     * @param reading 温度と湿度. まだ届いていなければ null
     */
    private fun showReading(reading: ThermoReading?) {
        readingView.text =
            activity.getString(
                R.string.ble_reading,
                formatTemperature(reading),
                formatHumidity(reading),
            )
    }

    private companion object {
        /** 必要な権限 (探すのと、つなぐの). 位置情報は、要らない. */
        val PERMISSIONS =
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    }
}

/**
 * 状態のメッセージを選ぶ.
 *
 * @param state 状態
 * @return 文字列のリソース
 */
private fun stateMessage(state: BleState): Int =
    when (state) {
        BleState.IDLE -> R.string.ble_state_idle
        BleState.BLUETOOTH_OFF -> R.string.ble_state_off
        BleState.SCANNING -> R.string.ble_state_scanning
        BleState.NOT_FOUND -> R.string.ble_state_not_found
        BleState.CONNECTING -> R.string.ble_state_connecting
        BleState.CONNECTED -> R.string.ble_state_connected
        BleState.DISCONNECTED -> R.string.ble_state_disconnected
        BleState.FAILED -> R.string.ble_state_failed
    }
