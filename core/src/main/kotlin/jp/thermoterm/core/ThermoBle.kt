/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.core

import java.util.Locale

/** Thermo サービスの UUID (ファームウェアの `thermo_ble_uuid.h` の THERMO_UUID_SERVICE_VAL と同じ). */
const val THERMO_SERVICE_UUID = "9f3c1a00-7b6e-4c3a-9d5e-2a6f0b1c8d01"

/** 温度の特性の UUID (読み取りと通知). */
const val THERMO_TEMPERATURE_UUID = "9f3c1a01-7b6e-4c3a-9d5e-2a6f0b1c8d01"

/** 温度の特性の値の大きさ (バイト). 温度 2 バイトと, 湿度 2 バイト. */
const val THERMO_TEMPERATURE_SIZE = 4

/** 湿度を測れないときの値 (ファームウェアの THERMO_HUMIDITY_NONE). */
private const val HUMIDITY_NONE = 0xFFFF

/** 1 バイトを符号なしの 0 から 255 にするマスク. */
private const val BYTE_MASK = 0xFF

/** 1 バイトのビット数. */
private const val BYTE_BITS = 8

/** 10 倍した値から, 元の値に戻す割り算の値. */
private const val SCALE = 10.0

/** 温度の符号つき 16 ビットの, 符号のビット. */
private const val SIGN_BIT = 0x8000

/** 16 ビットの値の個数 (負の値に直すときに引く). */
private const val SHORT_RANGE = 0x10000

/** 値が測れないときに表示する文字. */
const val NO_READING = "--"

/**
 * 機器が通知した, 温度と湿度.
 *
 * @property temperatureC 温度 (℃)
 * @property humidityPercent 湿度 (%). 湿度を測れないセンサは null
 */
data class ThermoReading(
    val temperatureC: Double,
    val humidityPercent: Double?,
)

/**
 * 温度の特性の値 (4 バイト) を解析する.
 *
 * 先頭の 2 バイトが温度 (符号つき 16 ビット, ℃ の 10 倍), 続く 2 バイトが湿度 (符号なし 16 ビット,
 * % の 10 倍. 0xFFFF は湿度なし). どちらもリトルエンディアン.
 *
 * @param bytes 特性の値
 * @return 温度と湿度. 大きさが 4 バイトでなければ null
 */
fun parseThermoReading(bytes: ByteArray): ThermoReading? {
    if (bytes.size != THERMO_TEMPERATURE_SIZE) {
        return null
    }
    val temp = littleEndian16(bytes, 0).let { if (it and SIGN_BIT != 0) it - SHORT_RANGE else it }
    val humidity = littleEndian16(bytes, 2)
    return ThermoReading(
        temp / SCALE,
        if (humidity == HUMIDITY_NONE) null else humidity / SCALE,
    )
}

/**
 * 温度を, 表示用の文字列にする (例: 23.5 °C).
 *
 * @param reading 温度と湿度. まだ届いていなければ null
 * @return 表示用の文字列. null なら [NO_READING]
 */
fun formatTemperature(reading: ThermoReading?): String =
    reading?.let { "%.1f °C".format(Locale.ROOT, it.temperatureC) } ?: NO_READING

/**
 * 湿度を, 表示用の文字列にする (例: 45.0 %).
 *
 * @param reading 温度と湿度. まだ届いていなければ null
 * @return 表示用の文字列. 湿度がなければ [NO_READING]
 */
fun formatHumidity(reading: ThermoReading?): String =
    reading?.humidityPercent?.let { "%.1f %%".format(Locale.ROOT, it) } ?: NO_READING

/**
 * バイト列の, 指定の位置から, リトルエンディアンの符号なし 16 ビットを読む.
 *
 * @param bytes バイト列
 * @param offset 位置
 * @return 0 から 65535 の値
 */
private fun littleEndian16(
    bytes: ByteArray,
    offset: Int,
): Int =
    (bytes[offset].toInt() and BYTE_MASK) or
        ((bytes[offset + 1].toInt() and BYTE_MASK) shl BYTE_BITS)
