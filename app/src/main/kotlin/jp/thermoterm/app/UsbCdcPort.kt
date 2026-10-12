/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.thermoterm.app

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import jp.thermoterm.core.SerialPort
import java.io.Closeable
import java.io.IOException

/** Espressif の USB のベンダー ID (0x303A). ESP32C3 の USB シリアル/JTAG が名乗る. */
const val ESPRESSIF_VENDOR_ID = 0x303A

/**
 * USB の CDC ACM (仮想のシリアルポート) の, データの通信路 (バルク転送) を使う [SerialPort].
 *
 * スマートフォンが USB のホストになり, ESP32C3 の USB シリアル/JTAG と通信する.
 * 通信速度などの設定は, 要らない (USB のままの通信). 制御線 (DTR と RTS) は, 動かさない
 * (基板が, 再起動したり, ブートローダに入ったりするため).
 *
 * @property connection 開いた USB の接続
 * @property usbInterface 占有した, データのインターフェース
 * @property input 受信のエンドポイント
 * @property output 送信のエンドポイント
 */
class UsbCdcPort private constructor(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val input: UsbEndpoint,
    private val output: UsbEndpoint,
) : SerialPort,
    Closeable {
    /**
     * データを送る (呼び出したスレッドで, 終わるまで待つ).
     *
     * @param data 送るデータ
     * @throws IOException 送れなかった
     */
    override suspend fun write(data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val size = minOf(output.maxPacketSize, data.size - offset)
            val packet = data.copyOfRange(offset, offset + size)
            val sent = connection.bulkTransfer(output, packet, size, WRITE_TIMEOUT_MILLIS)
            if (sent < 0) {
                throw IOException("USB write failed")
            }
            offset += size
        }
    }

    /**
     * データを受け取る (呼び出したスレッドで, 届くか時間が過ぎるまで待つ).
     *
     * @param timeoutMillis 待つ時間 (ミリ秒)
     * @return 受け取ったデータ. 時間内に何も届かなければ, 空
     */
    override suspend fun read(timeoutMillis: Int): ByteArray {
        val buffer = ByteArray(input.maxPacketSize)
        val received = connection.bulkTransfer(input, buffer, buffer.size, timeoutMillis)
        return if (received > 0) buffer.copyOf(received) else ByteArray(0)
    }

    /** インターフェースを手放して, 接続を閉じる. */
    override fun close() {
        connection.releaseInterface(usbInterface)
        connection.close()
    }

    companion object {
        /** 送信 1 回の待ち時間 (ミリ秒). */
        private const val WRITE_TIMEOUT_MILLIS = 1000

        /**
         * 機器が CDC ACM のデータのインターフェースを持っていれば, 開く.
         *
         * @param manager USB のマネージャ
         * @param device 開く機器 (使う許可を、得てあること)
         * @return 開いたポート. 機器にデータのインターフェースがない, または, 開けなければ null
         */
        fun open(
            manager: UsbManager,
            device: UsbDevice,
        ): UsbCdcPort? {
            val usbInterface = findDataInterface(device)
            val input = usbInterface?.let { findBulk(it, UsbConstants.USB_DIR_IN) }
            val output = usbInterface?.let { findBulk(it, UsbConstants.USB_DIR_OUT) }
            if (usbInterface == null || input == null || output == null) {
                return null
            }
            val connection = manager.openDevice(device)
            return if (connection != null && connection.claimInterface(usbInterface, true)) {
                UsbCdcPort(connection, usbInterface, input, output)
            } else {
                connection?.close()
                null
            }
        }

        /**
         * CDC のデータのインターフェース (クラス 0x0A) を探す.
         *
         * @param device USB の機器
         * @return インターフェース. なければ null
         */
        fun findDataInterface(device: UsbDevice): UsbInterface? =
            (0 until device.interfaceCount)
                .map { device.getInterface(it) }
                .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA }

        /**
         * インターフェースから, 向きの合うバルクのエンドポイントを探す.
         *
         * @param usbInterface インターフェース
         * @param direction 向き (UsbConstants.USB_DIR_IN か USB_DIR_OUT)
         * @return エンドポイント. なければ null
         */
        private fun findBulk(
            usbInterface: UsbInterface,
            direction: Int,
        ): UsbEndpoint? =
            (0 until usbInterface.endpointCount)
                .map { usbInterface.getEndpoint(it) }
                .firstOrNull {
                    it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == direction
                }
    }
}
