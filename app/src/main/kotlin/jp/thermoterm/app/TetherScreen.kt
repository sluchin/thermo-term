/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.app

import android.app.Activity
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import jp.thermoterm.core.ActionResult
import jp.thermoterm.core.DeviceStatus
import jp.thermoterm.core.WifiInputError

/** 画面の端と, 内容の間の余白 (dp). */
private const val CONTENT_PADDING_DP = 16

/**
 * 接続の状態と、2 つのタブ (「WiFi」と「Serial」) を並べた画面.
 *
 * 「WiFi」のタブは、SSID とパスワードの入力と、切り替えと戻すボタン. 入力欄には、ビルドのときに
 * local.properties で渡した値 (thermoterm.ssid と thermoterm.psk) が入る.
 * 「Serial」のタブは、ログとコマンドの入力 ([LogPanel]).
 *
 * 表示の更新は, どのスレッドから呼んでもよい (画面のスレッドで実行する).
 *
 * @property activity 画面を表示する Activity
 * @param onConnect 「接続」を押したときの処理
 * @param onTether 「テザリングに切り替える」を押したときの処理 (SSID とパスワードを受け取る)
 * @param onRestore 「元に戻す」を押したときの処理
 * @param onToggleLog 「ログの開始・停止」を押したときの処理
 * @param onSend 「送信」を押したときの処理 (入力したコマンドを受け取る)
 */
class TetherScreen(
    private val activity: Activity,
    onConnect: () -> Unit,
    onTether: (String, String) -> Unit,
    onRestore: () -> Unit,
    onToggleLog: () -> Unit,
    onSend: (String) -> Unit,
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
            onTether(ssidInput.text.toString(), pskInput.text.toString())
        }
    private val restoreButton = activity.button(R.string.restore, onRestore)
    private val logPanel = LogPanel(activity, onToggleLog, onSend)
    private val wifiTab = activity.button(R.string.tab_wifi) { selectTab(wifiPanel = true) }
    private val serialTab = activity.button(R.string.tab_serial) { selectTab(wifiPanel = false) }
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
        val fill = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(activity.button(R.string.connect, onConnect))
        root.addView(statusView)
        root.addView(messageView)
        root.addView(tabs)
        root.addView(wifiPanel, fill)
        root.addView(logPanel.view, fill)
        applySystemBarInsets()
        selectTab(wifiPanel = true)
        showDisconnected()
        activity.setContentView(root)
    }

    /** 未接続の表示にして, 切り替えと戻すボタンと、ログとコマンドの送信を押せなくする. */
    fun showDisconnected() {
        activity.runOnUiThread {
            statusView.text = activity.getString(R.string.not_connected)
            setActionsEnabled(false)
        }
        logPanel.setEnabled(false)
    }

    /** 接続したので、ログとコマンドの送信を使えるようにする (機器の状態が読めなくても使える). */
    fun showConnected() {
        activity.runOnUiThread { statusView.text = activity.getString(R.string.connected) }
        logPanel.setEnabled(true)
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
     * ログを画面の終わりに足す (終わりまで見ていれば、自動でスクロールする).
     *
     * @param text 足す文字列
     */
    fun appendLog(text: String) = logPanel.append(text)

    /**
     * ログのボタンの表示を、開始か停止にする.
     *
     * @param logging ログを読んでいるなら true
     */
    fun setLogging(logging: Boolean) = logPanel.setLogging(logging)

    /**
     * タブを切り替える (選んでいるタブのボタンは、押せなくする).
     *
     * @param wifiPanel true なら「WiFi」、false なら「Serial」
     */
    private fun selectTab(wifiPanel: Boolean) {
        this.wifiPanel.visibility = if (wifiPanel) View.VISIBLE else View.GONE
        logPanel.view.visibility = if (wifiPanel) View.GONE else View.VISIBLE
        wifiTab.isEnabled = !wifiPanel
        serialTab.isEnabled = wifiPanel
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

    /**
     * 内容が, システムバーとアクションバーの下に, 隠れないようにする.
     *
     * targetSdk 35 以上は, 画面が, システムバーの下まで広がる (edge-to-edge).
     * システムバーの余白と, アクションバーの高さを, パディングに入れる.
     */
    private fun applySystemBarInsets() {
        val padding = activity.dp(CONTENT_PADDING_DP)
        val actionBarSize =
            TypedValue().let {
                activity.theme.resolveAttribute(android.R.attr.actionBarSize, it, true)
                TypedValue.complexToDimensionPixelSize(it.data, activity.resources.displayMetrics)
            }
        root.setOnApplyWindowInsetsListener { target, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            target.setPadding(
                bars.left + padding,
                bars.top + actionBarSize + padding,
                bars.right + padding,
                bars.bottom + padding,
            )
            insets
        }
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
