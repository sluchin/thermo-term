/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** [readBootLog] と [removeEcho] の単体テスト. */
class SdLogTest {
    private val device = FakeDevice()
    private val shell = ShellSession(device, maxIdleReads = 3)

    /** `fs cat` で起動のログを読み, 反響を取り除く. */
    @Test
    fun readBootLogReturnsFileContent() =
        runTest {
            device.reply(
                "fs cat $BOOT_LOG_PATH",
                "[00:00:00.100,000] <inf> thermo_node: Thermo Node started\n" +
                    "[00:00:01.000,000] <inf> thermo_ble: Advertising started\n",
            )

            val text = readBootLog(shell)

            assertEquals(listOf("fs cat /SD:/boot.log"), device.written)
            assertEquals(
                "[00:00:00.100,000] <inf> thermo_node: Thermo Node started\n" +
                    "[00:00:01.000,000] <inf> thermo_ble: Advertising started",
                text,
            )
        }

    /** ファイルがないときは機器のエラーの文がそのまま返る. */
    @Test
    fun readBootLogReturnsDeviceError() =
        runTest {
            device.reply("fs cat $BOOT_LOG_PATH", "Failed to open /SD:/boot.log (-2)")

            assertEquals("Failed to open /SD:/boot.log (-2)", readBootLog(shell))
        }

    /** 機器が応答しなければ ShellException. */
    @Test
    fun readBootLogFailsWithoutReply() =
        runTest {
            val silent = ShellSession(FakeDevice(silent = true), maxIdleReads = 2)

            assertFailsWith<ShellException> { readBootLog(silent) }
        }

    /** 反響がない応答はそのまま. */
    @Test
    fun removeEchoKeepsResponseWithoutEcho() {
        assertEquals("abc\n", removeEcho("abc\n", "fs cat x"))
    }
}
