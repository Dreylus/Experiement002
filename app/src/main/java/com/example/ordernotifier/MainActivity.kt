package com.example.ordernotifier

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {
    private lateinit var s: Settings
    private lateinit var iconView: ImageView
    private lateinit var enableSwitch: SwitchMaterial
    private val fields = mutableMapOf<String, EditText>()

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            IconUtil.import(this, s, uri)
            refreshIcon()
        }
    }

    private val askNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) toast("Notifications are blocked - allow them in system settings.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = Settings(this)
        OrderNotifications.createChannels(this)
        if (Build.VERSION.SDK_INT >= 33 && !OrderNotifications.canPost(this)) {
            askNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad * 2, pad, pad)
        }

        enableSwitch = SwitchMaterial(this).apply {
            text = "Notifications on"
            textSize = 18f
            isChecked = s.enabled
            setOnCheckedChangeListener { _, on ->
                saveFields()
                s.enabled = on
                if (on) SimulatorService.start(this@MainActivity) else SimulatorService.stop(this@MainActivity)
            }
        }
        col.addView(enableSwitch)

        col.addView(header("Appearance"))
        iconView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
            setOnClickListener { pickImage.launch("image/*") }
        }
        col.addView(iconView)
        refreshIcon()
        col.addView(row(
            button("Choose image") { pickImage.launch("image/*") },
            button("Reset image") { s.iconFile.delete(); refreshIcon() }
        ))
        col.addView(field("name", "Store name", s.storeName, InputType.TYPE_CLASS_TEXT))
        col.addView(field("currency", "Currency symbol", s.currency, InputType.TYPE_CLASS_TEXT))

        col.addView(header("Timing (random each time)"))
        col.addView(field("minGap", "Min seconds between", s.minGapSec.toString(), NUM))
        col.addView(field("maxGap", "Max seconds between", s.maxGapSec.toString(), NUM))
        col.addView(field("burstChance", "Burst chance % (several in a few seconds)", s.burstChance.toString(), NUM))
        col.addView(field("burstMax", "Max notifications in a burst", s.burstMax.toString(), NUM))

        col.addView(header("Order contents"))
        col.addView(field("minPrice", "Min price", s.minPrice.toString(), DEC))
        col.addView(field("maxPrice", "Max price", s.maxPrice.toString(), DEC))
        col.addView(field("maxItems", "Max items per order", s.maxItems.toString(), NUM))
        col.addView(field("nextOrder", "Next order number", s.nextOrder.toString(), NUM))

        col.addView(row(
            button("Save") { saveFields(); restartIfRunning(); toast("Saved") },
            button("Send test") { saveFields(); OrderNotifications.postOrder(this, s) }
        ))

        return ScrollView(this).apply { addView(col) }
    }

    private fun saveFields() {
        fun str(k: String) = fields[k]!!.text.toString().trim()
        s.storeName = str("name")
        s.currency = str("currency")
        s.minGapSec = str("minGap").toIntOrNull()?.coerceAtLeast(1) ?: s.minGapSec
        s.maxGapSec = str("maxGap").toIntOrNull()?.coerceAtLeast(1) ?: s.maxGapSec
        s.burstChance = str("burstChance").toIntOrNull()?.coerceIn(0, 100) ?: s.burstChance
        s.burstMax = str("burstMax").toIntOrNull()?.coerceAtLeast(1) ?: s.burstMax
        s.minPrice = str("minPrice").toDoubleOrNull() ?: s.minPrice
        s.maxPrice = str("maxPrice").toDoubleOrNull() ?: s.maxPrice
        s.maxItems = str("maxItems").toIntOrNull()?.coerceAtLeast(1) ?: s.maxItems
        s.nextOrder = str("nextOrder").toIntOrNull() ?: s.nextOrder
    }

    private fun restartIfRunning() {
        if (s.enabled) SimulatorService.start(this)
    }

    private fun refreshIcon() = iconView.setImageBitmap(IconUtil.load(s))

    // ---- tiny UI helpers ----
    private val NUM = InputType.TYPE_CLASS_NUMBER
    private val DEC = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun header(t: String) = TextView(this).apply {
        text = t; textSize = 16f
        setPadding(0, dp(20), 0, dp(4))
    }

    private fun field(key: String, label: String, value: String, type: Int): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(this).apply { text = label; textSize = 13f })
        val et = EditText(this).apply { inputType = type; setText(value); setSingleLine() }
        fields[key] = et
        box.addView(et)
        return box
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t; setOnClickListener { onClick() }
    }

    private fun row(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        v.forEach { addView(it) }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
