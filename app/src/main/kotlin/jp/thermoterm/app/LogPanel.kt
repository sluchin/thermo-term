/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.app

import android.app.Activity
import android.graphics.Typeface
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** ログの文字の大きさ (sp). */
private const val LOG_TEXT_SIZE_SP = 11f

/** 画面に残すログの最大の文字数. これを超えたら、古い方から捨てる. */
private const val LOG_MAX_CHARS = 20000

/** 終わりまでの距離がこの値(px)以下なら、終わりまで見ているとみなす (自動でスクロールする). */
private const val BOTTOM_MARGIN_PX = 48

/**
 * ボタンを作る.
 *
 * @receiver 画面を表示する Activity
 * @param textId ボタンの文字列のリソース
 * @param onClick 押したときの処理
 * @return ボタン
 */
fun Activity.button(
    textId: Int,
    onClick: () -> Unit,
): Button =
    Button(this).apply {
        text = getString(textId)
        setOnClickListener { onClick() }
    }

/**
 * シリアルのログの表示と、コマンドの入力を並べた部分 (「Serial」のタブ).
 *
 * ログは、終わりまで見ているときは、新しい行が来るたびに、自動でスクロールする
 * (上へ戻して読んでいるときは、動かさない).
 *
 * @property activity 画面を表示する Activity
 * @param onToggleLog 「ログの開始・停止」を押したときの処理
 * @param onSend 「送信」を押したときの処理 (入力したコマンドを受け取る)
 */
class LogPanel(
    private val activity: Activity,
    onToggleLog: () -> Unit,
    onSend: (String) -> Unit,
) {
    private val logView =
        TextView(activity).apply {
            typeface = Typeface.MONOSPACE
            textSize = LOG_TEXT_SIZE_SP
            setTextIsSelectable(true)
        }
    private val scroll = ScrollView(activity).apply { addView(logView) }
    private val logButton = activity.button(R.string.log_start, onToggleLog)
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
    private val sendButton = activity.button(R.string.send) { send(onSend) }

    /** タブの中身. ログが残りの高さを使う. */
    val view: View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            val weight = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            buttons.addView(logButton, weight)
            buttons.addView(activity.button(R.string.log_clear) { logView.text = "" }, weight)
            addView(buttons)
            addView(
                scroll,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            val sendRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            sendRow.addView(commandInput, weight)
            sendRow.addView(sendButton)
            addView(sendRow)
        }

    /**
     * ログを終わりに足す (多すぎたら、古い方を捨てる). 終わりまで見ていたら、自動でスクロールする.
     *
     * @param text 足す文字列
     */
    fun append(text: String) {
        activity.runOnUiThread {
            val follow = isAtBottom()
            logView.append(text)
            val length = logView.length()
            if (length > LOG_MAX_CHARS) {
                logView.text = logView.text.subSequence(length - LOG_MAX_CHARS, length)
            }
            if (follow) {
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
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
     * ログとコマンドの送信のボタンを、押せるか押せないかにする.
     *
     * @param enabled 押せるなら true
     */
    fun setEnabled(enabled: Boolean) {
        activity.runOnUiThread {
            logButton.isEnabled = enabled
            sendButton.isEnabled = enabled
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
     * ログの終わりまで見ているか調べる (中身が短くて、スクロールしないときも、true).
     *
     * @return 終わりまで見ていれば true
     */
    private fun isAtBottom(): Boolean =
        logView.bottom - (scroll.height + scroll.scrollY) <= BOTTOM_MARGIN_PX
}
