package com.example.ordernotifier

import android.Manifest
import android.app.NotificationManager
import android.content.ColorStateList
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import com.google.android.material.R as MR

class MainActivity : AppCompatActivity() {
    private lateinit var s: Settings
    private val handler = Handler(Looper.getMainLooper())
    private var binding = false // true while we push saved values into widgets

    private val masterCard by lazy { findViewById<MaterialCardView>(R.id.masterCard) }
    private val statusTitle by lazy { findViewById<TextView>(R.id.statusTitle) }
    private val statusSub by lazy { findViewById<TextView>(R.id.statusSub) }
    private val masterSwitch by lazy { findViewById<MaterialSwitch>(R.id.masterSwitch) }

    private val previewIcon by lazy { findViewById<ShapeableImageView>(R.id.previewIcon) }
    private val previewHeader by lazy { findViewById<TextView>(R.id.previewHeader) }
    private val previewTitle by lazy { findViewById<TextView>(R.id.previewTitle) }
    private val previewBody by lazy { findViewById<TextView>(R.id.previewBody) }

    private val pickIcon by lazy { findViewById<ShapeableImageView>(R.id.pickIcon) }
    private val storeName by lazy { findViewById<EditText>(R.id.storeName) }
    private val currency by lazy { findViewById<EditText>(R.id.currency) }

    private val gapLabel by lazy { findViewById<TextView>(R.id.gapLabel) }
    private val gapSlider by lazy { findViewById<RangeSlider>(R.id.gapSlider) }
    private val burstChanceLabel by lazy { findViewById<TextView>(R.id.burstChanceLabel) }
    private val burstChanceSlider by lazy { findViewById<Slider>(R.id.burstChanceSlider) }
    private val burstMaxLabel by lazy { findViewById<TextView>(R.id.burstMaxLabel) }
    private val burstMaxSlider by lazy { findViewById<Slider>(R.id.burstMaxSlider) }

    private val priceLabel by lazy { findViewById<TextView>(R.id.priceLabel) }
    private val priceSlider by lazy { findViewById<RangeSlider>(R.id.priceSlider) }
    private val itemsLabel by lazy { findViewById<TextView>(R.id.itemsLabel) }
    private val itemsSlider by lazy { findViewById<Slider>(R.id.itemsSlider) }
    private val nextOrder by lazy { findViewById<EditText>(R.id.nextOrder) }

    private val avatarSwitch by lazy { findViewById<MaterialSwitch>(R.id.avatarSwitch) }
    private val soundSwitch by lazy { findViewById<MaterialSwitch>(R.id.soundSwitch) }
    private val awakeSwitch by lazy { findViewById<MaterialSwitch>(R.id.awakeSwitch) }
    private val diagText by lazy { findViewById<TextView>(R.id.diagText) }
    private val fixSettings by lazy { findViewById<Button>(R.id.fixSettings) }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (IconUtil.import(this, s, uri)) refreshPictures() else toast("Couldn't read that image")
        }
    }

    private val askNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) toast("Notifications are blocked - allow them in system settings.")
            refreshDiagnostics()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        s = Settings(this)
        OrderNotifications.createChannels(this)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= 33 && !OrderNotifications.canPost(this)) {
            askNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        bindSavedValues()
        attachListeners()
        refreshAll()

        // After a reboot/crash the saved state says "on" but nothing is running.
        if (s.enabled) SimulatorService.start(this)
    }

    override fun onResume() {
        super.onResume()
        // Order number advances in the background; channel settings may have changed.
        binding = true
        if (!nextOrder.hasFocus()) nextOrder.setText(s.nextOrder.toString())
        binding = false
        refreshAll()
    }

    // ---------------------------------------------------------------- binding

    private fun bindSavedValues() {
        binding = true
        masterSwitch.isChecked = s.enabled
        storeName.setText(s.storeName)
        currency.setText(s.currency)
        nextOrder.setText(s.nextOrder.toString())

        val gapLo = s.minGapSec.coerceIn(1, 300)
        val gapHi = s.maxGapSec.coerceIn(1, 300)
        gapSlider.setValues(minOf(gapLo, gapHi).toFloat(), maxOf(gapLo, gapHi).toFloat())
        burstChanceSlider.value = ((s.burstChance.coerceIn(0, 100) / 5) * 5).toFloat()
        burstMaxSlider.value = s.burstMax.coerceIn(1, 10).toFloat()

        val pLo = s.minPrice.toInt().coerceIn(1, 500)
        val pHi = s.maxPrice.toInt().coerceIn(1, 500)
        priceSlider.setValues(minOf(pLo, pHi).toFloat(), maxOf(pLo, pHi).toFloat())
        itemsSlider.value = s.maxItems.coerceIn(1, 10).toFloat()

        avatarSwitch.isChecked = s.singlePicture
        soundSwitch.isChecked = s.sound
        awakeSwitch.isChecked = s.keepAwake

        gapSlider.setLabelFormatter { v -> fmtSec(v.toInt()) }
        burstChanceSlider.setLabelFormatter { v -> "${v.toInt()}%" }
        burstMaxSlider.setLabelFormatter { v -> "${v.toInt()}" }
        priceSlider.setLabelFormatter { v -> "${s.currency}${v.toInt()}" }
        itemsSlider.setLabelFormatter { v -> "${v.toInt()}" }
        binding = false
    }

    private fun attachListeners() {
        masterSwitch.setOnCheckedChangeListener { _, on ->
            if (binding) return@setOnCheckedChangeListener
            s.enabled = on
            if (on) {
                if (Build.VERSION.SDK_INT >= 33 && !OrderNotifications.canPost(this)) {
                    askNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                SimulatorService.start(this)
            } else {
                SimulatorService.stop(this)
            }
            refreshStatus()
        }

        findViewById<Button>(R.id.choosePicture).setOnClickListener { pickImage.launch("image/*") }
        pickIcon.setOnClickListener { pickImage.launch("image/*") }
        findViewById<Button>(R.id.resetPicture).setOnClickListener {
            s.iconFile.delete()
            refreshPictures()
        }

        storeName.onTextChanged { if (!binding) { s.storeName = it; refreshPreview() } }
        currency.onTextChanged { if (!binding) { s.currency = it; refreshPreview(); refreshLabels() } }
        nextOrder.onTextChanged {
            if (!binding) it.toIntOrNull()?.let { n -> s.nextOrder = n; refreshPreview() }
        }

        gapSlider.addOnChangeListener { sl, _, fromUser ->
            if (!fromUser) return@addOnChangeListener
            s.minGapSec = sl.values[0].toInt()
            s.maxGapSec = sl.values[1].toInt()
            refreshLabels(); refreshStatus()
        }
        burstChanceSlider.addOnChangeListener { _, v, fromUser ->
            if (!fromUser) return@addOnChangeListener
            s.burstChance = v.toInt()
            refreshLabels(); refreshStatus()
        }
        burstMaxSlider.addOnChangeListener { _, v, fromUser ->
            if (!fromUser) return@addOnChangeListener
            s.burstMax = v.toInt()
            refreshLabels()
        }
        priceSlider.addOnChangeListener { sl, _, fromUser ->
            if (!fromUser) return@addOnChangeListener
            s.minPrice = sl.values[0].toDouble()
            s.maxPrice = sl.values[1].toDouble()
            refreshLabels(); refreshPreview()
        }
        itemsSlider.addOnChangeListener { _, v, fromUser ->
            if (!fromUser) return@addOnChangeListener
            s.maxItems = v.toInt()
            refreshLabels(); refreshPreview()
        }

        avatarSwitch.setOnCheckedChangeListener { _, on -> if (!binding) s.singlePicture = on }
        soundSwitch.setOnCheckedChangeListener { _, on ->
            if (binding) return@setOnCheckedChangeListener
            s.sound = on
            refreshDiagnostics()
        }
        awakeSwitch.setOnCheckedChangeListener { _, on ->
            if (binding) return@setOnCheckedChangeListener
            s.keepAwake = on
            if (s.enabled) SimulatorService.start(this) // re-applies the wake lock
        }

        findViewById<Button>(R.id.testNow).setOnClickListener { sendTest() }
        findViewById<Button>(R.id.testDelayed).setOnClickListener {
            if (!OrderNotifications.canPost(this)) { toast("Allow notifications first"); return@setOnClickListener }
            toast("Sending in 5 s - go to your home screen now!")
            val app = applicationContext
            handler.postDelayed({ OrderNotifications.postOrder(app, Settings(app)) }, 5000)
        }
        fixSettings.setOnClickListener { openNotificationSettings() }
    }

    private fun sendTest() {
        if (!OrderNotifications.canPost(this)) { toast("Allow notifications first"); return }
        OrderNotifications.postOrder(this, s)
        nextOrder.takeIf { !it.hasFocus() }?.let { binding = true; it.setText(s.nextOrder.toString()); binding = false }
        refreshPreview()
    }

    // ---------------------------------------------------------------- refreshing UI

    private fun refreshAll() {
        refreshPictures()
        refreshLabels()
        refreshStatus()
        refreshDiagnostics()
    }

    private fun refreshPictures() {
        val bmp = IconUtil.load(s)
        pickIcon.setImageBitmap(bmp)
        previewIcon.setImageBitmap(bmp)
        refreshPreview()
    }

    private fun refreshPreview() {
        previewHeader.text = "${getString(R.string.app_name)} • ${s.storeName} • now"
        previewTitle.text = "Order #${s.nextOrder}"
        val price = (s.minPrice + s.maxPrice) / 2
        previewBody.text = OrderNotifications.formatText(s, price, minOf(2, s.maxItems))
    }

    private fun refreshLabels() {
        val lo = gapSlider.values[0].toInt()
        val hi = gapSlider.values[1].toInt()
        gapLabel.text = "Time between orders: ${fmtSec(lo)} – ${fmtSec(hi)}"

        val chance = burstChanceSlider.value.toInt()
        burstChanceLabel.text = if (chance == 0) "Bursts: off" else "Burst chance: $chance%"
        val bm = burstMaxSlider.value.toInt()
        burstMaxLabel.text = if (bm <= 1) "Burst size: none" else "Burst size: up to $bm orders at once"
        burstMaxSlider.isEnabled = chance > 0

        val c = s.currency
        priceLabel.text = "Order total: $c${priceSlider.values[0].toInt()} – $c${priceSlider.values[1].toInt()}"
        val items = itemsSlider.value.toInt()
        itemsLabel.text = if (items == 1) "Items per order: 1" else "Items per order: 1 – $items"
    }

    private fun refreshStatus() {
        val on = s.enabled
        val bgAttr = if (on) MR.attr.colorPrimaryContainer else MR.attr.colorSurfaceVariant
        val fgAttr = if (on) MR.attr.colorOnPrimaryContainer else MR.attr.colorOnSurfaceVariant
        masterCard.setCardBackgroundColor(ColorStateList.valueOf(MaterialColors.getColor(masterCard, bgAttr)))
        val fg = MaterialColors.getColor(masterCard, fgAttr)
        statusTitle.setTextColor(fg)
        statusSub.setTextColor(fg)
        if (on) {
            statusTitle.text = "Running"
            val chance = s.burstChance
            statusSub.text = "An order every ${fmtSec(minOf(s.minGapSec, s.maxGapSec))} – " +
                "${fmtSec(maxOf(s.minGapSec, s.maxGapSec))}" + if (chance > 0) ", $chance% chance of a burst" else ""
        } else {
            statusTitle.text = "Paused"
            statusSub.text = "Flip the switch to start the orders"
        }
    }

    private fun refreshDiagnostics() {
        val nm = getSystemService(NotificationManager::class.java)
        val problems = mutableListOf<String>()
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            problems += "Notifications are blocked for this app."
        } else {
            val ch = nm.getNotificationChannel(OrderNotifications.orderChannelId(s))
            if (ch != null && ch.importance < NotificationManager.IMPORTANCE_HIGH) {
                problems += "This notification channel isn't set to \"Alerting / pop on screen\"."
            }
        }
        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            problems += "Do Not Disturb or a Focus mode is on, which hides pop-up banners."
        }
        if (problems.isEmpty()) {
            diagText.text = "✓ Pop-up banners are allowed. They appear at the top of the screen " +
                "when you're on the home screen or in another app."
            diagText.setTextColor(MaterialColors.getColor(diagText, MR.attr.colorPrimary))
            fixSettings.text = "Open notification settings"
        } else {
            diagText.text = problems.joinToString("\n") { "• $it" }
            diagText.setTextColor(MaterialColors.getColor(diagText, MR.attr.colorError))
            fixSettings.text = "Fix it in settings"
        }
    }

    private fun openNotificationSettings() {
        val intent = if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, OrderNotifications.orderChannelId(s))
        } else {
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        }
        intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            toast("Couldn't open settings - open them from the system Settings app")
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun fmtSec(sec: Int): String =
        if (sec < 60) "${sec}s" else if (sec % 60 == 0) "${sec / 60}m" else "${sec / 60}m ${sec % 60}s"

    private fun EditText.onTextChanged(cb: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(e: Editable?) = cb(e?.toString() ?: "")
        })
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
