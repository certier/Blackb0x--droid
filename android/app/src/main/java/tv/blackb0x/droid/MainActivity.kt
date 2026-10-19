package tv.blackb0x.droid

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-tap tethered boot for AppleTV2,1:
 * SHAtter → iBSS → iBEC → ramdisk → kernel → bootx
 */
class MainActivity : AppCompatActivity() {
    private lateinit var usb: UsbManager
    private lateinit var status: TextView
    private lateinit var imagesStatus: TextView
    private lateinit var logView: TextView
    private lateinit var jailbreakBtn: Button

    private var ibss: ByteArray? = null
    private var ibec: ByteArray? = null
    private var ramdisk: ByteArray? = null
    private var kernel: ByteArray? = null
    private var device: UsbDevice? = null
    private val running = AtomicBoolean(false)
    private val io = Executors.newSingleThreadExecutor()
    private val actionUsb = "tv.blackb0x.droid.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                actionUsb -> {
                    val dev = intent.parcelable<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device = dev
                        show(dev)
                    } else {
                        setStatus("permission denied", RED)
                        log("usb permission denied — unplug and replug")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val d = intent.parcelable<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (d != null && d.vendorId == AppleUsb.VENDOR) {
                        log("usb attached pid=%04x mode=%s".format(d.productId, AppleUsb.mode(d)))
                    } else {
                        log("usb attached")
                    }
                    scan()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    device = null
                    if (!running.get()) {
                        setStatus("waiting for device", GRAY)
                        jailbreakBtn.isEnabled = false
                        log("usb detached")
                    } else {
                        log("usb detached (expected mid-chain)")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        usb = getSystemService(USB_SERVICE) as UsbManager
        status = findViewById(R.id.status)
        imagesStatus = findViewById(R.id.images_status)
        logView = findViewById(R.id.log)
        jailbreakBtn = findViewById(R.id.jailbreak)
        jailbreakBtn.setOnClickListener { startJailbreak() }

        val filter = IntentFilter().apply {
            addAction(actionUsb)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(usbReceiver, filter)

        loadPayloads()
        scan()
    }

    override fun onResume() {
        super.onResume()
        if (!running.get()) scan()
    }

    override fun onDestroy() {
        try { unregisterReceiver(usbReceiver) } catch (_: Exception) {}
        io.shutdownNow()
        super.onDestroy()
    }

    private fun loadAsset(name: String): ByteArray? = try {
        assets.open("payloads/$name").use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    private fun loadPayloads() {
        ibss = loadAsset("ibss.img3")
        ibec = loadAsset("ibec.img3")
        ramdisk = loadAsset("ramdisk.dmg")
        kernel = loadAsset("kernelcache.img3")
        val parts = mutableListOf<String>()
        if (ibss != null) parts.add("iBSS ${ibss!!.size}")
        if (ibec != null) parts.add("iBEC ${ibec!!.size}")
        if (ramdisk != null) parts.add("RD ${ramdisk!!.size}")
        if (kernel != null) parts.add("K ${kernel!!.size}")
        if (ibss != null && ibec != null && kernel != null) {
            imagesStatus.text = parts.joinToString(" · ")
            imagesStatus.setTextColor(GREEN)
            log("payloads loaded: ${parts.joinToString(", ")}")
        } else {
            imagesStatus.text = "missing payloads"
            imagesStatus.setTextColor(RED)
            log("need ibss + ibec + kernel in assets")
        }
    }

    private fun scan() {
        if (running.get()) return
        val found = appleDevice()
        if (found == null) {
            device = null
            setStatus("waiting for device", GRAY)
            jailbreakBtn.isEnabled = false
            return
        }
        device = found
        if (!usb.hasPermission(found)) {
            setStatus("grant USB permission", YELLOW)
            jailbreakBtn.isEnabled = false
            val pending = PendingIntent.getBroadcast(
                this, 0, Intent(actionUsb), PendingIntent.FLAG_IMMUTABLE
            )
            usb.requestPermission(found, pending)
            return
        }
        show(found)
    }

    private fun show(dev: UsbDevice) {
        val mode = AppleUsb.mode(dev)
        val id = AppleUsb.identity(dev)
        val ready = ibss != null && ibec != null && kernel != null &&
            mode == "dfu" && (id == null || AppleUsb.isAtv2(id))
        val line = when {
            mode != "dfu" -> "$mode  ·  put device in DFU"
            id != null && !AppleUsb.isAtv2(id) -> "wrong device CPID:%04X".format(id.cpid)
            else -> "DFU ready"
        }
        setStatus(line, if (ready) GREEN else YELLOW)
        jailbreakBtn.isEnabled = ready && !running.get()
        if (id != null) log("CPID:%04X BDID:%02X pid=%04x".format(id.cpid, id.bdid, dev.productId))
    }

    private fun appleDevice(): UsbDevice? = usb.deviceList.values.firstOrNull {
        it.vendorId == AppleUsb.VENDOR &&
            (it.productId == AppleUsb.PID_DFU || AppleUsb.isRecoveryPid(it.productId))
    }

    private fun startJailbreak() {
        if (!running.compareAndSet(false, true)) return
        val a = ibss
        val b = ibec
        val k = kernel
        if (a == null || b == null || k == null) {
            running.set(false)
            log("payloads incomplete")
            return
        }
        jailbreakBtn.isEnabled = false
        jailbreakBtn.text = "Working…"
        setStatus("running", YELLOW)
        logView.text = ""
        log("jailbreak started")
        io.execute {
            try {
                runChain(a, b, ramdisk, k)
            } catch (t: Throwable) {
                log("error: ${t.message}")
                setStatus("failed", RED)
            } finally {
                running.set(false)
                runOnUiThread {
                    jailbreakBtn.text = "Jailbreak"
                    scan()
                }
            }
        }
    }

    private fun openMode(want: String, timeoutMs: Long): Pair<UsbDevice, android.hardware.usb.UsbDeviceConnection>? {
        val d = waitDevice(want, timeoutMs) ?: return null
        if (!ensurePermission(d)) return null
        val c = usb.openDevice(d) ?: return null
        if (!AppleUsb.claim(c, d, configure = false)) {
            c.close()
            return null
        }
        return d to c
    }

    /** Wait for device to leave, then return first apple reattach (any mode). */
    private fun waitReconnect(timeoutMs: Long): UsbDevice? {
        val untilGone = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < untilGone) {
            if (appleDevice() == null) break
            Thread.sleep(200)
        }
        log("waiting for reconnect…")
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val d = appleDevice()
            if (d != null) {
                log("reappeared pid=%04x mode=%s".format(d.productId, AppleUsb.mode(d)))
                if (ensurePermission(d)) return d
            }
            Thread.sleep(300)
        }
        return null
    }

    private fun runChain(
        ibssImage: ByteArray,
        ibecImage: ByteArray,
        ramdiskImage: ByteArray?,
        kernelImage: ByteArray,
    ) {
        log("1/5 SHAtter")
        val pwned = Shatter.run(
            usb,
            open = {
                val d = waitDevice("dfu", 20_000) ?: return@run null
                if (!ensurePermission(d)) return@run null
                val conn = usb.openDevice(d) ?: return@run null
                d to conn
            },
            log = { log(it) },
            progress = { p -> log("shatter ${p.toInt()}%") },
        )
        if (!pwned) {
            setStatus("SHAtter failed", RED)
            log("retry: unplug, Menu+Down DFU, try again")
            return
        }

        Thread.sleep(1500)

        log("2/5 send iBSS (${ibssImage.size})")
        val dfuPair = openMode("dfu", 30_000)
        if (dfuPair == null) {
            setStatus("DFU lost after SHAtter", RED)
            return
        }
        try {
            if (!AppleUsb.dfuSend(dfuPair.second, ibssImage) { log(it) }) {
                setStatus("iBSS send failed", RED)
                return
            }
        } finally {
            try { dfuPair.second.close() } catch (_: Exception) {}
        }

        // iBSS should cause disconnect + recovery (or pwned DFU)
        val after = waitReconnect(45_000)
        if (after == null) {
            setStatus("no device after iBSS", RED)
            log("iBSS sent but device never came back")
            return
        }

        log("3/5 send iBEC (${ibecImage.size})")
        var sentIbec = false

        // prefer recovery
        if (AppleUsb.mode(after) == "recovery") {
            if (ensurePermission(after)) {
                val c = usb.openDevice(after)
                if (c != null) {
                    try {
                        if (AppleUsb.claim(c, after, configure = false)) {
                            sentIbec = AppleUsb.recoverySend(c, after, ibecImage) { log(it) }
                        }
                    } finally {
                        try { c.close() } catch (_: Exception) {}
                    }
                }
            }
        }

        if (!sentIbec) {
            val rec = openMode("recovery", 20_000)
            if (rec != null) {
                try {
                    sentIbec = AppleUsb.recoverySend(rec.second, rec.first, ibecImage) { log(it) }
                } finally {
                    try { rec.second.close() } catch (_: Exception) {}
                }
            }
        }

        if (!sentIbec) {
            log("no recovery — iBEC over DFU with clear")
            for (attempt in 1..3) {
                val dfu = openMode("dfu", 12_000) ?: continue
                try {
                    AppleUsb.dfuClear(dfu.second)
                    if (AppleUsb.dfuSend(dfu.second, ibecImage) { log(it) }) {
                        sentIbec = true
                        break
                    }
                    log("iBEC DFU attempt $attempt failed")
                } finally {
                    try { dfu.second.close() } catch (_: Exception) {}
                }
                Thread.sleep(1000)
            }
        }

        if (!sentIbec) {
            setStatus("iBEC send failed", RED)
            log("TV may stay black — iBEC did not land")
            return
        }

        Thread.sleep(3000)
        waitReconnect(20_000)

        if (ramdiskImage != null && ramdiskImage.isNotEmpty()) {
            log("4/5 send ramdisk (${ramdiskImage.size})")
            val rec2 = openMode("recovery", 30_000)
            if (rec2 != null) {
                try {
                    if (AppleUsb.recoverySend(rec2.second, rec2.first, ramdiskImage) { log(it) }) {
                        val reply = AppleUsb.recoveryCommand(rec2.second, "ramdisk")
                        log("ramdisk cmd -> $reply")
                    } else {
                        log("ramdisk send failed — continuing without")
                    }
                } finally {
                    try { rec2.second.close() } catch (_: Exception) {}
                }
            } else {
                log("no recovery for ramdisk — skip")
            }
            Thread.sleep(2000)
        } else {
            log("4/5 no ramdisk asset — skip")
        }

        log("5/5 send kernel (${kernelImage.size})")
        val rec3 = openMode("recovery", 30_000)
        if (rec3 == null) {
            setStatus("no recovery for kernel", RED)
            log("iBEC may have booted alone — check TV")
            return
        }
        try {
            if (!AppleUsb.recoverySend(rec3.second, rec3.first, kernelImage) { log(it) }) {
                setStatus("kernel send failed", RED)
                return
            }
            val boot = AppleUsb.recoveryCommand(rec3.second, "bootx")
            log("bootx -> $boot")
        } finally {
            try { rec3.second.close() } catch (_: Exception) {}
        }

        setStatus("booting", GREEN)
        log("chain done — watch the TV (tethered: redo after every power-off)")
    }

    private fun waitDevice(mode: String, timeoutMs: Long): UsbDevice? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val dev = appleDevice()
            if (dev != null && AppleUsb.mode(dev) == mode) {
                device = dev
                if (usb.hasPermission(dev)) return dev
                runOnUiThread {
                    val pending = PendingIntent.getBroadcast(
                        this, 0, Intent(actionUsb), PendingIntent.FLAG_IMMUTABLE
                    )
                    usb.requestPermission(dev, pending)
                }
                Thread.sleep(800)
                if (usb.hasPermission(dev)) return dev
            }
            Thread.sleep(350)
        }
        return null
    }

    private fun ensurePermission(dev: UsbDevice): Boolean {
        if (usb.hasPermission(dev)) return true
        runOnUiThread {
            val pending = PendingIntent.getBroadcast(
                this, 0, Intent(actionUsb), PendingIntent.FLAG_IMMUTABLE
            )
            usb.requestPermission(dev, pending)
        }
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (usb.hasPermission(dev)) return true
            Thread.sleep(300)
        }
        return usb.hasPermission(dev)
    }

    private fun setStatus(text: String, color: Int) {
        runOnUiThread {
            status.text = text
            status.setTextColor(color)
        }
    }

    private fun log(line: String) {
        runOnUiThread { logView.append(line + "\n") }
    }

    companion object {
        private val GREEN = Color.parseColor("#3DDC84")
        private val RED = Color.parseColor("#E23D3D")
        private val YELLOW = Color.parseColor("#F0C040")
        private val GRAY = Color.parseColor("#8A8A8A")
    }
}

private inline fun <reified T> Intent.parcelable(key: String): T? {
    return if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java)
    else @Suppress("DEPRECATION") getParcelableExtra(key)
}
