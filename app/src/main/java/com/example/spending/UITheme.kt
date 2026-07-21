package com.example.spending

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*

object UITheme {
    // --- Modern Color Palette ---
    val COLOR_PRIMARY = Color.parseColor("#2563EB")       // Royal Blue
    val COLOR_PRIMARY_DARK = Color.parseColor("#1D4ED8")  // Darker Blue for active state
    val COLOR_BACKGROUND = Color.parseColor("#F8FAFC")    // Soft Off-White
    val COLOR_CARD_BG = Color.parseColor("#FFFFFF")       // Clean White Card
    val COLOR_TEXT_DARK = Color.parseColor("#0F172A")     // Slate Black
    val COLOR_TEXT_MUTED = Color.parseColor("#64748B")    // Slate Gray
    val COLOR_BORDER = Color.parseColor("#E2E8F0")        // Soft Line Border
    val COLOR_DANGER = Color.parseColor("#EF4444")        // Red

    // --- Helper: Dynamic Rounded Backgrounds ---
    fun createRoundedDrawable(
        fillColor: Int,
        cornerRadiusDp: Float = 16f,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidthDp: Int = 0
    ): GradientDrawable {
        val density = 2f // Approximate scaling factor
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = cornerRadiusDp * density
            if (strokeWidthDp > 0) {
                setStroke((strokeWidthDp * density).toInt(), strokeColor)
            }
        }
    }

    // --- Styled Section Titles ---
    fun createHeaderTextView(context: Context, textStr: String): TextView {
        return TextView(context).apply {
            text = textStr
            textSize = 20f
            setTextColor(COLOR_TEXT_DARK)
            setPadding(0, 16, 0, 24)
        }
    }

    // --- Styled Labels ---
    fun createLabelTextView(context: Context, textStr: String): TextView {
        return TextView(context).apply {
            text = textStr
            textSize = 13f
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, 16, 0, 8)
        }
    }

    // --- Styled Input Text Fields ---
    fun createStyledEditText(context: Context, hintText: String, inputTypeVal: Int = InputType.TYPE_CLASS_TEXT): EditText {
        return EditText(context).apply {
            hint = hintText
            setHintTextColor(COLOR_TEXT_MUTED)
            setTextColor(COLOR_TEXT_DARK)
            textSize = 15f
            inputType = inputTypeVal
            background = createRoundedDrawable(COLOR_CARD_BG, 12f, COLOR_BORDER, 1)
            setPadding(32, 28, 32, 28)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 8, 0, 16)
            layoutParams = params
        }
    }

    // --- Styled Primary Action Button ---
    fun createPrimaryButton(context: Context, textStr: String): Button {
        return Button(context).apply {
            text = textStr
            setTextColor(Color.WHITE)
            textSize = 15f
            isAllCaps = false
            background = createRoundedDrawable(COLOR_PRIMARY, 12f)
            setPadding(32, 24, 32, 24)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 24, 0, 16)
            layoutParams = params
        }
    }

    // --- Styled Card Container ---
    fun createCardLayout(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedDrawable(COLOR_CARD_BG, 16f, COLOR_BORDER, 1)
            setPadding(32, 32, 32, 32)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 20)
            layoutParams = params
        }
    }

    // --- Styled AutoComplete Text View ---
    // --- Styled AutoComplete Text View (With Tap-to-Open Dropdown) ---
    fun createStyledAutoCompleteTextView(context: Context, hintText: String): AutoCompleteTextView {
        return AutoCompleteTextView(context).apply {
            hint = hintText
            setHintTextColor(COLOR_TEXT_MUTED)
            setTextColor(COLOR_TEXT_DARK)
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = createRoundedDrawable(COLOR_CARD_BG, 12f, COLOR_BORDER, 1)
            setPadding(32, 28, 32, 28)
            threshold = 1 // Shows suggestions when typing

            // 1. Show all options immediately when the user taps into/focuses the field
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    postDelayed({ showDropDown() }, 100)
                }
            }

            // 2. Show options again if the user taps an already-focused field
            setOnClickListener {
                showDropDown()
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 8, 0, 16)
            layoutParams = params
        }
    }
}