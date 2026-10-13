/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [parseThermoReading] と表示用の文字列の単体テスト. */
class ThermoBleTest {
    /**
     * バイト列を作る.
     *
     * @param values バイトの値 (0 から 255)
     * @return バイト列
     */
    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { values[it].toByte() }

    /** 温度 24.0 ℃ と湿度 49.0 % (ファームウェアの SD_LOG.md の例と同じ). */
    @Test
    fun parsesTemperatureAndHumidity() {
        assertEquals(ThermoReading(24.0, 49.0), parseThermoReading(bytes(0xF0, 0x00, 0xEA, 0x01)))
    }

    /** 負の温度 (-5.0 ℃ は 0xFFCE). */
    @Test
    fun parsesNegativeTemperature() {
        assertEquals(ThermoReading(-5.0, 10.0), parseThermoReading(bytes(0xCE, 0xFF, 0x64, 0x00)))
    }

    /** 湿度が 0xFFFF なら, 湿度なし. */
    @Test
    fun parsesNoHumidity() {
        assertEquals(ThermoReading(23.5, null), parseThermoReading(bytes(0xEB, 0x00, 0xFF, 0xFF)))
    }

    /** 大きさが 4 バイトでなければ, null. */
    @Test
    fun rejectsWrongSize() {
        assertNull(parseThermoReading(bytes(0x01, 0x02, 0x03)))
        assertNull(parseThermoReading(bytes(0x01, 0x02, 0x03, 0x04, 0x05)))
    }

    /** 表示用の文字列. 届いていないときと, 湿度なしは --. */
    @Test
    fun formatsForDisplay() {
        val reading = ThermoReading(23.54, 45.0)

        assertEquals("23.5 °C", formatTemperature(reading))
        assertEquals("45.0 %", formatHumidity(reading))
        assertEquals("--", formatTemperature(null))
        assertEquals("--", formatHumidity(null))
        assertEquals("--", formatHumidity(ThermoReading(1.0, null)))
    }
}
