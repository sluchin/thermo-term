/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.app

import android.app.Activity
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import jp.thermoterm.core.ActionResult
import jp.thermoterm.core.DeviceStatus
import jp.thermoterm.core.WifiInputError

/** 画面の端と, 内容の間の余白 (dp). */
private const val CONTENT_PADDING_DP = 16

/** ログの文字の大きさ (sp). */
private const val LOG_TEXT_SIZE_SP = 11f

/** 画面に残すログの最大の文字数. これを超えたら、古い方から捨てる. */
private const val LOG_MAX_CHARS = 20000

/**
 * 説明, 入力欄, ボタン, 機器の状態を並べた画面.
 *
 * SSID とパスワードの入力欄には, ビルドのときに local.properties で渡した値 (thermoterm.ssid と
 * thermoterm.psk) が入る.
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
    private val root = ScrollView(activity).apply { clipToPadding = false }
    private val statusView = TextView(activity)
    private val messageView = TextView(activity)
    private val ssidInput =
        EditText(
            activity,
        ).apply { hint = activity.getString(R.string.ssid_hint) }
    private val pskInput =
        EditText(activity).apply {
            hint = activity.getString(R.string.psk_hint)
            setText(BuildConfig.TETHER_PSK)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
    private val tetherButton =
        button(R.string.tether) { onTether(ssidInput.text.toString(), pskInput.text.toString()) }
    private val restoreButton = button(R.string.restore, onRestore)
    private val commandInput =
        EditText(activity).apply {
            hint = activity.getString(R.string.command_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEND
            isSingleLine = true
            setOnEditorActionListener { _, _, _ ->
                send(onSend)
                true
            }
        }
    private val sendButton = button(R.string.send) { send(onSend) }
    private val logButton = button(R.string.log_start, onToggleLog)
    private val logView =
        TextView(activity).apply {
            typeface = Typeface.MONOSPACE
            textSize = LOG_TEXT_SIZE_SP
            setTextIsSelectable(true)
        }

    init {
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(TextView(activity).apply { text = activity.getString(R.string.hint) })
        content.addView(button(R.string.connect, onConnect))
        content.addView(statusView)
        content.addView(ssidInput)
        content.addView(pskInput)
        content.addView(tetherButton)
        content.addView(restoreButton)
        content.addView(messageView)
        content.addView(commandInput)
        content.addView(sendButton)
        content.addView(logButton)
        content.addView(button(R.string.log_clear) { logView.text = "" })
        content.addView(logView)
        root.addView(content)
        applySystemBarInsets()
        showDisconnected()
        activity.setContentView(root)
    }

    /** 未接続の表示にして, 切り替えと戻すボタンを押せなくする. */
    fun showDisconnected() {
        activity.runOnUiThread {
            statusView.text = activity.getString(R.string.not_connected)
            setActionsEnabled(false)
            logButton.isEnabled = false
            sendButton.isEnabled = false
        }
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
            logButton.isEnabled = true
            sendButton.isEnabled = true
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
     * 入力したコマンドを渡して、入力欄を空にする (空のときは、何もしない).
     *
     * @param onSend コマンドを受け取る処理
     */
    private fun send(onSend: (String) -> Unit) {
        val command = commandInput.text.toString()
        if (command.isNotEmpty()) {
            commandInput.setText("")
            onSend(command)
        }
    }

    /**
     * ログを画面の終わりに足す (多すぎたら、古い方を捨てる).
     *
     * @param text 足す文字列
     */
    fun appendLog(text: String) {
        activity.runOnUiThread {
            logView.append(text)
            val length = logView.length()
            if (length > LOG_MAX_CHARS) {
                logView.text = logView.text.subSequence(length - LOG_MAX_CHARS, length)
            }
        }
    }

    /**
     * ログのボタンの表示を、開始か停止にする.
     *
     * @param logging ログを読んでいるなら true
     */
    fun setLogging(logging: Boolean) {
        activity.runOnUiThread {
            logButton.text =
                activity.getString(if (logging) R.string.log_stop else R.string.log_start)
        }
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
     * ボタンを作る.
     *
     * @param textId ボタンの文字列のリソース
     * @param onClick 押したときの処理
     * @return ボタン
     */
    private fun button(
        textId: Int,
        onClick: () -> Unit,
    ): Button =
        Button(activity).apply {
            text = activity.getString(textId)
            setOnClickListener { onClick() }
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
