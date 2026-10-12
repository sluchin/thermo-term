/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [cleanShellOutput], [quoteShellArgument], [hasControlCharacter] の単体テスト. */
class ShellTextTest {
    /** 画面の制御 (色) を取り除いて, CR を消す. */
    @Test
    fun cleanShellOutputRemovesAnsiAndCarriageReturns() {
        assertEquals("a\nb\nc", cleanShellOutput("\u001B[1;32ma\u001B[m\r\nb\nc\r"))
    }

    /** 引用符で囲む. */
    @Test
    fun quoteShellArgumentWrapsInQuotes() {
        assertEquals("\"my phone\"", quoteShellArgument("my phone"))
        assertEquals("\"\"", quoteShellArgument(""))
    }

    /** 引用符とバックスラッシュは, バックスラッシュで打ち消す. */
    @Test
    fun quoteShellArgumentEscapesQuoteAndBackslash() {
        assertEquals("\"a\\\"b\\\\c\"", quoteShellArgument("a\"b\\c"))
    }

    /** 制御文字 (改行, DEL) を見つける. */
    @Test
    fun hasControlCharacterDetectsControls() {
        assertTrue(hasControlCharacter("a\nb"))
        assertTrue(hasControlCharacter("a\u007Fb"))
        assertTrue(hasControlCharacter("\u001Fa"))
        assertFalse(hasControlCharacter("a b ~ あ"))
    }
}
