package com.example.spending

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

object SettingsView {
    fun create(context: Context): View {
        val prefs = context.getSharedPreferences("SpendingPrefs", Context.MODE_PRIVATE)

        val scrollView = ScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 64)
        }

        layout.addView(UITheme.createHeaderTextView(context, "App Settings"))

        layout.addView(UITheme.createLabelTextView(context, "MONTHLY SPENDING TARGET (AED)"))

        val targetInput = UITheme.createStyledEditText(context, "e.g., 2000", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply {
            val currentTarget = prefs.getFloat("MONTHLY_TARGET", 2000f)
            setText(if (currentTarget > 0) currentTarget.toString() else "")
        }

        layout.addView(targetInput)

        val saveBtn = UITheme.createPrimaryButton(context, "Save Settings").apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 32, 0, 0) }

            setOnClickListener {
                val targetVal = targetInput.text.toString().toFloatOrNull() ?: 0f
                prefs.edit().putFloat("MONTHLY_TARGET", targetVal).apply()
                Toast.makeText(context, "Target saved: $targetVal", Toast.LENGTH_SHORT).show()

                // Clear focus and hide keyboard roughly
                targetInput.clearFocus()
            }
        }
        layout.addView(saveBtn)

        scrollView.addView(layout)
        return scrollView
    }
}