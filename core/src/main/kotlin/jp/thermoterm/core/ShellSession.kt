/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.core

import java.io.ByteArrayOutputStream

/** シェルのプロンプト (Zephyr の既定). */
const val DEFAULT_PROMPT = "uart:~$ "

/** 1 回の受信で待つ時間 (ミリ秒). */
const val DEFAULT_READ_TIMEOUT_MILLIS = 100

/** 何も届かない受信を, この回数だけ続けたら, 応答がないとみなす (100 ミリ秒 x 30 = 3 秒). */
const val DEFAULT_MAX_IDLE_READS = 30

/**
 * 機器のシェル (Zephyr の shell) にコマンドを送って, 応答を受け取る.
 *
 * コマンドの終わりはプロンプトが現れることで分かる.
 *
 * @property port 機器につながったシリアルポート
 * @property prompt シェルのプロンプト
 * @property readTimeoutMillis 1 回の受信で待つ時間 (ミリ秒)
 * @property maxIdleReads 何も届かない受信を続けてよい回数
 */
class ShellSession(
    private val port: SerialPort,
    private val prompt: String = DEFAULT_PROMPT,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
    private val maxIdleReads: Int = DEFAULT_MAX_IDLE_READS,
) {
    /**
     * コマンドを実行して, 応答を返す.
     *
     * 先に, 溜まっている出力 (起動のログなど) を捨ててから, コマンドを送る.
     * 応答には, 入力の反響 (エコー) が含まれる. パスワードを含むコマンドの応答は, 画面に出さないこと.
     *
     * @param command コマンド (改行は付けない)
     * @return プロンプトの前までの応答 (画面の制御は取り除き済み)
     * @throws ShellException プロンプトが現れなかった (機器が応答しない)
     */
    suspend fun execute(command: String): String {
        discardPending()
        port.write("$command\r".toByteArray(Charsets.UTF_8))
        val received = ByteArrayOutputStream()
        var idle = 0
        while (idle < maxIdleReads) {
            val chunk = port.read(readTimeoutMillis)
            if (chunk.isEmpty()) {
                idle++
            } else {
                idle = 0
                received.write(chunk)
                val text = cleanShellOutput(received.toString(Charsets.UTF_8))
                if (text.endsWith(prompt)) {
                    return text.removeSuffix(prompt)
                }
            }
        }
        throw ShellException("No prompt from the device")
    }

    /** 溜まっている出力を, 何も届かなくなるまで読み捨てる. */
    private suspend fun discardPending() {
        while (port.read(readTimeoutMillis).isNotEmpty()) {
            // 読み捨てる
        }
    }
}
