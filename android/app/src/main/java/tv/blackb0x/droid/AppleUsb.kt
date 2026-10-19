package tv.blackb0x.droid

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

object AppleUsb {
    const val VENDOR = 0x05AC
    const val PID_DFU = 0x1227
    // libirecovery: 0x1280..0x1283 all recovery
    const val PID_RECOVERY_LO = 0x1280
    const val PID_RECOVERY_HI = 0x1283
    const val PACKET = 0x800
    const val CPID_A4 = 0x8930
    const val BDID_ATV2 = 0x10

    data class Identity(val cpid: Int, val bdid: Int, val ecid: String, val raw: String)

    fun isRecoveryPid(pid: Int): Boolean = pid in PID_RECOVERY_LO..PID_RECOVERY_HI

    fun mode(device: UsbDevice): String = when {
        device.vendorId != VENDOR -> "other"
        device.productId == PID_DFU -> "dfu"
        isRecoveryPid(device.productId) -> "recovery"
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

    fun claim(connection: UsbDeviceConnection, device: UsbDevice, configure: Boolean = false): Boolean {
        if (device.interfaceCount < 1) return false
        val claimed = connection.claimInterface(device.getInterface(0), true)
        if (configure && device.configurationCount > 0) {
            connection.setConfiguration(device.getConfiguration(0))
        }
        return claimed
    }

    /** Clear DFU error state → dfuIDLE. Required after errWRITE (8). */
    fun dfuClear(connection: UsbDeviceConnection) {
        connection.controlTransfer(0x21, 4, 0, 0, ByteArray(0), 0, 1000) // CLRSTATUS
        val status = ByteArray(6)
        repeat(3) {
            connection.controlTransfer(0xA1, 3, 0, 0, status, 6, 1000)
            val state = status[4].toInt() and 0xFF
            if (state == 2 || state == 5) return // dfuIDLE / dfuDNLOAD-IDLE
            connection.controlTransfer(0x21, 4, 0, 0, ByteArray(0), 0, 500)
        }
    }

    fun dfuSend(connection: UsbDeviceConnection, image: ByteArray, log: (String) -> Unit): Boolean {
        if (image.isEmpty()) {
            log("empty image")
            return false
        }
        dfuClear(connection)
        val packets = (image.size + PACKET - 1) / PACKET
        for (i in 0 until packets) {
            val off = i * PACKET
            val n = minOf(PACKET, image.size - off)
            val chunk = image.copyOfRange(off, off + n)
            var sent = connection.controlTransfer(0x21, 1, i, 0, chunk, n, 5000)
            if (sent != n) {
                // one recovery from stalled DFU
                log("dnload block $i sent $sent — clear and retry")
                dfuClear(connection)
                sent = connection.controlTransfer(0x21, 1, i, 0, chunk, n, 5000)
                if (sent != n) {
                    log("dnload block $i sent $sent wanted $n")
                    return false
                }
            }
            if (!dfuStatus(connection, log, "block $i")) {
                log("status fail at $i — clear and retry packet")
                dfuClear(connection)
                sent = connection.controlTransfer(0x21, 1, i, 0, chunk, n, 5000)
                if (sent != n || !dfuStatus(connection, log, "block $i retry")) {
                    return false
                }
            }
            if (i % 32 == 0) log("dfu $off / ${image.size}")
        }
        val end = connection.controlTransfer(0x21, 1, packets, 0, ByteArray(0), 0, 1000)
        if (end < 0) {
            log("finish dnload failed $end")
            return false
        }
        // after final ZLP, device often disconnects — status may fail, treat as ok
        dfuStatus(connection, log, "finish")
        log("image ${image.size} bytes sent")
        return true
    }

    private fun dfuStatus(connection: UsbDeviceConnection, log: (String) -> Unit, tag: String): Boolean {
        val status = ByteArray(6)
        val n = connection.controlTransfer(0xA1, 3, 0, 0, status, 6, 1000)
        if (n != 6) {
            // disconnect mid-finish is normal after iBSS
            if (tag.startsWith("finish")) return true
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

    fun recoverySend(
        connection: UsbDeviceConnection,
        device: UsbDevice,
        data: ByteArray,
        log: (String) -> Unit,
    ): Boolean {
        if (data.isEmpty()) return false
        val iface: UsbInterface = device.getInterface(0)
        var bulkOut: UsbEndpoint? = null
        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.direction == UsbConstants.USB_DIR_OUT &&
                ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK
            ) {
                bulkOut = ep
                break
            }
        }
        if (bulkOut == null) {
            log("no bulk OUT — DFU control path")
            return dfuSend(connection, data, log)
        }
        val packet = minOf(0x8000, bulkOut.maxPacketSize * 32).coerceAtLeast(bulkOut.maxPacketSize)
        var sent = 0
        var part = 0
        while (sent < data.size) {
            val n = minOf(packet, data.size - sent)
            val chunk = ByteArray(n)
            System.arraycopy(data, sent, chunk, 0, n)
            val r = connection.bulkTransfer(bulkOut, chunk, n, 8000)
            if (r != n) {
                log("bulk send part $part r=$r wanted $n")
                return false
            }
            sent += n
            part++
            if (part % 16 == 0) log("recovery $sent / ${data.size}")
        }
        connection.bulkTransfer(bulkOut, ByteArray(0), 0, 1000)
        log("recovery ${data.size} bytes sent")
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
