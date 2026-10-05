package com.example.ordernotifier

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import kotlin.math.roundToInt
import kotlin.random.Random
import com.google.android.material.R as MR

/**
 * One screen with a bottom tab bar (Home / Customize / Timing / Alerts), laid out like the
 * profile, edit-profile and settings pages of the big social apps.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var s: Settings
    private val handler = Handler(Looper.getMainLooper())
    private var binding = false // true while code (not the user) flips switches
    private var currentTab = R.id.nav_home
    private var iconCache: Bitmap? = null
    private var ringAnimator: ObjectAnimator? = null
    private var timelineSeed = Random.nextLong()

    // ---- Shell
    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }
    private val bottomNav by lazy { findViewById<BottomNavigationView>(R.id.bottomNav) }
    private val pages by lazy {
        mapOf(
            R.id.nav_home to findViewById<NestedScrollView>(R.id.pageHome),
            R.id.nav_customize to findViewById<NestedScrollView>(R.id.pageCustomize),
            R.id.nav_timing to findViewById<NestedScrollView>(R.id.pageTiming),
            R.id.nav_alerts to findViewById<NestedScrollView>(R.id.pageAlerts),
        )
    }

    // ---- Home
    private val avatarRing by lazy { findViewById<View>(R.id.avatarRing) }
    private val homeAvatar by lazy { findViewById<ShapeableImageView>(R.id.homeAvatar) }
    private val liveBadge by lazy { findViewById<TextView>(R.id.liveBadge) }
    private val homeName by lazy { findViewById<TextView>(R.id.homeName) }
    private val homeFrom by lazy { findViewById<TextView>(R.id.homeFrom) }
    private val statOrders by lazy { findViewById<TextView>(R.id.statOrders) }
    private val statRevenue by lazy { findViewById<TextView>(R.id.statRevenue) }
    private val statTotal by lazy { findViewById<TextView>(R.id.statTotal) }
    private val startStop by lazy { findViewById<MaterialButton>(R.id.startStop) }
    private val homeStatus by lazy { findViewById<TextView>(R.id.homeStatus) }
    private val recentList by lazy { findViewById<LinearLayout>(R.id.recentList) }
    private val recentEmpty by lazy { findViewById<View>(R.id.recentEmpty) }

    // ---- Customize
    private val customAvatar by lazy { findViewById<ShapeableImageView>(R.id.customAvatar) }
    private val customPreview by lazy { findViewById<View>(R.id.customPreview) }
    private val priceValue by lazy { findViewById<TextView>(R.id.priceValue) }
    private val priceSlider by lazy { findViewById<RangeSlider>(R.id.priceSlider) }
    private val itemsValue by lazy { findViewById<TextView>(R.id.itemsValue) }
    private val itemsSlider by lazy { findViewById<Slider>(R.id.itemsSlider) }

    // ---- Timing
    private val presetChips by lazy { findViewById<ChipGroup>(R.id.presetChips) }
    private val timelineTitle by lazy { findViewById<TextView>(R.id.timelineTitle) }
    private val rateText by lazy { findViewById<TextView>(R.id.rateText) }
    private val timeline by lazy { findViewById<TimelineView>(R.id.timeline) }
    private val gapValue by lazy { findViewById<TextView>(R.id.gapValue) }
    private val gapSlider by lazy { findViewById<RangeSlider>(R.id.gapSlider) }
    private val burstChanceValue by lazy { findViewById<TextView>(R.id.burstChanceValue) }
    private val burstChanceSlider by lazy { findViewById<Slider>(R.id.burstChanceSlider) }
    private val burstMaxValue by lazy { findViewById<TextView>(R.id.burstMaxValue) }
    private val burstMaxSlider by lazy { findViewById<Slider>(R.id.burstMaxSlider) }

    // ---- Alerts
    private val statusIcon by lazy { findViewById<ImageView>(R.id.statusIcon) }
    private val statusTitle by lazy { findViewById<TextView>(R.id.statusTitle) }
    private val statusText by lazy { findViewById<TextView>(R.id.statusText) }
    private val fixSettings by lazy { findViewById<MaterialButton>(R.id.fixSettings) }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (IconUtil.import(this, s, uri)) {
                refreshPictures()
                snack("Picture updated")
            } else {
                snack("Couldn't read that image")
            }
        }
    }

    private val pickAudio = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val error = NotificationSound.import(this, s, uri)
            snack(error ?: "Sound ready: ${s.soundName}")
        }
        // Switch was flipped on but no usable file was chosen: switch it back off.
        if (!NotificationSound.hasFile(s) && s.customSoundOn) s.customSoundOn = false
        refreshAlerts()
    }

    private val askNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) snack("Notifications are blocked. Allow them in Alerts → Open notification settings.")
            refreshAlerts()
        }

    // The service posts orders in the background; keep Home's numbers and feed live.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            Settings.KEY_RECENT -> refreshHome()
            Settings.KEY_NEXT_ORDER -> {
                refreshTextRows()
                refreshPreviews()
            }
        }
    }

    // Keeps the "5m ago" labels in the feed fresh.
    private val clockTick = object : Runnable {
        override fun run() {
            refreshRecent()
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        s = Settings(this)
        OrderNotifications.createChannels(this)
        setContentView(R.layout.activity_main)
        UiStyle.systemBars(this, findViewById(R.id.root))

        if (Build.VERSION.SDK_INT >= 33 && !OrderNotifications.canPost(this)) {
            askNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setupShell()
        setupHome()
        setupCustomize()
        setupTiming()
        setupAlerts()

        val saved = savedInstanceState?.getInt(KEY_TAB, 0)?.takeIf { it in pages.keys }
        val tab = saved ?: tabFromIntent(intent) ?: R.id.nav_home
        bottomNav.selectedItemId = tab
        showTab(tab)

        // After a reboot or crash the saved state says "on" but nothing is running.
        if (s.enabled) SimulatorService.start(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabFromIntent(intent)?.let {
            bottomNav.selectedItemId = it
            showTab(it)
        }
    }

    override fun onStart() {
        super.onStart()
        s.addListener(prefListener)
        refreshAll()
        handler.postDelayed(clockTick, 30_000)
    }

    override fun onStop() {
        s.removeListener(prefListener)
        handler.removeCallbacks(clockTick)
        stopRing()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab)
    }

    // ================================================================ shell

    private fun setupShell() {
        bottomNav.setOnItemSelectedListener {
            showTab(it.itemId)
            true
        }
        // Tapping the current tab again scrolls back to the top.
        bottomNav.setOnItemReselectedListener { pages[it.itemId]?.smoothScrollTo(0, 0) }
        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_reset_stats) {
                confirmResetStats()
                true
            } else {
                false
            }
        }
    }

    private fun showTab(id: Int) {
        currentTab = id
        pages.forEach { (tabId, page) -> page.isVisible = tabId == id }
        toolbar.title = when (id) {
            R.id.nav_customize -> "Customize"
            R.id.nav_timing -> "Timing"
            R.id.nav_alerts -> "Alerts"
            else -> getString(R.string.app_name)
        }
        toolbar.menu.clear()
        if (id == R.id.nav_home) toolbar.inflateMenu(R.menu.menu_home)
        if (id == R.id.nav_alerts) refreshAlerts()
    }

    private fun tabFromIntent(i: Intent?): Int? = when (i?.getStringExtra(EXTRA_TAB)) {
        "home" -> R.id.nav_home
        "customize" -> R.id.nav_customize
        "timing" -> R.id.nav_timing
        "alerts" -> R.id.nav_alerts
        else -> null
    }

    private fun refreshAll() {
        refreshPictures() // also refreshes the previews
        refreshHome()
        refreshTextRows()
        refreshAmounts()
        refreshTiming()
        refreshAlerts()
    }

    // ================================================================ home

    private fun setupHome() {
        startStop.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            setRunning(!s.enabled)
        }
        findViewById<View>(R.id.testNow).setOnClickListener { sendTest() }
        findViewById<View>(R.id.testDelayed).setOnClickListener { sendTestDelayed() }
        homeAvatar.setOnClickListener { showPictureSheet() }
        homeName.setOnClickListener { openEditor(EditField.STORE_NAME) }
        homeFrom.setOnClickListener { openEditor(EditField.FROM_TEXT) }
    }

    private fun setRunning(on: Boolean) {
        s.enabled = on
        if (on) {
            if (!OrderNotifications.canPost(this) && Build.VERSION.SDK_INT >= 33) {
                askNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            SimulatorService.start(this)
            snack("You're live! First order within ${Format.seconds(gapHi())}.")
        } else {
            SimulatorService.stop(this)
            snack("Orders paused")
        }
        refreshHome()
    }

    private fun sendTest() {
        if (!ensureCanPost()) return
        OrderNotifications.postOrder(this, s) // Home refreshes through prefListener
    }

    private fun sendTestDelayed() {
        if (!ensureCanPost()) return
        snack("Order coming in 5 s. Go to your home screen to see the pop-up!")
        val app = applicationContext
        handler.postDelayed({ OrderNotifications.postOrder(app, Settings(app)) }, 5000)
    }

    private fun ensureCanPost(): Boolean {
        if (OrderNotifications.canPost(this)) return true
        if (Build.VERSION.SDK_INT >= 33) {
            askNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
        return false
    }

    private fun refreshHome() {
        val on = s.enabled
        homeName.text = s.storeName
        homeFrom.text = if (s.fromText.isBlank()) "Tap to add a “from” text" else "from ${s.fromText}"
        statOrders.text = Format.count(s.todayOrders)
        statRevenue.text = Format.moneyShort(s.currency, s.todayCents)
        statTotal.text = Format.moneyShort(s.currency, s.totalCents)

        val bg = color(if (on) MR.attr.colorSurfaceContainerHighest else MR.attr.colorPrimary)
        val fg = color(if (on) MR.attr.colorOnSurface else MR.attr.colorOnPrimary)
        startStop.text = if (on) "Stop orders" else "Start orders"
        startStop.setIconResource(if (on) R.drawable.ic_stop else R.drawable.ic_play)
        startStop.backgroundTintList = ColorStateList.valueOf(bg)
        startStop.setTextColor(fg)
        startStop.iconTint = ColorStateList.valueOf(fg)

        homeStatus.text = if (on) {
            "● Live · a new order every ${Format.seconds(gapLo())}–${Format.seconds(gapHi())}"
        } else {
            "Paused · tap Start orders to begin"
        }
        liveBadge.visibility = if (on) View.VISIBLE else View.INVISIBLE
        avatarRing.background = ringDrawable(on)
        if (on) startRing() else stopRing()
        refreshRecent()
    }

    private fun refreshRecent() {
        val orders = s.recentOrders()
        recentList.removeAllViews()
        recentEmpty.isVisible = orders.isEmpty()
        if (orders.isEmpty()) return
        val icon = icon()
        val now = System.currentTimeMillis()
        for (o in orders) {
            val row = layoutInflater.inflate(R.layout.item_recent_order, recentList, false)
            row.findViewById<ImageView>(R.id.roIcon).setImageBitmap(icon)
            row.findViewById<TextView>(R.id.roTitle).text = o.title
            row.findViewById<TextView>(R.id.roSub).text = Format.items(o.items)
            row.findViewById<TextView>(R.id.roAmount).text = "+" + Format.money(s.currency, o.cents)
            row.findViewById<TextView>(R.id.roTime).text = Format.ago(o.time, now)
            recentList.addView(row)
        }
    }

    /** Gradient "live" ring when running, a thin outline when paused. */
    private fun ringDrawable(on: Boolean): Drawable {
        val primary = color(MR.attr.colorPrimary)
        val tertiary = color(MR.attr.colorTertiary)
        val outline = color(MR.attr.colorOutlineVariant)
        val stroke = dp(2)
        val ring = GradientDrawable()
        ring.shape = GradientDrawable.OVAL
        if (on) {
            ring.gradientType = GradientDrawable.SWEEP_GRADIENT
            ring.colors = intArrayOf(primary, tertiary, primary)
        } else {
            ring.setColor(Color.TRANSPARENT)
            ring.setStroke(stroke, outline)
        }
        return ring
    }

    private fun startRing() {
        if (ringAnimator != null) return
        ringAnimator = ObjectAnimator.ofFloat(avatarRing, View.ROTATION, 0f, 360f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopRing() {
        ringAnimator?.cancel()
        ringAnimator = null
        avatarRing.rotation = 0f
    }

    private fun confirmResetStats() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset stats?")
            .setMessage("Clears today's numbers, all-time revenue and the recent orders list.")
            .setPositiveButton("Reset") { _, _ ->
                s.resetStats()
                refreshHome()
                snack("Stats reset")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ================================================================ customize

    private fun setupCustomize() {
        customAvatar.setOnClickListener { showPictureSheet() }
        findViewById<View>(R.id.editPicture).setOnClickListener { showPictureSheet() }

        textRow(R.id.rowStoreName, R.drawable.ic_store, "Store name", EditField.STORE_NAME)
        textRow(R.id.rowFromText, R.drawable.ic_local_mall, "From text", EditField.FROM_TEXT)
        textRow(R.id.rowTitlePrefix, R.drawable.ic_title, "Order title", EditField.TITLE_PREFIX)
        textRow(R.id.rowCurrency, R.drawable.ic_money, "Currency symbol", EditField.CURRENCY)
        textRow(R.id.rowNextOrder, R.drawable.ic_tag, "Next order number", EditField.NEXT_ORDER)

        val steps = Timing.PRICE_STEPS
        val lo = Timing.nearestIndex(steps, minOf(s.minPrice, s.maxPrice).roundToInt())
        val hi = Timing.nearestIndex(steps, maxOf(s.minPrice, s.maxPrice).roundToInt())
        // Keep saved values on the slider's steps so what you see is what's used.
        s.minPrice = steps[lo].toDouble()
        s.maxPrice = steps[hi].toDouble()
        priceSlider.valueFrom = 0f
        priceSlider.valueTo = (steps.size - 1).toFloat()
        priceSlider.stepSize = 1f
        priceSlider.setValues(lo.toFloat(), hi.toFloat())
        priceSlider.setLabelFormatter { v -> s.currency + steps[idx(v, steps.size)] }
        priceSlider.addOnChangeListener { sl, _, fromUser ->
            if (!fromUser) return@addOnChangeListener
            s.minPrice = steps[idx(sl.values[0], steps.size)].toDouble()
            s.maxPrice = steps[idx(sl.values[1], steps.size)].toDouble()
            refreshAmounts()
            refreshPreviews()
            refreshTiming()
        }

        itemsSlider.value = s.maxItems.coerceIn(1, 10).toFloat()
        itemsSlider.setLabelFormatter { v -> v.roundToInt().toString() }
        itemsSlider.addOnChangeListener { _, v, fromUser ->
            if (!fromUser) return@addOnChangeListener
            s.maxItems = v.roundToInt()
            refreshAmounts()
            refreshPreviews()
        }
    }

    private fun textRow(rowId: Int, icon: Int, title: String, field: EditField) {
        val row = findViewById<View>(rowId)
        row.findViewById<ImageView>(R.id.rowIcon).setImageResource(icon)
        row.findViewById<TextView>(R.id.rowTitle).text = title
        row.setOnClickListener { openEditor(field) }
    }

    private fun openEditor(field: EditField) {
        startActivity(Intent(this, EditFieldActivity::class.java).putExtra(EditFieldActivity.EXTRA_FIELD, field.name))
    }

    private fun refreshTextRows() {
        rowValue(R.id.rowStoreName, s.storeName)
        rowValue(R.id.rowFromText, if (s.fromText.isBlank()) "Hidden" else s.fromText)
        rowValue(R.id.rowTitlePrefix, s.titlePrefix)
        rowValue(R.id.rowCurrency, s.currency.ifEmpty { "None" })
        rowValue(R.id.rowNextOrder, s.nextOrder.toString())
    }

    private fun refreshAmounts() {
        val c = s.currency
        val lo = minOf(s.minPrice, s.maxPrice).roundToInt()
        val hi = maxOf(s.minPrice, s.maxPrice).roundToInt()
        priceValue.text = if (lo == hi) "$c$lo" else "$c$lo – $c$hi"
        val items = s.maxItems
        itemsValue.text = if (items <= 1) "1" else "1 – $items"
    }

    private fun refreshPictures() {
        iconCache = null
        val bmp = icon()
        homeAvatar.setImageBitmap(bmp)
        customAvatar.setImageBitmap(bmp)
        refreshPreviews()
        refreshRecent()
    }

    private fun refreshPreviews() {
        val title = s.titlePrefix + s.nextOrder
        val body = OrderNotifications.formatBody(s.currency, Preview.sampleCents(s), Preview.sampleItems(s), s.fromText)
        Preview.bind(customPreview, icon(), "${getString(R.string.app_name)} • ${s.storeName} • now", title, body)
    }

    private fun showPictureSheet() {
        val dialog = BottomSheetDialog(this)
        val sheet = layoutInflater.inflate(R.layout.sheet_picture, null)
        fun option(id: Int, icon: Int, title: String, danger: Boolean, action: () -> Unit) {
            val row = sheet.findViewById<View>(id)
            val iconView = row.findViewById<ImageView>(R.id.rowIcon)
            val titleView = row.findViewById<TextView>(R.id.rowTitle)
            iconView.setImageResource(icon)
            titleView.text = title
            row.findViewById<View>(R.id.rowValue).isVisible = false
            row.findViewById<View>(R.id.rowChevron).isVisible = false
            if (danger) {
                val red = color(MR.attr.colorError)
                titleView.setTextColor(red)
                iconView.imageTintList = ColorStateList.valueOf(red)
            }
            row.setOnClickListener {
                dialog.dismiss()
                action()
            }
        }
        option(R.id.sheetChoose, R.drawable.ic_image, "Choose from gallery", false) { pickImage.launch("image/*") }
        option(R.id.sheetRemove, R.drawable.ic_delete, "Remove picture", true) {
            s.iconFile.delete()
            refreshPictures()
            snack("Picture removed")
        }
        sheet.findViewById<View>(R.id.sheetRemove).isVisible = s.iconFile.exists()
        dialog.setContentView(sheet)
        dialog.show()
    }

    // ================================================================ timing

    private fun setupTiming() {
        for (p in Timing.PRESETS) {
            val chip = layoutInflater.inflate(R.layout.chip_preset, presetChips, false) as Chip
            chip.id = View.generateViewId()
            chip.text = p.label
            chip.tag = p
            chip.setOnClickListener { applyPreset(p) }
            presetChips.addView(chip)
        }

        val steps = Timing.GAP_STEPS
        val lo = Timing.nearestIndex(steps, gapLo())
        val hi = Timing.nearestIndex(steps, gapHi())
        s.minGapSec = steps[lo]
        s.maxGapSec = steps[hi]
        gapSlider.valueFrom = 0f
        gapSlider.valueTo = (steps.size - 1).toFloat()
        gapSlider.stepSize = 1f
        gapSlider.setValues(lo.toFloat(), hi.toFloat())
        gapSlider.setLabelFormatter { v -> Format.seconds(steps[idx(v, steps.size)]) }
        gapSlider.addOnChangeListener { sl, _, fromUser ->
            if (!fromUser) return@addOnChangeListener
            s.minGapSec = steps[idx(sl.values[0], steps.size)]
            s.maxGapSec = steps[idx(sl.values[1], steps.size)]
            onTimingChanged()
        }

        s.burstChance = (s.burstChance.coerceIn(0, 100) / 5) * 5
        s.burstMax = s.burstMax.coerceIn(1, 10)
        burstChanceSlider.value = s.burstChance.toFloat()
        burstChanceSlider.setLabelFormatter { v -> "${v.roundToInt()}%" }
        burstChanceSlider.addOnChangeListener { _, v, fromUser ->
            if (!fromUser) return@addOnChangeListener
            s.burstChance = v.roundToInt()
            onTimingChanged()
        }
        burstMaxSlider.value = s.burstMax.toFloat()
        burstMaxSlider.setLabelFormatter { v -> v.roundToInt().toString() }
        burstMaxSlider.addOnChangeListener { _, v, fromUser ->
            if (!fromUser) return@addOnChangeListener
            s.burstMax = v.roundToInt()
            onTimingChanged()
        }

        findViewById<View>(R.id.reroll).setOnClickListener {
            timelineSeed = Random.nextLong()
            refreshTimeline()
        }
    }

    private fun applyPreset(p: Timing.Preset) {
        s.minGapSec = p.minGap
        s.maxGapSec = p.maxGap
        s.burstChance = p.chance
        s.burstMax = p.burstMax
        val steps = Timing.GAP_STEPS
        gapSlider.setValues(
            Timing.nearestIndex(steps, p.minGap).toFloat(), Timing.nearestIndex(steps, p.maxGap).toFloat()
        )
        burstChanceSlider.value = p.chance.toFloat()
        burstMaxSlider.value = p.burstMax.toFloat()
        onTimingChanged()
    }

    private fun onTimingChanged() {
        refreshTiming()
        refreshHome()
    }

    private fun refreshTiming() {
        gapValue.text = "${Format.seconds(gapLo())} – ${Format.seconds(gapHi())}"
        val chance = s.burstChance
        burstChanceValue.text = if (chance == 0) "Off" else "$chance%"
        val bm = s.burstMax
        burstMaxValue.text = if (bm <= 1) "Single orders" else "Up to $bm orders"
        burstMaxSlider.isEnabled = chance > 0
        for (i in 0 until presetChips.childCount) {
            val chip = presetChips.getChildAt(i) as Chip
            chip.isChecked = (chip.tag as Timing.Preset).matches(s)
        }
        refreshTimeline()
    }

    private fun refreshTimeline() {
        val lo = gapLo()
        val hi = gapHi()
        val window = Timing.windowFor(lo, hi)
        val points = Timing.sample(lo, hi, s.burstChance, s.burstMax, window, Random(timelineSeed))
        timeline.setData(points.map { (it.atSec / window).toFloat() to it.stack }, "+" + Format.seconds(window))
        timeline.contentDescription = "Example: ${points.size} orders in the next ${Format.window(window)}"
        timelineTitle.text = "Next ${Format.window(window)}"

        val perHour = Timing.ordersPerHour(lo, hi, s.burstChance, s.burstMax)
        val rate = if (perHour >= 1) {
            "≈ ${perHour.roundToInt()} orders an hour"
        } else {
            "≈ ${(perHour * 24).roundToInt()} orders a day"
        }
        val perHourCents = (perHour * Preview.sampleCents(s)).toLong()
        rateText.text = "$rate · ≈ ${Format.moneyShort(s.currency, perHourCents)} an hour"
    }

    private fun gapLo() = minOf(s.minGapSec, s.maxGapSec).coerceAtLeast(1)
    private fun gapHi() = maxOf(s.minGapSec, s.maxGapSec).coerceAtLeast(1)

    // ================================================================ alerts

    private fun setupAlerts() {
        switchRow(R.id.rowCustomSound, R.drawable.ic_music_note, "Custom sound") { on ->
            s.customSoundOn = on
            if (on && !NotificationSound.hasFile(s)) pickAudio.launch("audio/*")
            refreshAlerts()
        }
        actionRow(R.id.rowChooseSound, R.drawable.ic_folder, "Choose sound file") { pickAudio.launch("audio/*") }
        actionRow(R.id.rowPlaySound, R.drawable.ic_play, "Play sound") {
            if (!NotificationSound.play(this, s, ignoreToggle = true)) {
                snack(
                    if (NotificationSound.dndActive(this)) "Do Not Disturb is on, so the sound is silenced"
                    else "Choose a sound file first"
                )
            }
        }
        actionRow(R.id.rowRemoveSound, R.drawable.ic_delete, "Remove sound") {
            NotificationSound.remove(s)
            refreshAlerts()
            snack("Sound removed")
        }
        switchRow(R.id.rowSystemSound, R.drawable.ic_volume, "System sound + vibration") { on ->
            s.sound = on
            refreshAlerts()
        }
        switchRow(R.id.rowSinglePicture, R.drawable.ic_chat, "One picture only") { on -> s.singlePicture = on }
        switchRow(R.id.rowKeepAwake, R.drawable.ic_battery, "Keep going with screen off") { on ->
            s.keepAwake = on
            if (s.enabled) SimulatorService.start(this) // re-applies the wake lock
        }
        fixSettings.setOnClickListener { openNotificationSettings() }
    }

    private fun refreshAlerts() {
        val has = NotificationSound.hasFile(s)
        val custom = NotificationSound.isActive(s)
        setSwitch(
            R.id.rowCustomSound, s.customSoundOn,
            if (has) s.soundName.ifBlank { "Custom sound" } else "Off · no file chosen yet"
        )
        rowValue(R.id.rowChooseSound, "MP3, OGG or WAV · a few seconds · max 8 MB")
        rowEnabled(R.id.rowPlaySound, has)
        rowEnabled(R.id.rowRemoveSound, has)
        rowValue(R.id.rowPlaySound, if (has) "Hear how it sounds" else "Choose a file first")
        rowValue(R.id.rowRemoveSound, null)
        setSwitch(
            R.id.rowSystemSound, s.sound,
            if (custom) "Off while your custom sound is on" else "Android's normal notification sound",
            enabled = !custom
        )
        setSwitch(R.id.rowSinglePicture, s.singlePicture, "Chat style: your picture shows once, on the left")
        setSwitch(R.id.rowKeepAwake, s.keepAwake, "Steadier timing when the phone is locked. Uses more battery.")
        refreshStatus()
    }

    /** Checks the things that stop pop-up banners and shows them on the status card. */
    private fun refreshStatus() {
        val nm = getSystemService(NotificationManager::class.java)
        val problems = mutableListOf<String>()
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            problems += "Notifications are blocked for this app."
        } else {
            val ch = nm.getNotificationChannel(OrderNotifications.orderChannelId(s))
            if (ch != null && ch.importance < NotificationManager.IMPORTANCE_HIGH) {
                problems += "Order notifications aren't set to pop on screen."
            }
        }
        if (NotificationSound.dndActive(this)) {
            problems += "Do Not Disturb (or Bedtime / a Focus mode) is on. It hides banners and silences sounds."
        }
        val ok = problems.isEmpty()
        val tint = color(if (ok) MR.attr.colorPrimary else MR.attr.colorError)
        statusIcon.setImageResource(if (ok) R.drawable.ic_check_circle else R.drawable.ic_warning)
        statusIcon.imageTintList = ColorStateList.valueOf(tint)
        statusTitle.text = if (ok) "Pop-up banners are ready" else "Banners might not show"
        statusText.text = if (ok) {
            "New orders slide down from the top of the screen, on the home screen and in other apps."
        } else {
            problems.joinToString("\n") { "• $it" }
        }
        fixSettings.text = if (ok) "Open notification settings" else "Fix it in settings"
        bottomNav.getOrCreateBadge(R.id.nav_alerts).isVisible = !ok
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
            snack("Couldn't open settings. Open them from the system Settings app.")
        }
    }

    // ================================================================ rows & helpers

    private fun switchRow(id: Int, icon: Int, title: String, onChange: (Boolean) -> Unit) {
        val row = findViewById<View>(id)
        row.findViewById<ImageView>(R.id.rowIcon).setImageResource(icon)
        row.findViewById<TextView>(R.id.rowTitle).text = title
        val sw = row.findViewById<MaterialSwitch>(R.id.rowSwitch)
        row.setOnClickListener { sw.isChecked = !sw.isChecked }
        sw.setOnCheckedChangeListener { _, on -> if (!binding) onChange(on) }
    }

    private fun actionRow(id: Int, icon: Int, title: String, onClick: () -> Unit) {
        val row = findViewById<View>(id)
        row.findViewById<ImageView>(R.id.rowIcon).setImageResource(icon)
        row.findViewById<TextView>(R.id.rowTitle).text = title
        row.findViewById<View>(R.id.rowChevron).isVisible = false
        row.setOnClickListener { onClick() }
    }

    private fun setSwitch(id: Int, checked: Boolean, subtitle: String?, enabled: Boolean = true) {
        val row = findViewById<View>(id)
        val sw = row.findViewById<MaterialSwitch>(R.id.rowSwitch)
        binding = true
        sw.isChecked = checked
        binding = false
        sw.isEnabled = enabled
        rowEnabled(id, enabled)
        rowValue(id, subtitle)
    }

    private fun rowValue(id: Int, value: String?) {
        val v = findViewById<View>(id).findViewById<TextView>(R.id.rowValue)
        v.text = value
        v.isVisible = !value.isNullOrEmpty()
    }

    private fun rowEnabled(id: Int, enabled: Boolean) {
        val row = findViewById<View>(id)
        row.isEnabled = enabled
        row.alpha = if (enabled) 1f else 0.45f
    }

    private fun icon(): Bitmap = iconCache ?: IconUtil.load(s).also { iconCache = it }

    private fun idx(v: Float, size: Int) = v.roundToInt().coerceIn(0, size - 1)

    private fun color(attr: Int) = MaterialColors.getColor(bottomNav, attr)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun snack(msg: String) {
        Snackbar.make(findViewById(R.id.root), msg, Snackbar.LENGTH_SHORT).setAnchorView(bottomNav).show()
    }

    companion object {
        /** Optional launch extra: "home", "customize", "timing" or "alerts". */
        const val EXTRA_TAB = "tab"
        private const val KEY_TAB = "tab"
    }
}
