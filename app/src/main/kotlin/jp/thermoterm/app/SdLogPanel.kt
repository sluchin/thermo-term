/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.app

import android.app.Activity
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** ログの文字の大きさ (sp). */
private const val SDLOG_TEXT_SIZE_SP = 11f

/**
 * SD カードに保存された起動のログの表示 (「SD log」のタブ).
 *
 * 「Load」を押すと, 機器から起動のログ (boot.log) を読んで, 表示する.
 *
 * @property activity 画面を表示する Activity
 * @param onLoad 「読み込む」を押したときの処理
 */
class SdLogPanel(
    private val activity: Activity,
    onLoad: () -> Unit,
) {
    private val textView =
        TextView(activity).apply {
            typeface = Typeface.MONOSPACE
            textSize = SDLOG_TEXT_SIZE_SP
            setTextIsSelectable(true)
        }
    private val loadButton = activity.button(R.string.sdlog_load, onLoad)

    /** タブの中身. ログが残りの高さを使う. */
    val view: View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(loadButton)
            addView(
                ScrollView(activity).apply { addView(textView) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }

    /**
     * 読み込んだログを表示する (空なら, その旨を表示する).
     *
     * @param text ログの文字列
     */
    fun show(text: String) {
        activity.runOnUiThread {
            textView.text = text.ifEmpty { activity.getString(R.string.sdlog_empty) }
        }
    }

    /**
     * 「読み込む」ボタンを押せるか押せないかにする.
     *
     * @param enabled 押せるなら true
     */
    fun setEnabled(enabled: Boolean) {
        activity.runOnUiThread { loadButton.isEnabled = enabled }
    }
}
