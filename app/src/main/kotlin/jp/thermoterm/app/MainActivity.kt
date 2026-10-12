/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.app

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import jp.thermoterm.core.ShellSession
import jp.thermoterm.core.TetherController
import jp.thermoterm.core.streamLog
import jp.thermoterm.core.writeLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/** USB の使用許可の結果を受け取る, ブロードキャストの名前. */
private const val ACTION_USB_PERMISSION = "jp.thermoterm.app.USB_PERMISSION"

/**
 * ESP32C3 の WiFi の SSID とパスワードを, スマートフォンのテザリングに切り替えて, 元に戻す画面.
 *
 * スマートフォンと ESP32C3 を, USB ケーブル (Type-C) でつなぐ. 元の SSID とパスワードは,
 * ESP32C3 が保存する (このアプリは, パスワードを保存しない). テザリングの SSID は, Android が
 * アプリに教えないので, 手で入力する.
 */
class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var screen: TetherScreen
    private var port: UsbCdcPort? = null
    private var controller: TetherController? = null
    private var logJob: Job? = null

    /** 使用許可の結果と, 機器が抜かれたことを受け取る. */
    private val usbReceiver =
        object : BroadcastReceiver() {
            /**
             * ブロードキャストを受け取る.
             *
             * @param context コンテキスト
             * @param intent 受け取ったインテント
             */
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == ACTION_USB_PERMISSION) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) {
                        openPort()
                    } else {
                        screen.showMessage(
                            getString(R.string.permission_denied),
                        )
                    }
                } else {
                    disconnect()
                }
            }
        }

    /**
     * 画面を作って, USB のブロードキャストの受け取りを始める.
     *
     * @param savedInstanceState 保存した状態
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen =
            TetherScreen(
                this,
                onConnect = ::connect,
                onTether = { ssid, psk ->
                    launchAction { control ->
                        screen.showResult(control.tether(ssid, psk), R.string.done_tether)
                    }
                },
                onRestore = {
                    launchAction { control ->
                        screen.showResult(control.restore(), R.string.done_restore)
                    }
                },
                onToggleLog = ::toggleLog,
                onSend = ::sendCommand,
            )
        val filter =
            IntentFilter(ACTION_USB_PERMISSION).apply {
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
        registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    /** 画面を閉じるときに, 通信を止めて, 受け取りをやめる. */
    override fun onDestroy() {
        unregisterReceiver(usbReceiver)
        disconnect()
        scope.cancel()
        super.onDestroy()
    }

    /** 機器を探して, 使う許可を求める (許可があれば, すぐに開く). */
    private fun connect() {
        val manager = getSystemService(UsbManager::class.java)
        val device = UsbCdcPort.findDevice(manager)
        if (device == null) {
            screen.showMessage(getString(R.string.no_device))
        } else if (manager.hasPermission(device)) {
            openPort()
        } else {
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
            val pending = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_MUTABLE)
            manager.requestPermission(device, pending)
        }
    }

    /** 許可された機器のポートを開いて, 状態を読む. */
    private fun openPort() {
        val manager = getSystemService(UsbManager::class.java)
        val opened = UsbCdcPort.findDevice(manager)?.let { UsbCdcPort.open(manager, it) }
        if (opened == null) {
            screen.showMessage(getString(R.string.open_failed))
            return
        }
        disconnect()
        port = opened
        controller = TetherController(ShellSession(opened))
        screen.showConnected()
        launchAction { control -> screen.showStatus(control.readStatus()) }
    }

    /** ポートを閉じて, 未接続の表示に戻す. */
    private fun disconnect() {
        logJob?.cancel()
        logJob = null
        screen.setLogging(false)
        port?.close()
        port = null
        controller = null
        screen.showDisconnected()
    }

    /**
     * 通信を別スレッドで行い, 失敗したらメッセージを出す.
     *
     * @param action 機器に対する操作
     */
    private fun launchAction(action: suspend (TetherController) -> Unit) {
        val control = controller ?: return
        screen.showMessage(getString(R.string.working))
        scope.launch {
            stopLog()
            // 画面に出すのは, メッセージだけ (パスワードを含む応答は, 出さない)
            runCatching { action(control) }.onFailure {
                screen.showMessage(getString(R.string.error, it.message ?: it.javaClass.simpleName))
            }
        }
    }

    /** ログの読み取りを、始める. 読んでいるときは、止める. */
    private fun toggleLog() {
        val serial = port ?: return
        if (logJob != null) {
            logJob?.cancel()
            logJob = null
            screen.setLogging(false)
            return
        }
        screen.setLogging(true)
        logJob =
            scope.launch {
                runCatching { streamLog(serial, screen::appendLog) }.onFailure {
                    if (it !is CancellationException) {
                        screen.showMessage(
                            getString(
                                R.string.error,
                                it.message ?: it.javaClass.simpleName,
                            ),
                        )
                    }
                }
            }
    }

    /** ログの読み取りが動いていれば、終わるまで待って止める (コマンドの応答と、混ざらないように). */
    private suspend fun stopLog() {
        logJob?.cancelAndJoin()
        logJob = null
        screen.setLogging(false)
    }

    /**
     * 入力したコマンドを、そのまま機器に送る (応答は、ログに出る. ログが止まっていれば、始める).
     *
     * @param command コマンド
     */
    private fun sendCommand(command: String) {
        val serial = port ?: return
        if (logJob == null) {
            toggleLog()
        }
        scope.launch {
            runCatching { serial.writeLine(command) }.onFailure {
                screen.showMessage(getString(R.string.error, it.message ?: it.javaClass.simpleName))
            }
        }
    }
}
