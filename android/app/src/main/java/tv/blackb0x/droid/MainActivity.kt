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
 * Peak stable one-tap.
 * Plug ATV in DFU → grant USB once → press Jailbreak.
 * Chain: SHAtter → send iBSS → wait. Nothing else to configure.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var usb: UsbManager
    private lateinit var status: TextView
    private lateinit var imagesStatus: TextView
    private lateinit var logView: TextView
    private lateinit var jailbreakBtn: Button

    private var ibss: ByteArray? = null
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
                    log("usb attached")
                    scan()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    device = null
                    if (!running.get()) {
                        setStatus("waiting for device", GRAY)
                        jailbreakBtn.isEnabled = false
                        log("usb detached")
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

    private fun loadPayloads() {
        try {
            ibss = assets.open("payloads/ibss.img3").use { it.readBytes() }
            imagesStatus.text = "iBSS ready  ·  ${ibss!!.size} bytes"
            imagesStatus.setTextColor(GREEN)
            log("payload loaded")
        } catch (e: Exception) {
            ibss = null
            imagesStatus.text = "missing iBSS payload"
            imagesStatus.setTextColor(RED)
            log("no payloads/ibss.img3 in APK")
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
        val ready = ibss != null && mode == "dfu" && (id == null || AppleUsb.isAtv2(id))
        val line = when {
            mode != "dfu" -> "$mode  ·  put device in DFU"
            id != null && !AppleUsb.isAtv2(id) -> "wrong device CPID:%04X".format(id.cpid)
            else -> "DFU ready"
        }
        setStatus(line, if (ready) GREEN else YELLOW)
        jailbreakBtn.isEnabled = ready && !running.get()
        if (id != null) log("CPID:%04X BDID:%02X".format(id.cpid, id.bdid))
    }

    private fun appleDevice(): UsbDevice? = usb.deviceList.values.firstOrNull {
        it.vendorId == AppleUsb.VENDOR &&
            (it.productId == AppleUsb.PID_DFU || it.productId == AppleUsb.PID_RECOVERY)
    }

    private fun startJailbreak() {
        if (!running.compareAndSet(false, true)) return
        val image = ibss
        if (image == null) {
            running.set(false)
            log("no iBSS")
            return
        }
        jailbreakBtn.isEnabled = false
        jailbreakBtn.text = "Working…"
        setStatus("running", YELLOW)
        logView.text = ""
        log("jailbreak started")
        io.execute {
            try {
                runChain(image)
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

    private fun runChain(ibssImage: ByteArray) {
        // 1) SHAtter
        log("1/2 SHAtter")
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

        // device re-enumerates after SHAtter
        Thread.sleep(2000)

        // 2) iBSS only — ATV2 stable path (DeviceManager::sendiBSS)
        log("2/2 send iBSS (${ibssImage.size})")
        val dfu = waitDevice("dfu", 30_000)
        if (dfu == null) {
            setStatus("DFU lost after SHAtter", RED)
            log("device did not reappear in DFU")
            return
        }
        if (!ensurePermission(dfu)) {
            setStatus("need USB permission", RED)
            return
        }
        val conn = usb.openDevice(dfu)
        if (conn == null) {
            setStatus("open failed", RED)
            return
        }
        try {
            AppleUsb.claim(conn, dfu)
            if (!AppleUsb.dfuSend(conn, ibssImage) { log(it) }) {
                setStatus("iBSS send failed", RED)
                return
            }
        } finally {
            conn.close()
        }

        log("waiting for boot…")
        Thread.sleep(3000)
        val rec = waitDevice("recovery", 25_000)
        if (rec != null) {
            setStatus("recovery  ·  done", GREEN)
            log("recovery up — tethered path complete")
            if (ensurePermission(rec)) {
                val c = usb.openDevice(rec)
                if (c != null) {
                    try {
                        AppleUsb.claim(c, rec)
                        log("bootx -> " + AppleUsb.recoveryCommand(c, "bootx"))
                    } finally { c.close() }
                }
            }
        } else {
            // iBSS may have continued without recovery USB enum — still success for SHAtter+iBSS
            setStatus("done", GREEN)
            log("iBSS sent — check the TV")
        }
    }

    private fun waitDevice(mode: String, timeoutMs: Long): UsbDevice? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val dev = appleDevice()
            if (dev != null && AppleUsb.mode(dev) == mode) {
                device = dev
                if (usb.hasPermission(dev)) return dev
                // request and keep waiting
                runOnUiThread {
                    val pending = PendingIntent.getBroadcast(
                        this, 0, Intent(actionUsb), PendingIntent.FLAG_IMMUTABLE
                    )
                    usb.requestPermission(dev, pending)
                }
                // give user a moment to accept
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
