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

/** [ShellSession] の単体テスト. */
class ShellSessionTest {
    /** コマンドの応答を、反響とプロンプトを含めて、返す. */
    @Test
    fun executeReturnsReplyUpToPrompt() =
        runTest {
            val device = FakeDevice()
            device.reply("thermo show", "ssid: home")

            val reply = ShellSession(device).execute("thermo show")

            assertEquals("thermo show\nssid: home\n", reply)
            assertEquals(listOf("thermo show"), device.written)
        }

    /** 溜まっている出力 (起動のログ) は、捨ててから、送る. */
    @Test
    fun executeDiscardsPendingOutput() =
        runTest {
            val device = FakeDevice()
            device.push("boot log\r\nuart:~$ ")
            device.reply("x", "y")

            val reply = ShellSession(device).execute("x")

            assertEquals("x\ny\n", reply)
        }

    /** 文字の途中で切れた受信も、つなげて読む (日本語の SSID). */
    @Test
    fun executeJoinsChunksSplitInsideACharacter() =
        runTest {
            val device = FakeDevice(chunkSize = 2)
            device.reply("show", "ssid: 自宅")

            val reply = ShellSession(device).execute("show")

            assertEquals("show\nssid: 自宅\n", reply)
        }

    /** プロンプトが現れなければ、ShellException. */
    @Test
    fun executeFailsWithoutPrompt() =
        runTest {
            val device = FakeDevice(silent = true)

            assertFailsWith<ShellException> { ShellSession(device, maxIdleReads = 3).execute("x") }
        }
}
