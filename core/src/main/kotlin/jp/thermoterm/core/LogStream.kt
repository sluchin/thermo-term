/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.core

import kotlinx.coroutines.delay

/** 何も届かなかったときに、次の受信まで待つ時間 (ミリ秒). コルーチンを、止められるようにする. */
private const val IDLE_DELAY_MILLIS = 10L

/** 受信の 1 回の待ち時間 (ミリ秒). */
private const val LOG_READ_TIMEOUT_MILLIS = 100

/** UTF-8 の 1 文字の最大のバイト数. */
private const val UTF8_MAX_BYTES = 4

/** 1 バイトを、符号なしの 0 から 255 にするマスク. */
private const val BYTE_MASK = 0xFF

/** 2 バイトと 3 バイトの文字の、バイト数. */
private const val TWO_BYTES = 2
private const val THREE_BYTES = 3

/** UTF-8 の, 続きのバイト (10xxxxxx) を見分けるマスクと値. */
private const val CONTINUATION_MASK = 0xC0
private const val CONTINUATION_BITS = 0x80

/** UTF-8 の先頭のバイトの, 2 バイト, 3 バイト, 4 バイトの文字を表す上位ビット. */
private const val LEAD2_MASK = 0xE0
private const val LEAD2_BITS = 0xC0
private const val LEAD3_MASK = 0xF0
private const val LEAD3_BITS = 0xE0
private const val LEAD4_MASK = 0xF8
private const val LEAD4_BITS = 0xF0

/**
 * シリアルポートから届くログを、止められるまで読み続けて、文字列にして渡す.
 *
 * 文字の途中 (日本語など) で受信が切れても、続きが届いてから渡す. 画面の制御 (色) と CR は、
 * 取り除く. コルーチンを取り消すと、止まる. 同じポートで、[ShellSession] と同時に使わないこと.
 *
 * @param port 機器につながったシリアルポート
 * @param onText 文字列を受け取る処理
 */
suspend fun streamLog(
    port: SerialPort,
    onText: (String) -> Unit,
) {
    var pending = ByteArray(0)
    while (true) {
        val chunk = port.read(LOG_READ_TIMEOUT_MILLIS)
        if (chunk.isEmpty()) {
            delay(IDLE_DELAY_MILLIS)
        } else {
            val bytes = pending + chunk
            val complete = completeUtf8Length(bytes)
            pending = bytes.copyOfRange(complete, bytes.size)
            val text = cleanShellOutput(String(bytes, 0, complete, Charsets.UTF_8))
            if (text.isNotEmpty()) {
                onText(text)
            }
        }
    }
}

/**
 * バイト列の終わりが、UTF-8 の文字の途中なら、その手前までの長さを返す.
 *
 * @param bytes バイト列
 * @return 完全な文字だけを含む、先頭からの長さ
 */
internal fun completeUtf8Length(bytes: ByteArray): Int {
    val tail = minOf(UTF8_MAX_BYTES - 1, bytes.size)
    for (back in 1..tail) {
        val value = bytes[bytes.size - back].toInt() and BYTE_MASK
        if (value and CONTINUATION_MASK != CONTINUATION_BITS) {
            return if (needed(value) > back) bytes.size - back else bytes.size
        }
    }
    return bytes.size
}

/**
 * UTF-8 の先頭のバイトから、その文字のバイト数を求める.
 *
 * @param lead 先頭のバイト (0 から 255)
 * @return バイト数. 1 バイトの文字か、不正な値は 1
 */
private fun needed(lead: Int): Int =
    when {
        lead and LEAD2_MASK == LEAD2_BITS -> TWO_BYTES
        lead and LEAD3_MASK == LEAD3_BITS -> THREE_BYTES
        lead and LEAD4_MASK == LEAD4_BITS -> UTF8_MAX_BYTES
        else -> 1
    }
