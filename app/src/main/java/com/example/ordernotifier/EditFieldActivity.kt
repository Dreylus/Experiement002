package com.example.ordernotifier

import android.graphics.Bitmap
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** The text fields that can be customized, each edited on its own full screen. */
enum class EditField(val title: String, val helper: String, val maxLength: Int, val numeric: Boolean) {
    STORE_NAME("Store name", "Your store's name, shown on Home and in the notification header.", 30, false),
    FROM_TEXT("From text", "Shown after “from” in every order. Leave it empty to hide the “from …” part.", 40, false),
    TITLE_PREFIX("Order title", "Goes right before the order number, like “Order #”.", 20, false),
    CURRENCY("Currency symbol", "Shown in front of every price. Can be empty.", 4, false),
    NEXT_ORDER("Next order number", "The next order uses this number, then counts up.", 9, true),
}

class EditFieldActivity : AppCompatActivity() {
    private lateinit var s: Settings
    private lateinit var field: EditField
    private lateinit var original: String
    private lateinit var icon: Bitmap

    private val input by lazy { findViewById<TextInputEditText>(R.id.input) }
    private val inputLayout by lazy { findViewById<TextInputLayout>(R.id.inputLayout) }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        val f = EditField.values().firstOrNull { it.name == intent.getStringExtra(EXTRA_FIELD) }
        if (f == null) {
            finish()
            return
        }
        field = f
        s = Settings(this)
        icon = IconUtil.load(s)
        setContentView(R.layout.activity_edit_field)
        UiStyle.systemBars(this, findViewById(R.id.root))

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = field.title
        toolbar.setNavigationOnClickListener { attemptClose() }
        toolbar.inflateMenu(R.menu.menu_edit)
        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_save) {
                save()
                true
            } else {
                false
            }
        }

        original = currentValue()
        inputLayout.hint = field.title
        inputLayout.helperText = field.helper
        inputLayout.counterMaxLength = field.maxLength
        input.filters = arrayOf(InputFilter.LengthFilter(field.maxLength))
        input.inputType = if (field.numeric) {
            InputType.TYPE_CLASS_NUMBER
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        if (savedInstanceState == null) input.setText(original)
        input.setSelection(input.text?.length ?: 0)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(t: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(t: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(e: Editable?) {
                inputLayout.error = null
                updatePreview()
            }
        })
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save()
                true
            } else {
                false
            }
        }
        input.requestFocus()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = attemptClose()
        })
        updatePreview()
    }

    private fun currentValue(): String = when (field) {
        EditField.STORE_NAME -> s.storeName
        EditField.FROM_TEXT -> s.fromText
        EditField.TITLE_PREFIX -> s.titlePrefix
        EditField.CURRENCY -> s.currency
        EditField.NEXT_ORDER -> s.nextOrder.toString()
    }

    private fun text() = input.text?.toString() ?: ""

    /** Shows the notification with the text being typed, before it's saved. */
    private fun updatePreview() {
        val t = text()
        val store = if (field == EditField.STORE_NAME) t.trim().ifBlank { Settings.DEFAULT_STORE_NAME } else s.storeName
        val from = if (field == EditField.FROM_TEXT) t else s.fromText
        val prefix = if (field == EditField.TITLE_PREFIX) t.ifBlank { Settings.DEFAULT_TITLE_PREFIX } else s.titlePrefix
        val currency = if (field == EditField.CURRENCY) t else s.currency
        val number = if (field == EditField.NEXT_ORDER) t.toIntOrNull() ?: s.nextOrder else s.nextOrder
        val body = OrderNotifications.formatBody(currency, Preview.sampleCents(s), Preview.sampleItems(s), from)
        Preview.bind(
            findViewById(R.id.editPreview), icon,
            "${getString(R.string.app_name)} • $store • now", prefix + number, body
        )
    }

    private fun save() {
        val t = text()
        when (field) {
            EditField.STORE_NAME -> s.storeName = t.trim()
            EditField.FROM_TEXT -> s.fromText = t.trim()
            EditField.TITLE_PREFIX -> s.titlePrefix = t
            EditField.CURRENCY -> s.currency = t
            EditField.NEXT_ORDER -> {
                val n = t.trim().toIntOrNull()
                if (n == null) {
                    inputLayout.error = "Enter a whole number"
                    return
                }
                s.nextOrder = n
            }
        }
        finish()
    }

    private fun attemptClose() {
        if (text() == original) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Discard changes?")
            .setMessage("Your new ${field.title.lowercase()} won't be saved.")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Keep editing", null)
            .show()
    }

    companion object {
        const val EXTRA_FIELD = "field"
    }
}
