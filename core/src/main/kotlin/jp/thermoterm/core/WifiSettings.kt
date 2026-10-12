/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.core

/** 機器が保存できる SSID の最大の長さ (バイト). ファームウェアの CFG_SSID_MAX と同じ. */
const val SSID_MAX_BYTES = 32

/** 機器が保存できるパスワードの最大の長さ (バイト). ファームウェアの CFG_PSK_MAX と同じ. */
const val PSK_MAX_BYTES = 64

/** 入力した SSID とパスワードの誤り. */
enum class WifiInputError {
    /** SSID が空. */
    SSID_EMPTY,

    /** SSID が, 32 バイトを超える. */
    SSID_TOO_LONG,

    /** パスワードが, 64 バイトを超える. */
    PSK_TOO_LONG,

    /** SSID かパスワードに, 改行などの制御文字がある. */
    CONTROL_CHARACTER,
}

/**
 * テザリングの SSID とパスワードの入力を確認する.
 *
 * 長さは UTF-8 のバイト数で数える (機器は, バイト列として保存する).
 *
 * @param ssid テザリングの SSID
 * @param psk テザリングのパスワード (空でもよい)
 * @return 誤りがあれば, その種類. なければ null
 */
fun validateWifi(
    ssid: String,
    psk: String,
): WifiInputError? =
    when {
        ssid.isEmpty() -> WifiInputError.SSID_EMPTY
        hasControlCharacter(ssid) || hasControlCharacter(psk) -> WifiInputError.CONTROL_CHARACTER
        ssid.toByteArray(Charsets.UTF_8).size > SSID_MAX_BYTES -> WifiInputError.SSID_TOO_LONG
        psk.toByteArray(Charsets.UTF_8).size > PSK_MAX_BYTES -> WifiInputError.PSK_TOO_LONG
        else -> null
    }

/**
 * 機器の WiFi の設定の状態 (`thermo show` の応答).
 *
 * パスワードは, 機器が伏せるので、分からない (設定されているかだけ分かる).
 *
 * @property ssid 今の SSID
 * @property hasPsk パスワードが設定されているか
 * @property backupSsid テザリングに切り替える前の SSID. 切り替えていなければ null
 * @property tetherSupported ファームウェアが, テザリングの切り替えに対応しているか
 */
data class DeviceStatus(
    val ssid: String,
    val hasPsk: Boolean,
    val backupSsid: String?,
    val tetherSupported: Boolean,
)

/**
 * `thermo show` の応答から, WiFi の設定の状態を取り出す.
 *
 * @param reply `thermo show` の応答 (画面の制御は取り除き済み)
 * @return 設定の状態. 応答に `ssid:` の行がなければ (Thermo の機器でなければ) null
 */
fun parseStatus(reply: String): DeviceStatus? {
    val lines = reply.lines()
    val ssid = lines.firstNotNullOfOrNull { valueOf(it, "ssid:") } ?: return null
    val psk = lines.firstNotNullOfOrNull { valueOf(it, "psk:") }
    val backup = lines.firstNotNullOfOrNull { valueOf(it, "tether backup:") }
    return DeviceStatus(
        ssid = ssid,
        hasPsk = psk != null && psk != "(empty)",
        backupSsid = backup?.takeIf { it != "(none)" },
        tetherSupported = backup != null,
    )
}

/**
 * 「項目名: 値」の形の行から, 値を取り出す.
 *
 * @param line 1 行
 * @param label 項目名 (コロンを含む)
 * @return 値. 行が項目名で始まらなければ null
 */
private fun valueOf(
    line: String,
    label: String,
): String? = if (line.startsWith(label)) line.removePrefix(label).trim() else null
