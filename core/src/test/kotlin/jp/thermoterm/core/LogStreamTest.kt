/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** [streamLog] と [completeUtf8Length] の単体テスト. */
@OptIn(ExperimentalCoroutinesApi::class)
class LogStreamTest {
    /** ログを読んで、色と CR を取り除いて渡す. 取り消すと止まる. */
    @Test
    fun streamLogDeliversCleanText() =
        runTest {
            val device = FakeDevice()
            device.push("\u001B[1;32m[00:00:01] <inf> app: hello\u001B[m\r\n")
            val received = StringBuilder()

            val job = backgroundScope.launch { streamLog(device) { received.append(it) } }
            runCurrent()
            device.push("next\r\n")
            testScheduler.advanceTimeBy(50)
            runCurrent()
            job.cancel()

            assertEquals("[00:00:01] <inf> app: hello\nnext\n", received.toString())
        }

    /** 文字の途中で切れた受信は、続きが届いてから渡す (日本語). */
    @Test
    fun streamLogJoinsSplitCharacters() =
        runTest {
            val device = FakeDevice(chunkSize = 1)
            device.push("温度😀\n")
            val received = StringBuilder()

            val job = backgroundScope.launch { streamLog(device) { received.append(it) } }
            runCurrent()
            job.cancel()

            assertEquals("温度😀\n", received.toString())
        }

    /** 色の制御だけの受信は、空の文字列を渡さない. */
    @Test
    fun streamLogSkipsEmptyText() =
        runTest {
            val device = FakeDevice()
            device.push("\u001B[m")
            var calls = 0

            val job = backgroundScope.launch { streamLog(device) { calls++ } }
            runCurrent()
            job.cancel()

            assertEquals(0, calls)
        }

    /** 完全な文字だけの長さを返す. 不正なバイトは、そのまま通す. */
    @Test
    fun completeUtf8LengthCutsIncompleteTail() {
        assertEquals(0, completeUtf8Length(ByteArray(0)))
        assertEquals(1, completeUtf8Length("a".toByteArray()))
        val accent = "é".toByteArray()
        assertEquals(0, completeUtf8Length(accent.copyOf(1)))
        assertEquals(2, completeUtf8Length(accent))
        val kanji = "温".toByteArray()
        assertEquals(0, completeUtf8Length(kanji.copyOf(1)))
        assertEquals(0, completeUtf8Length(kanji.copyOf(2)))
        assertEquals(3, completeUtf8Length(kanji))
        val emoji = "😀".toByteArray()
        assertEquals(0, completeUtf8Length(emoji.copyOf(3)))
        assertEquals(4, completeUtf8Length(emoji))
        assertEquals(
            5,
            completeUtf8Length(byteArrayOf(0x61, 0x62, 0xC3.toByte(), 0xA9.toByte(), 0x63)),
        )
        assertEquals(2, completeUtf8Length(byteArrayOf(0x61, 0xFF.toByte())))
        assertEquals(
            3,
            completeUtf8Length(byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte())),
        )
    }
}
