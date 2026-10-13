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
import kotlin.test.assertIs

/** [TetherController] の単体テスト. */
class TetherControllerTest {
    private val device = FakeDevice()
    private val controller = TetherController(ShellSession(device, maxIdleReads = 3))

    /** `thermo show` の応答を登録する. */
    private fun showReply(
        ssid: String,
        backup: String,
    ) = device.reply("thermo show", "ssid: $ssid\npsk: ********\ntether backup: $backup\n")

    /** 状態を読む. */
    @Test
    fun readStatusParsesShow() =
        runTest {
            showReply("home", "(none)")

            assertEquals(DeviceStatus("home", true, null, true), controller.readStatus())
        }

    /** Thermo の機器でなければ, ShellException. */
    @Test
    fun readStatusFailsForOtherDevice() =
        runTest {
            device.reply("thermo show", "command not found")

            assertFailsWith<ShellException> { controller.readStatus() }
        }

    /** 切り替える: tether, apply, show の順に送り, 成功を返す. */
    @Test
    fun tetherSendsCommandsInOrder() =
        runTest {
            device.reply(
                "thermo tether \"my phone\" \"p\\\"w\"",
                "tethering saved (original: home)",
            )
            device.reply("thermo apply", "reconnecting")
            showReply("my phone", "home")

            val result = controller.tether("my phone", "p\"w")

            assertEquals(
                listOf("thermo tether \"my phone\" \"p\\\"w\"", "thermo apply", "thermo show"),
                device.written,
            )
            assertEquals(ActionResult.Success(DeviceStatus("my phone", true, "home", true)), result)
        }

    /** 入力に誤りがあれば, 何も送らない. */
    @Test
    fun tetherRejectsInvalidInput() =
        runTest {
            val result = controller.tether("", "pw")

            assertEquals(ActionResult.Rejected(WifiInputError.SSID_EMPTY), result)
            assertEquals(emptyList(), device.written)
        }

    /** 機器が失敗を返したら, エラーコードつきの Failed. apply は送らない. */
    @Test
    fun tetherReportsDeviceFailure() =
        runTest {
            device.reply(
                "thermo tether \"a\" \"b\"",
                "Failed to switch to the tethering (err -5)",
            )

            val result = controller.tether("a", "b")

            assertEquals(ActionResult.Failed("tether", -5), result)
            assertEquals(1, device.written.size)
        }

    /** apply が失敗したら, Failed("apply"). エラーコードが応答になければ null. */
    @Test
    fun tetherReportsApplyFailure() =
        runTest {
            device.reply("thermo tether \"a\" \"b\"", "tethering saved (original: x)")
            device.reply("thermo apply", "error")

            assertEquals(ActionResult.Failed("apply", null), controller.tether("a", "b"))
        }

    /** 戻す: restore, apply, show の順に送る. */
    @Test
    fun restoreSendsCommandsInOrder() =
        runTest {
            device.reply("thermo restore", "original settings restored")
            device.reply("thermo apply", "reconnecting")
            showReply("home", "(none)")

            val result = controller.restore()

            assertEquals(listOf("thermo restore", "thermo apply", "thermo show"), device.written)
            assertIs<ActionResult.Success>(result)
        }

    /** 戻す設定がなければ, Failed("restore", -2). */
    @Test
    fun restoreReportsMissingBackup() =
        runTest {
            device.reply(
                "thermo restore",
                "Failed to restore the original settings (err -2)",
            )

            assertEquals(ActionResult.Failed("restore", -2), controller.restore())
        }

    /** エラーコードが大きすぎる応答はコードなし. */
    @Test
    fun restoreIgnoresHugeErrorCode() =
        runTest {
            device.reply("thermo restore", "(err 99999999999999999999)")

            assertEquals(ActionResult.Failed("restore", null), controller.restore())
        }

    /** SD カードの起動のログを読む. */
    @Test
    fun readBootLogReadsTheFile() =
        runTest {
            device.reply("fs cat $BOOT_LOG_PATH", "line1\nline2\n")

            assertEquals("line1\nline2", controller.readBootLog())
        }
}
