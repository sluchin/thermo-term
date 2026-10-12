/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** [describeUsbDevices] の単体テスト. */
class UsbDeviceInfoTest {
    /** 1 台も見えないとき. */
    @Test
    fun describesNoDevice() {
        assertEquals("USB devices seen by this phone: (none)", describeUsbDevices(emptyList()))
    }

    /** 機器の ID とインターフェースのクラスが, 16 進で並ぶ. */
    @Test
    fun describesDevices() {
        val text =
            describeUsbDevices(
                listOf(
                    UsbDeviceInfo(0x303A, 0x1001, listOf(0x02, 0x0A, 0xFF)),
                    UsbDeviceInfo(0x1234, 0x0001, emptyList()),
                ),
            )
        assertEquals(
            "USB devices seen by this phone:\n" +
                "303A:1001 classes=[0x02,0x0A,0xFF]\n" +
                "1234:0001 classes=[]",
            text,
        )
    }
}
