/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.app

import android.app.Activity
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import jp.thermoterm.core.ActionResult
import jp.thermoterm.core.DeviceStatus
import jp.thermoterm.core.WifiInputError

/** 画面のタブ. */
private enum class Tab {
    /** SSID とパスワードの入力. */
    WIFI,

    /** ログとコマンド. */
    SERIAL,

    /** SD カードの起動のログ. */
    SD_LOG,

    /** Bluetooth でつないで表示. */
    BLE,
}

/**
 * 画面のボタンを押したときの処理.
 *
 * @property onConnect 「接続」
 * @property onTether 「テザリングに切り替える」(SSID とパスワードを受け取る)
 * @property onRestore 「元に戻す」
 * @property onToggleLog 「ログの開始・停止」
 * @property onSend 「送信」(入力したコマンドを受け取る)
 * @property onLoadSdLog 「SD カードの起動のログを読み込む」
 */
class ScreenActions(
    val onConnect: () -> Unit,
    val onTether: (String, String) -> Unit,
    val onRestore: () -> Unit,
    val onToggleLog: () -> Unit,
    val onSend: (String) -> Unit,
    val onLoadSdLog: () -> Unit,
)

/**
 * 接続の状態と, 4 つのタブ (「WiFi」と「Serial」と「SD log」と「BLE」) を並べた画面.
 *
 * 「WiFi」のタブは SSID とパスワードの入力と, 切り替えと戻すボタン. 入力欄にはビルドのときに
 * local.properties で渡した値 (thermoterm.ssid と thermoterm.psk) が入る.
 * 「Serial」のタブはログとコマンドの入力 ([LogPanel]).
 * 「SD log」のタブは SD カードに保存された起動のログ ([SdLogPanel]).
 * 「BLE」のタブは Bluetooth でつないで, 温度と湿度を表示する ([BlePanel]).
 *
 * 表示の更新は, どのスレッドから呼んでもよい (画面のスレッドで実行する).
 *
 * @property activity 画面を表示する Activity
 * @param actions ボタンを押したときの処理
 */
class TetherScreen(
    private val activity: Activity,
    actions: ScreenActions,
) {
    private val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val statusView = TextView(activity)
    private val messageView = TextView(activity)
    private val ssidInput =
        EditText(activity).apply {
            hint = activity.getString(R.string.ssid_hint)
            setText(BuildConfig.TETHER_SSID)
        }
    private val pskInput =
        EditText(activity).apply {
            hint = activity.getString(R.string.psk_hint)
            setText(BuildConfig.TETHER_PSK)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
    private val tetherButton =
        activity.button(R.string.tether) {
            actions.onTether(ssidInput.text.toString(), pskInput.text.toString())
        }
    private val restoreButton = activity.button(R.string.restore, actions.onRestore)

    /** 「Serial」のタブ. */
    val logPanel = LogPanel(activity, actions.onToggleLog, actions.onSend)

    /** 「SD log」のタブ. */
    val sdLogPanel = SdLogPanel(activity, actions.onLoadSdLog)

    /** 「BLE」のタブ. */
    val blePanel = BlePanel(activity)
    private val wifiTab = activity.button(R.string.tab_wifi) { selectTab(Tab.WIFI) }
    private val serialTab = activity.button(R.string.tab_serial) { selectTab(Tab.SERIAL) }
    private val sdLogTab = activity.button(R.string.tab_sdlog) { selectTab(Tab.SD_LOG) }
    private val bleTab = activity.button(R.string.tab_ble) { selectTab(Tab.BLE) }
    private val wifiPanel =
        ScrollView(activity).apply {
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(activity).apply { text = activity.getString(R.string.hint) })
                    addView(ssidInput)
                    addView(pskInput)
                    addView(tetherButton)
                    addView(restoreButton)
                },
            )
        }

    init {
        val tabs = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val weight = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        tabs.addView(wifiTab, weight)
        tabs.addView(serialTab, weight)
        tabs.addView(sdLogTab, weight)
        tabs.addView(bleTab, weight)
        val fill = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(activity.button(R.string.connect, actions.onConnect))
        root.addView(statusView)
        root.addView(messageView)
        root.addView(tabs)
        root.addView(wifiPanel, fill)
        root.addView(logPanel.view, fill)
        root.addView(sdLogPanel.view, fill)
        root.addView(blePanel.view, fill)
        activity.padForSystemBars(root)
        selectTab(Tab.WIFI)
        showDisconnected()
        activity.setContentView(root)
    }

    /** 未接続の表示にして, 切り替えと戻すボタンと, ログとコマンドの送信を押せなくする. */
    fun showDisconnected() {
        activity.runOnUiThread {
            statusView.text = activity.getString(R.string.not_connected)
            setActionsEnabled(false)
        }
        logPanel.setEnabled(false)
        sdLogPanel.setEnabled(false)
    }

    /** 接続したので, ログとコマンドの送信を使えるようにする (機器の状態が読めなくても使える). */
    fun showConnected() {
        activity.runOnUiThread { statusView.text = activity.getString(R.string.connected) }
        logPanel.setEnabled(true)
        sdLogPanel.setEnabled(true)
    }

    /**
     * 機器の状態を表示する (パスワードは, 設定されているかだけ).
     *
     * @param status 機器の状態
     */
    fun showStatus(status: DeviceStatus) {
        val psk = activity.getString(if (status.hasPsk) R.string.psk_set else R.string.psk_empty)
        val backup = status.backupSsid ?: activity.getString(R.string.no_backup)
        val text = activity.getString(R.string.status, status.ssid, psk, backup)
        activity.runOnUiThread {
            statusView.text = text
            setActionsEnabled(true)
            if (!status.tetherSupported) {
                messageView.text = activity.getString(R.string.old_firmware)
            }
        }
    }

    /**
     * メッセージを表示する.
     *
     * @param text メッセージ
     */
    fun showMessage(text: String) {
        activity.runOnUiThread { messageView.text = text }
    }

    /**
     * 操作の結果を, メッセージにして表示する.
     *
     * @param result 操作の結果
     * @param doneId 成功したときのメッセージ
     */
    fun showResult(
        result: ActionResult,
        doneId: Int,
    ) {
        when (result) {
            is ActionResult.Success -> {
                showStatus(result.status)
                showMessage(activity.getString(doneId))
            }
            is ActionResult.Rejected ->
                showMessage(
                    activity.getString(inputErrorMessage(result.error)),
                )
            is ActionResult.Failed ->
                showMessage(activity.getString(R.string.failed, result.command, result.code ?: "?"))
        }
    }

    /**
     * タブを切り替える (選んでいるタブのボタンは押せなくする).
     *
     * @param tab 表示するタブ
     */
    private fun selectTab(tab: Tab) {
        wifiPanel.visibility = if (tab == Tab.WIFI) View.VISIBLE else View.GONE
        logPanel.view.visibility = if (tab == Tab.SERIAL) View.VISIBLE else View.GONE
        sdLogPanel.view.visibility = if (tab == Tab.SD_LOG) View.VISIBLE else View.GONE
        blePanel.view.visibility = if (tab == Tab.BLE) View.VISIBLE else View.GONE
        wifiTab.isEnabled = tab != Tab.WIFI
        serialTab.isEnabled = tab != Tab.SERIAL
        sdLogTab.isEnabled = tab != Tab.SD_LOG
        bleTab.isEnabled = tab != Tab.BLE
    }

    /**
     * 切り替えと戻すボタンを, 押せるか押せないかにする.
     *
     * @param enabled 押せるなら true
     */
    private fun setActionsEnabled(enabled: Boolean) {
        tetherButton.isEnabled = enabled
        restoreButton.isEnabled = enabled
    }
}

/**
 * 入力の誤りの, メッセージを選ぶ.
 *
 * @param error 入力の誤り
 * @return 文字列のリソース
 */
private fun inputErrorMessage(error: WifiInputError): Int =
    when (error) {
        WifiInputError.SSID_EMPTY -> R.string.input_ssid_empty
        WifiInputError.SSID_TOO_LONG -> R.string.input_ssid_too_long
        WifiInputError.PSK_TOO_LONG -> R.string.input_psk_too_long
        WifiInputError.CONTROL_CHARACTER -> R.string.input_control
    }
