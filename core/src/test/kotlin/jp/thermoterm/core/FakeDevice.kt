/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.core

/**
 * Zephyr のシェルを真似る, 偽の機器.
 *
 * 行 (CR で終わる) を受け取ると, 入力の反響 (エコー) と, 登録した応答と, プロンプトを返す.
 *
 * @property replies コマンドごとの応答. 登録のないコマンドは, 応答なしで, プロンプトを返す
 * @property silent true なら, 何も返さない (機器が応答しない)
 * @property chunkSize 1 回の read で返す最大のバイト数 (文字の途中で切れる確認に使う)
 */
class FakeDevice(
    private val replies: MutableMap<String, String> = mutableMapOf(),
    private val silent: Boolean = false,
    private val chunkSize: Int = Int.MAX_VALUE,
) : SerialPort {
    /** 送られた行 (改行を除く). */
    val written = mutableListOf<String>()

    /** 次の read で返すデータ. */
    private var pending = ByteArray(0)

    /**
     * 次の read で返すデータを足す (起動のログなど).
     *
     * @param text 足す文字列
     */
    fun push(text: String) {
        pending += text.toByteArray(Charsets.UTF_8)
    }

    /**
     * コマンドの応答を登録する (同じコマンドは, 置き換える).
     *
     * @param command コマンド
     * @param reply 応答 (行の区切りは LF)
     */
    fun reply(
        command: String,
        reply: String,
    ) {
        replies[command] = reply
    }

    override suspend fun write(data: ByteArray) {
        val line = String(data, Charsets.UTF_8).removeSuffix("\r")
        written += line
        if (!silent) {
            val reply = replies[line].orEmpty().replace("\n", "\r\n")
            push("$line\r\n$reply\r\n\u001B[1;32muart:~\$ \u001B[m")
        }
    }

    override suspend fun read(timeoutMillis: Int): ByteArray {
        val size = minOf(chunkSize, pending.size)
        val chunk = pending.copyOfRange(0, size)
        pending = pending.copyOfRange(size, pending.size)
        return chunk
    }
}
