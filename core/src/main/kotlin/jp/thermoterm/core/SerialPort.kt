/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.core

/**
 * ESP32C3 とつながったシリアルポート (USB の CDC ACM).
 *
 * Android の実装は app にある (USB ホストの API). core は、この型にだけ依存するので、
 * 単体テストでは、偽の機器に置き換える.
 */
interface SerialPort {
    /**
     * データを送る.
     *
     * @param data 送るデータ
     */
    suspend fun write(data: ByteArray)

    /**
     * データを受け取る.
     *
     * @param timeoutMillis 待つ時間 (ミリ秒)
     * @return 受け取ったデータ. 時間内に何も届かなければ, 空
     */
    suspend fun read(timeoutMillis: Int): ByteArray
}

/**
 * 機器との通信の失敗 (応答がない, 応答の形が違う).
 *
 * @param message 失敗の内容
 */
class ShellException(
    message: String,
) : Exception(message)
