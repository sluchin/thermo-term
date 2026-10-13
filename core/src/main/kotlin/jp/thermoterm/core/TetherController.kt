/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.core

/** `thermo tether` が成功したときの応答に含まれる文字列. */
private const val TETHER_OK = "tethering saved"

/** `thermo restore` が成功したときの応答に含まれる文字列. */
private const val RESTORE_OK = "original settings restored"

/** `thermo apply` が成功したときの応答に含まれる文字列. */
private const val APPLY_OK = "reconnecting"

/** 失敗の応答に含まれるエラーコード (`(err -22)` の形) を取り出す式. */
private val ERROR_CODE = Regex("\\(err (-?\\d+)\\)")

/** 操作の結果. */
sealed interface ActionResult {
    /**
     * 成功した.
     *
     * @property status 操作のあとの, 機器の状態
     */
    data class Success(
        val status: DeviceStatus,
    ) : ActionResult

    /**
     * 入力に誤りがあるので, 機器には何も送らなかった.
     *
     * @property error 誤りの種類
     */
    data class Rejected(
        val error: WifiInputError,
    ) : ActionResult

    /**
     * 機器が失敗を返した.
     *
     * @property command 失敗したコマンドの名前 (`tether`, `restore`, `apply`)
     * @property code 機器のエラーコード (負の errno). 応答から分からなければ null
     */
    data class Failed(
        val command: String,
        val code: Int?,
    ) : ActionResult
}

/**
 * ESP32C3 の WiFi の設定を, スマートフォンのテザリングに切り替えたり, 元に戻したりする.
 *
 * 元の SSID とパスワードは, 機器が保存する (`thermo tether` と `thermo restore`). アプリは,
 * パスワードを, 保存しない.
 *
 * @property shell 機器のシェル
 */
class TetherController(
    private val shell: ShellSession,
) {
    /**
     * 機器の WiFi の設定の状態を読む.
     *
     * @return 設定の状態
     * @throws ShellException 機器が応答しない, または, Thermo の機器ではない
     */
    suspend fun readStatus(): DeviceStatus =
        parseStatus(shell.execute("thermo show")) ?: throw ShellException("Not a Thermo device")

    /**
     * SD カードに保存された, 起動のログを読む.
     *
     * @return ログの文字列 (ファイルがないときは機器のエラーの文)
     * @throws ShellException 機器が応答しない
     */
    suspend fun readBootLog(): String = jp.thermoterm.core.readBootLog(shell)

    /**
     * テザリングの SSID とパスワードに切り替えて, 接続をやり直させる.
     *
     * 元の SSID とパスワードは, 機器が保存する (すでに切り替えているときは, 最初の元の設定を残す).
     *
     * @param ssid テザリングの SSID
     * @param psk テザリングのパスワード (空でもよい)
     * @return 結果
     * @throws ShellException 機器が応答しない
     */
    suspend fun tether(
        ssid: String,
        psk: String,
    ): ActionResult {
        val error = validateWifi(ssid, psk)
        if (error != null) {
            return ActionResult.Rejected(error)
        }
        val command = "thermo tether ${quoteShellArgument(ssid)} ${quoteShellArgument(psk)}"
        return run("tether", command, TETHER_OK)
    }

    /**
     * 切り替える前の SSID とパスワードに戻して, 接続をやり直させる.
     *
     * @return 結果. 戻す設定がなければ, Failed
     * @throws ShellException 機器が応答しない
     */
    suspend fun restore(): ActionResult = run("restore", "thermo restore", RESTORE_OK)

    /**
     * 設定を変えるコマンドを送り, 続けて接続のやり直しを依頼して, 機器の状態を読む.
     *
     * @param name コマンドの名前
     * @param command 送るコマンド
     * @param okText 成功の応答に含まれる文字列
     * @return 結果
     */
    private suspend fun run(
        name: String,
        command: String,
        okText: String,
    ): ActionResult {
        val failure =
            failureOf(name, shell.execute(command), okText)
                ?: failureOf("apply", shell.execute("thermo apply"), APPLY_OK)
        return failure ?: ActionResult.Success(readStatus())
    }

    /**
     * 応答が成功を表していなければ, 失敗の結果を作る.
     *
     * @param name コマンドの名前
     * @param reply 応答
     * @param okText 成功の応答に含まれる文字列
     * @return 失敗の結果. 成功していれば null
     */
    private fun failureOf(
        name: String,
        reply: String,
        okText: String,
    ): ActionResult.Failed? =
        if (reply.contains(okText)) null else ActionResult.Failed(name, errorCode(reply))

    /**
     * 失敗の応答から, エラーコードを取り出す.
     *
     * @param reply 応答
     * @return エラーコード. なければ null
     */
    private fun errorCode(reply: String): Int? =
        ERROR_CODE
            .find(reply)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
}
