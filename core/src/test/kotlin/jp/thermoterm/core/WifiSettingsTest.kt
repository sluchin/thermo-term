/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [validateWifi], [parseStatus] の単体テスト. */
class WifiSettingsTest {
    /** 正しい入力は誤りなし. パスワードは空でもよい. */
    @Test
    fun validateWifiAcceptsValidInput() {
        assertNull(validateWifi("phone", "password"))
        assertNull(validateWifi("phone", ""))
        assertNull(validateWifi("a".repeat(SSID_MAX_BYTES), "b".repeat(PSK_MAX_BYTES)))
    }

    /** SSID が空. */
    @Test
    fun validateWifiRejectsEmptySsid() {
        assertEquals(WifiInputError.SSID_EMPTY, validateWifi("", "pw"))
    }

    /** 制御文字. */
    @Test
    fun validateWifiRejectsControlCharacters() {
        assertEquals(WifiInputError.CONTROL_CHARACTER, validateWifi("a\nb", "pw"))
        assertEquals(WifiInputError.CONTROL_CHARACTER, validateWifi("ab", "p\rw"))
    }

    /** 長さはバイト数で数える (日本語は 3 バイト). */
    @Test
    fun validateWifiCountsBytes() {
        assertEquals(WifiInputError.SSID_TOO_LONG, validateWifi("a".repeat(SSID_MAX_BYTES + 1), ""))
        assertEquals(WifiInputError.SSID_TOO_LONG, validateWifi("あ".repeat(11), ""))
        assertNull(validateWifi("あ".repeat(10), ""))
        assertEquals(WifiInputError.PSK_TOO_LONG, validateWifi("a", "b".repeat(PSK_MAX_BYTES + 1)))
    }

    /** `thermo show` の応答から, 状態を取り出す (切り替え前). */
    @Test
    fun parseStatusReadsPlainSettings() {
        val status =
            parseStatus(
                "thermo show\nssid: home-ap\npsk: ********\nendpoint: e\n" +
                    "tether backup: (none)\nready to connect: yes\n",
            )

        assertEquals(DeviceStatus("home-ap", true, null, true), status)
    }

    /** 切り替え済みなら, 元の SSID. パスワードが空なら, hasPsk は false. */
    @Test
    fun parseStatusReadsBackup() {
        val status = parseStatus("ssid: phone\npsk: (empty)\ntether backup: home-ap\n")

        assertEquals(DeviceStatus("phone", false, "home-ap", true), status)
    }

    /** 古いファームウェア (tether backup の行がない). */
    @Test
    fun parseStatusWithoutTetherSupport() {
        val status = parseStatus("ssid: home\npsk: ********\n")

        assertFalse(status!!.tetherSupported)
        assertNull(status.backupSsid)
    }

    /** psk の行がなくても読める. */
    @Test
    fun parseStatusWithoutPskLine() {
        assertFalse(parseStatus("ssid: home\n")!!.hasPsk)
    }

    /** ssid の行がなければ null. */
    @Test
    fun parseStatusReturnsNullWithoutSsid() {
        assertNull(parseStatus("hello\n"))
        assertTrue(parseStatus("ssid:\n")!!.ssid.isEmpty())
    }
}
