package tv.blackb0x.droid

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection

object AppleUsb {
    const val VENDOR = 0x05AC
    const val PID_DFU = 0x1227
    const val PID_RECOVERY = 0x1281
    const val PACKET = 0x800
    const val CPID_A4 = 0x8930
    const val BDID_ATV2 = 0x10

    data class Identity(val cpid: Int, val bdid: Int, val ecid: String, val raw: String)

    fun mode(device: UsbDevice): String = when {
        device.vendorId != VENDOR -> "other"
        device.productId == PID_DFU -> "dfu"
        device.productId == PID_RECOVERY -> "recovery"
        else -> "apple-other:%04x".format(device.productId)
    }

    fun identity(device: UsbDevice): Identity? {
        val raw = device.serialNumber ?: return null
        fun field(name: String): String? {
            val i = raw.indexOf(name)
            if (i < 0) return null
            return raw.substring(i + name.length).takeWhile { it != ' ' && it != '\u0000' }
        }
        val cpid = field("CPID:")?.toIntOrNull(16) ?: return null
        val bdid = field("BDID:")?.toIntOrNull(16) ?: -1
        val ecid = field("ECID:") ?: ""
        return Identity(cpid, bdid, ecid, raw)
    }

    fun isAtv2(id: Identity?): Boolean {
        if (id == null) return true
        return id.cpid == CPID_A4 && id.bdid == BDID_ATV2
    }

    fun claim(connection: UsbDeviceConnection, device: UsbDevice): Boolean {
        val claimed = connection.claimInterface(device.getInterface(0), true)
        connection.setConfiguration(device.getConfiguration(0))
        return claimed
    }

    fun dfuSend(connection: UsbDeviceConnection, image: ByteArray, log: (String) -> Unit): Boolean {
        if (image.isEmpty()) {
            log("empty image")
            return false
        }
        val packets = (image.size + PACKET - 1) / PACKET
        for (i in 0 until packets) {
            val off = i * PACKET
            val n = minOf(PACKET, image.size - off)
            val chunk = image.copyOfRange(off, off + n)
            val sent = connection.controlTransfer(0x21, 1, i, 0, chunk, n, 5000)
            if (sent != n) {
                log("dnload block $i sent $sent wanted $n")
                return false
            }
            if (!dfuStatus(connection, log, "block $i")) return false
            if (i % 32 == 0) log("dfu $off / ${image.size}")
        }
        val end = connection.controlTransfer(0x21, 1, packets, 0, ByteArray(0), 0, 1000)
        if (end < 0) {
            log("finish dnload failed $end")
            return false
        }
        dfuStatus(connection, log, "finish")
        dfuStatus(connection, log, "finish-2")
        log("image ${image.size} bytes sent")
        return true
    }

    private fun dfuStatus(connection: UsbDeviceConnection, log: (String) -> Unit, tag: String): Boolean {
        val status = ByteArray(6)
        val n = connection.controlTransfer(0xA1, 3, 0, 0, status, 6, 1000)
        if (n != 6) {
            log("getstatus $tag returned $n")
            return false
        }
        val err = status[0].toInt() and 0xFF
        val state = status[4].toInt() and 0xFF
        if (err != 0) {
            log("dfu err=$err state=$state ($tag)")
            return false
        }
        return true
    }

    fun recoveryCommand(connection: UsbDeviceConnection, command: String): String {
        val out = command.toByteArray(Charsets.UTF_8) + 0
        val w = connection.controlTransfer(0x40, 0, 0, 0, out, out.size, 5000)
        if (w < 0) return "send failed $w"
        val buf = ByteArray(0x1000)
        val r = connection.controlTransfer(0xC0, 0, 0, 0, buf, buf.size, 5000)
        if (r <= 0) return "no reply ($r) after write $w"
        return buf.copyOf(r).toString(Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
    }
}
