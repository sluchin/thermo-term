/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.core

/**
 * スマートフォンから見えている USB の機器 1 台の情報.
 *
 * @property vendorId ベンダー ID
 * @property productId プロダクト ID
 * @property interfaceClasses 機器が持つインターフェースのクラス
 */
data class UsbDeviceInfo(
    val vendorId: Int,
    val productId: Int,
    val interfaceClasses: List<Int>,
)

/**
 * 機器が見つからなかったときに出す, つながっている USB の機器の一覧の文字列を作る.
 *
 * 機器が全く見えないのか, 見えているが条件に合わないのかを, 区別できるようにする.
 *
 * @param devices つながっている USB の機器
 * @return 機器ごとに 1 行の文字列. 1 台もなければ, "(none)" の 1 行
 */
fun describeUsbDevices(devices: List<UsbDeviceInfo>): String =
    if (devices.isEmpty()) {
        "USB devices seen by this phone: (none)"
    } else {
        "USB devices seen by this phone:\n" +
            devices.joinToString("\n") {
                val ids = "%04X:%04X".format(it.vendorId, it.productId)
                val classes =
                    it.interfaceClasses.joinToString(",") { cls -> "0x%02X".format(cls) }
                "$ids classes=[$classes]"
            }
    }
