package tv.blackb0x.droid

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var usb: UsbManager
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private val images = ArrayList<ByteArray>()
    private var device: UsbDevice? = null
    private val io = Executors.newSingleThreadExecutor()
    private val actionUsb = "tv.blackb0x.droid.USB_PERMISSION"

    private val pick = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        io.execute {
            images.clear()
            for (uri in uris) {
                val bytes = readUri(uri)
                images.add(bytes)
                log("queued ${bytes.size} bytes")
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != actionUsb) return
            val dev = intent.parcelable<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                device = dev
                show(dev)
            } else {
                log("usb permission denied")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        usb = getSystemService(USB_SERVICE) as UsbManager
        status = findViewById(R.id.status)
        logView = findViewById(R.id.log)
        findViewById<Button>(R.id.pick_payload).setOnClickListener { pick.launch(arrayOf("*/*")) }
        findViewById<Button>(R.id.jailbreak).setOnClickListener { startJailbreak() }
        findViewById<Button>(R.id.send_boot).setOnClickListener {
            io.execute { runCatching { sendRecovery("bootx") }.onFailure { log(it.message ?: "boot failed") } }
        }
        val filter = IntentFilter(actionUsb)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
        loadBundled()
        scan()
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        io.shutdownNow()
        super.onDestroy()
    }

    private fun loadBundled() {
        for (name in listOf("payloads/ibss.img3", "payloads/ramdisk.dmg")) {
            try {
                val bytes = assets.open(name).use { it.readBytes() }
                images.add(bytes)
                log("bundled $name ${bytes.size}")
            } catch (_: Exception) {
            }
        }
    }

    private fun scan() {
        val found = appleDevice()
        if (found == null) {
            status.text = "no device"
            log("waiting for 05ac:1227 or 05ac:1281")
            return
        }
        device = found
        if (!usb.hasPermission(found)) {
            val pending = PendingIntent.getBroadcast(this, 0, Intent(actionUsb), PendingIntent.FLAG_IMMUTABLE)
            usb.requestPermission(found, pending)
        } else show(found)
    }

    private fun show(dev: UsbDevice) {
        val id = AppleUsb.identity(dev)
        val who = if (id == null) "serial hidden" else "CPID:%04X BDID:%02X".format(id.cpid, id.bdid)
        status.text = AppleUsb.mode(dev) + "  " + who
        log(id?.raw ?: AppleUsb.mode(dev))
    }

    private fun appleDevice(): UsbDevice? = usb.deviceList.values.firstOrNull {
        it.vendorId == AppleUsb.VENDOR && (it.productId == AppleUsb.PID_DFU || it.productId == AppleUsb.PID_RECOVERY)
    }

    private fun startJailbreak() {
        val dev = device ?: appleDevice()
        if (dev == null) { scan(); log("no apple device on the bus"); return }
        if (images.isEmpty()) { log("no images. pick ibss then ramdisk, in that order"); return }
        device = dev
        if (!usb.hasPermission(dev)) { scan(); return }
        val id = AppleUsb.identity(dev)
        if (id != null && !AppleUsb.isAtv2(id)) {
            log("not AppleTV2,1. got CPID:%04X BDID:%02X".format(id.cpid, id.bdid))
            return
        }
        io.execute { runChain() }
    }

    private fun runChain() {
        log("running SHAtter")
        val pwned = Shatter.run(
            usb,
            open = {
                val d = waitFor("dfu", 15000) ?: return@run null
                if (!usb.hasPermission(d)) return@run null
                val conn = usb.openDevice(d) ?: return@run null
                d to conn
            },
            log = { log(it) },
            progress = { p -> log("shatter ${p.toInt()}%") },
        )
        if (!pwned) {
            log("SHAtter failed — aborting image chain")
            return
        }
        Thread.sleep(1000)
        for ((index, image) in images.withIndex()) {
            val dev = waitFor("dfu", 30000) ?: run { log("image $index: DFU did not come back"); return }
            if (!usb.hasPermission(dev)) { log("image $index: no permission after re-enum"); return }
            val conn = usb.openDevice(dev) ?: run { log("openDevice failed"); return }
            try {
                AppleUsb.claim(conn, dev)
                log("sending image $index (${image.size})")
                if (!AppleUsb.dfuSend(conn, image) { log(it) }) return
            } finally { conn.close() }
            if (index != images.lastIndex) { log("waiting for re-enumeration"); Thread.sleep(1500) }
        }
        val rec = waitFor("recovery", 30000)
        if (rec != null) { device = rec; log("recovery is up"); sendRecovery("bootx") }
        else log("chain sent. recovery did not enumerate.")
    }

    private fun waitFor(mode: String, timeoutMs: Long): UsbDevice? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val dev = appleDevice()
            if (dev != null && AppleUsb.mode(dev) == mode) {
                device = dev
                if (!usb.hasPermission(dev)) {
                    runOnUiThread {
                        val pending = PendingIntent.getBroadcast(this, 0, Intent(actionUsb), PendingIntent.FLAG_IMMUTABLE)
                        usb.requestPermission(dev, pending)
                    }
                }
                if (usb.hasPermission(dev)) return dev
            }
            Thread.sleep(400)
        }
        return null
    }

    private fun sendRecovery(command: String) {
        val dev = appleDevice() ?: device ?: run { log("no device"); return }
        if (AppleUsb.mode(dev) != "recovery") { log("boot needs recovery, current ${AppleUsb.mode(dev)}"); return }
        if (!usb.hasPermission(dev)) { log("no permission"); return }
        val conn = usb.openDevice(dev) ?: run { log("openDevice failed"); return }
        try {
            AppleUsb.claim(conn, dev)
            log("$command -> " + AppleUsb.recoveryCommand(conn, command))
        } finally { conn.close() }
    }

    private fun readUri(uri: Uri): ByteArray {
        contentResolver.openInputStream(uri).use { input -> return input?.readBytes() ?: ByteArray(0) }
    }

    private fun log(line: String) { runOnUiThread { logView.append(line + "\n") } }
}

private inline fun <reified T> Intent.parcelable(key: String): T? {
    return if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java)
    else @Suppress("DEPRECATION") getParcelableExtra(key)
}
