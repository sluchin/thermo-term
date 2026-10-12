/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.core

/** 画面の制御のための, ANSI のエスケープシーケンス (ESC [ ... 英字) の形. */
private val ANSI_ESCAPE = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

/** シェルの引数を囲む引用符. */
private const val QUOTE = '"'

/** シェルの引数の中で, 引用符の前に付ける文字. */
private const val ESCAPE = '\\'

/** 制御文字の最後 (0x1F). これ以下と, DEL (0x7F) は, 引数に使えない. */
private const val LAST_CONTROL = 0x1F

/** DEL の文字コード. */
private const val DELETE = 0x7F

/**
 * シェルの出力から, 画面の制御 (色など) を取り除いて, 改行を統一する.
 *
 * @param text シェルの出力
 * @return 制御を取り除いた文字列 (改行は LF だけ. CR は消す)
 */
fun cleanShellOutput(text: String): String =
    text.replace(ANSI_ESCAPE, "").replace("\r\n", "\n").replace("\r", "")

/**
 * 文字列を, Zephyr のシェルの 1 つの引数として, 引用符で囲む.
 *
 * 空白を含む SSID も, 1 つの引数になる. 引用符とバックスラッシュは, バックスラッシュで打ち消す.
 *
 * @param value 引数の文字列 (制御文字を含まないこと)
 * @return 引用符で囲んだ文字列
 */
fun quoteShellArgument(value: String): String {
    val escaped =
        buildString {
            value.forEach {
                if (it == QUOTE || it == ESCAPE) {
                    append(ESCAPE)
                }
                append(it)
            }
        }
    return "$QUOTE$escaped$QUOTE"
}

/**
 * 文字列に, 制御文字 (改行など) が含まれるか調べる.
 *
 * @param value 調べる文字列
 * @return 含まれていれば true
 */
fun hasControlCharacter(value: String): Boolean =
    value.any {
        it.code <= LAST_CONTROL ||
            it.code == DELETE
    }
