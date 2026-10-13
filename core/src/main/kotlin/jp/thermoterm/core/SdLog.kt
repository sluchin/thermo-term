/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.core

/** SD カードに保存された, 起動のログのファイル (ファームウェアの sdlog.c が書く). */
const val BOOT_LOG_PATH = "/SD:/boot.log"

/**
 * SD カードの起動のログを読む (機器のシェルの `fs cat` で, ファイルの中身を出させる).
 *
 * ファイルがない, SD カードがマウントされていないときは, 機器が出すエラーの文が, そのまま返る.
 *
 * @param shell 機器のシェル
 * @return ログの文字列 (入力の反響と, 終わりの空白と改行は取り除き済み)
 * @throws ShellException 機器が応答しない
 */
suspend fun readBootLog(shell: ShellSession): String {
    val command = "fs cat $BOOT_LOG_PATH"
    return removeEcho(shell.execute(command), command).trimEnd()
}

/**
 * シェルの応答の先頭にある, 入力したコマンドの反響 (エコー) を取り除く.
 *
 * @param response シェルの応答
 * @param command 送ったコマンド
 * @return 反響の行を取り除いた応答. 反響がなければ, そのまま
 */
internal fun removeEcho(
    response: String,
    command: String,
): String =
    if (response.startsWith(command)) {
        response.removePrefix(command).removePrefix("\n")
    } else {
        response
    }
