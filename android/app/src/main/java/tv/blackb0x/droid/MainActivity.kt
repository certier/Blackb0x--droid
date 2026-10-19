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
    private var payload: ByteArray? = null
    private var device: UsbDevice? = null
    private val io = Executors.newSingleThreadExecutor()
    private val actionUsb = "tv.blackb0x.droid.USB_PERMISSION"

    private val pick = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        io.execute {
            val bytes = readUri(uri)
            payload = bytes
            log("payload ${bytes.size} bytes")
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != actionUsb) return
            val dev = intent.parcelable<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                device = dev
                status.text = AppleUsb.mode(dev) + "  " + dev.deviceName
                log("permission granted ${AppleUsb.mode(dev)}")
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
        findViewById<Button>(R.id.pick_payload).setOnClickListener {
            pick.launch(arrayOf("*/*"))
        }
        findViewById<Button>(R.id.jailbreak).setOnClickListener { startJailbreak() }
        findViewById<Button>(R.id.send_boot).setOnClickListener {
            io.execute { runCatching { sendRecovery("bootx") }.onFailure { log(it.message ?: "boot failed") } }
        }
        val filter = IntentFilter(actionUsb)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        scan()
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        io.shutdownNow()
        super.onDestroy()
    }

    private fun scan() {
        val found = usb.deviceList.values.firstOrNull {
            it.vendorId == AppleUsb.VENDOR &&
                (it.productId == AppleUsb.PID_DFU || it.productId == AppleUsb.PID_RECOVERY)
        }
        if (found == null) {
            status.text = "no device"
            log("waiting for 05ac:1227 or 05ac:1281")
            return
        }
        device = found
        status.text = AppleUsb.mode(found)
        if (!usb.hasPermission(found)) {
            val pending = PendingIntent.getBroadcast(this, 0, Intent(actionUsb), PendingIntent.FLAG_IMMUTABLE)
            usb.requestPermission(found, pending)
        } else {
            log("already permitted ${AppleUsb.mode(found)}")
        }
    }

    private fun startJailbreak() {
        val dev = device
        val image = payload
        if (dev == null) {
            scan()
            log("no apple device on the bus")
            return
        }
        if (image == null) {
            log("pick the baked image first")
            return
        }
        if (!usb.hasPermission(dev)) {
            scan()
            return
        }
        io.execute {
            val conn = usb.openDevice(dev)
            if (conn == null) {
                log("openDevice failed")
                return@execute
            }
            try {
                AppleUsb.claim(conn, dev)
                val mode = AppleUsb.mode(dev)
                log("opened $mode")
                if (mode != "dfu") {
                    log("not in DFU. Menu+Down until the LED strobes.")
                    return@execute
                }
                val ok = AppleUsb.dfuSend(conn, image) { log(it) }
                log(if (ok) "upload finished" else "upload failed")
            } finally {
                conn.close()
            }
        }
    }

    private fun sendRecovery(command: String) {
        val dev = device ?: run { log("no device"); return }
        if (AppleUsb.mode(dev) != "recovery") {
            log("boot command needs recovery, current ${AppleUsb.mode(dev)}")
            return
        }
        val conn = usb.openDevice(dev) ?: run { log("openDevice failed"); return }
        try {
            AppleUsb.claim(conn, dev)
            log("$command -> " + AppleUsb.recoveryCommand(conn, command))
        } finally {
            conn.close()
        }
    }

    private fun readUri(uri: Uri): ByteArray {
        contentResolver.openInputStream(uri).use { input ->
            return input?.readBytes() ?: ByteArray(0)
        }
    }

    private fun log(line: String) {
        runOnUiThread { logView.append(line + "\n") }
    }
}

private inline fun <reified T> Intent.parcelable(key: String): T? {
    return if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java)
    else @Suppress("DEPRECATION") getParcelableExtra(key)
}
