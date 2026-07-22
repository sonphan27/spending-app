package com.example.spending

import android.content.Context
import android.graphics.Color
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog
import android.widget.EditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

object HistoryView {

    enum class ViewMode { MONTH, WEEK }

    fun create(context: Context, dao: SpendingDao, scope: CoroutineScope): View {
        val prefs = context.getSharedPreferences("SpendingPrefs", Context.MODE_PRIVATE)

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        var currentMode = ViewMode.MONTH
        var currentCalendar = Calendar.getInstance()

        // UI Elements
        val headerLayout = UITheme.createCardLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val navigationRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnPrev = Button(context).apply { text = "<"; setBackgroundColor(Color.TRANSPARENT) }
        val btnNext = Button(context).apply { text = ">"; setBackgroundColor(Color.TRANSPARENT) }
        val periodText = TextView(context).apply {
            textSize = 18f
            setTextColor(UITheme.COLOR_TEXT_DARK)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        navigationRow.addView(btnPrev)
        navigationRow.addView(periodText)
        navigationRow.addView(btnNext)

        val budgetStatusText = TextView(context).apply {
            textSize = 22f
            setPadding(0, 16, 0, 8)
            gravity = Gravity.CENTER
        }

        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        val btnToggleMode = Button(context).apply {
            text = "Switch to Week View"
            textSize = 12f
            isAllCaps = false
            setTextColor(UITheme.COLOR_PRIMARY)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnRefresh = Button(context).apply {
            text = "Refresh Data"
            textSize = 12f
            isAllCaps = false
            setTextColor(UITheme.COLOR_PRIMARY)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        actionRow.addView(btnToggleMode)
        actionRow.addView(btnRefresh)

        headerLayout.addView(navigationRow)
        headerLayout.addView(budgetStatusText)
        headerLayout.addView(actionRow)

        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 100)
        }
        val scrollView = ScrollView(context).apply { addView(listContainer) }

        mainLayout.addView(headerLayout)
        mainLayout.addView(scrollView)

        // Rendering Logic
        fun updateUI() {
            if (currentMode == ViewMode.MONTH) {
                periodText.text = DateFormat.format("MMMM yyyy", currentCalendar)
            } else {
                val week = currentCalendar.get(Calendar.WEEK_OF_YEAR)
                val year = currentCalendar.get(Calendar.YEAR)
                periodText.text = "Week $week, $year"
            }

            scope.launch(Dispatchers.IO) {
                val allRecords: List<Spending> = try {
                    val rawData = dao.getAllSpendings()
                    when (rawData) {
                        is List<*> -> rawData as List<Spending>
                        is androidx.lifecycle.LiveData<*> -> (rawData.value as? List<Spending>) ?: emptyList()
                        is kotlinx.coroutines.flow.Flow<*> -> rawData.first() as? List<Spending> ?: emptyList()
                        else -> emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }

                val filteredRecords = allRecords.filter { spending ->
                    val recordTime = try {
                        val ts = spending.javaClass.getMethod("getTimestamp").invoke(spending) as? Long
                        ts ?: System.currentTimeMillis()
                    } catch (e: Exception) { System.currentTimeMillis() }

                    val recordCal = Calendar.getInstance().apply { timeInMillis = recordTime }

                    if (currentMode == ViewMode.MONTH) {
                        recordCal.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH) &&
                                recordCal.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR)
                    } else {
                        recordCal.get(Calendar.WEEK_OF_YEAR) == currentCalendar.get(Calendar.WEEK_OF_YEAR) &&
                                recordCal.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR)
                    }
                }

                val totalSpent = filteredRecords.sumOf { it.totalAmount.toDouble() }.toFloat()

                var target = prefs.getFloat("MONTHLY_TARGET", 2000f)
                if (currentMode == ViewMode.WEEK) target /= 4

                withContext(Dispatchers.Main) {
                    budgetStatusText.text = String.format("%.2f / %.0f AED", totalSpent, target)

                    val colorGreen = Color.parseColor("#4CAF50")
                    val colorYellow = Color.parseColor("#FF9800")
                    val colorRed = Color.parseColor("#F44336")

                    if (target > 0f) {
                        if (totalSpent > target) {
                            budgetStatusText.setTextColor(colorRed)
                            headerLayout.background = UITheme.createRoundedDrawable(Color.WHITE, 16f, colorRed, 4)
                        } else if (target - totalSpent <= (if (currentMode == ViewMode.MONTH) 1000f else 250f)) {
                            budgetStatusText.setTextColor(colorYellow)
                            headerLayout.background = UITheme.createRoundedDrawable(Color.WHITE, 16f, colorYellow, 4)
                        } else {
                            budgetStatusText.setTextColor(colorGreen)
                            headerLayout.background = UITheme.createRoundedDrawable(Color.WHITE, 16f, colorGreen, 4)
                        }
                    } else {
                        budgetStatusText.setTextColor(UITheme.COLOR_TEXT_DARK)
                        headerLayout.background = UITheme.createRoundedDrawable(Color.WHITE, 16f, UITheme.COLOR_BORDER, 2)
                    }

                    listContainer.removeAllViews()
                    if (filteredRecords.isEmpty()) {
                        listContainer.addView(TextView(context).apply {
                            text = "No records found for this period."
                            gravity = Gravity.CENTER
                            setPadding(0, 48, 0, 0)
                        })
                    } else {
                        // Group Records by Day
                        val recordsWithTime = filteredRecords.map { item ->
                            val ts = try {
                                val fetchedTs = item.javaClass.getMethod("getTimestamp").invoke(item) as? Long ?: 0L
                                if (fetchedTs > 0) fetchedTs else System.currentTimeMillis()
                            } catch(e:Exception){ System.currentTimeMillis() }
                            Pair(item, ts)
                        }

                        val groupedByDay = recordsWithTime.groupBy {
                            val cal = Calendar.getInstance().apply { timeInMillis = it.second }
                            DateFormat.format("EEEE, MMM dd, yyyy", cal).toString()
                        }

                        val sortedDays = groupedByDay.keys.sortedByDescending { dateStr ->
                            groupedByDay[dateStr]?.firstOrNull()?.second ?: 0L
                        }

                        for (dayStr in sortedDays) {
                            val dayLabel = TextView(context).apply {
                                text = dayStr
                                textSize = 14f
                                setTextColor(UITheme.COLOR_PRIMARY)
                                setPadding(8, 32, 8, 8)
                                paint.isFakeBoldText = true
                            }
                            listContainer.addView(dayLabel)

                            val dayRecords = groupedByDay[dayStr]?.sortedByDescending { it.second } ?: emptyList()
                            for ((item, _) in dayRecords) {
                                val card = UITheme.createCardLayout(context).apply {
                                    setPadding(24, 24, 24, 24)
                                    layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    ).apply { setMargins(0, 0, 0, 16) }
                                }

                                val titleStr = "${item.merchantName} - ${item.totalAmount} AED"
                                card.addView(TextView(context).apply {
                                    text = titleStr
                                    textSize = 16f
                                    setTextColor(UITheme.COLOR_TEXT_DARK)
                                })
                                card.addView(TextView(context).apply {
                                    text = "Category: ${item.category}"
                                    textSize = 13f
                                    setTextColor(UITheme.COLOR_TEXT_MUTED)
                                })

                                // Make card interactive!
                                card.setOnClickListener {
                                    showOptionsDialog(context, item, dao, scope) { updateUI() }
                                }

                                listContainer.addView(card)
                            }
                        }
                    }
                }
            }
        }

        btnPrev.setOnClickListener {
            if (currentMode == ViewMode.MONTH) currentCalendar.add(Calendar.MONTH, -1)
            else currentCalendar.add(Calendar.WEEK_OF_YEAR, -1)
            updateUI()
        }

        btnNext.setOnClickListener {
            if (currentMode == ViewMode.MONTH) currentCalendar.add(Calendar.MONTH, 1)
            else currentCalendar.add(Calendar.WEEK_OF_YEAR, 1)
            updateUI()
        }

        btnToggleMode.setOnClickListener {
            if (currentMode == ViewMode.MONTH) {
                currentMode = ViewMode.WEEK
                btnToggleMode.text = "Switch to Month"
            } else {
                currentMode = ViewMode.MONTH
                btnToggleMode.text = "Switch to Week"
            }
            updateUI()
        }

        btnRefresh.setOnClickListener { updateUI() }

        mainLayout.tag = Runnable { updateUI() }

        updateUI()
        return mainLayout
    }

    private fun showOptionsDialog(context: Context, item: Spending, dao: SpendingDao, scope: CoroutineScope, onRefresh: () -> Unit) {
        val options = arrayOf("Edit Record", "Delete Record")
        AlertDialog.Builder(context)
            .setTitle(item.merchantName)
            .setItems(options) { _, which ->
                if (which == 0) {
                    showEditDialog(context, item, dao, scope, onRefresh)
                } else {
                    AlertDialog.Builder(context)
                        .setTitle("Delete?")
                        .setMessage("Are you sure you want to permanently delete this record?")
                        .setPositiveButton("Delete") { _, _ ->
                            scope.launch(Dispatchers.IO) {
                                var success = false
                                try {
                                    dao.javaClass.getMethod("delete", item.javaClass).invoke(dao, item)
                                    success = true
                                } catch (e: Exception) {
                                    try {
                                        dao.javaClass.getMethod("deleteSpending", item.javaClass).invoke(dao, item)
                                        success = true
                                    } catch (e2: Exception) {}
                                }
                                withContext(Dispatchers.Main) {
                                    if (success) onRefresh()
                                    else Toast.makeText(context, "Error: Add '@Delete fun delete(spending: Spending)' to your DAO.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .show()
    }

    private fun showEditDialog(context: Context, item: Spending, dao: SpendingDao, scope: CoroutineScope, onRefresh: () -> Unit) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val merchantInput = EditText(context).apply {
            setText(item.merchantName)
            hint = "Merchant Name"
        }
        val categoryInput = EditText(context).apply {
            setText(item.category)
            hint = "Category"
        }
        val totalInput = EditText(context).apply {
            setText(item.totalAmount.toString())
            hint = "Total Amount"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        layout.addView(merchantInput)
        layout.addView(categoryInput)
        layout.addView(totalInput)

        AlertDialog.Builder(context)
            .setTitle("Edit Record")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newMerchant = merchantInput.text.toString()
                val newCat = categoryInput.text.toString()
                val newTotal = totalInput.text.toString().toFloatOrNull() ?: item.totalAmount

                var updated = false
                try {
                    item.javaClass.getMethod("setMerchantName", String::class.java).invoke(item, newMerchant)
                    item.javaClass.getMethod("setCategory", String::class.java).invoke(item, newCat)

                    try {
                        item.javaClass.getMethod("setTotalAmount", java.lang.Float.TYPE).invoke(item, newTotal)
                    } catch (e: Exception) {
                        item.javaClass.getMethod("setTotalAmount", java.lang.Double.TYPE).invoke(item, newTotal.toDouble())
                    }
                    updated = true
                } catch (e: Exception) {
                    Toast.makeText(context, "Warning: To save edits, properties in Spending.kt must be declared as 'var'!", Toast.LENGTH_LONG).show()
                }

                if (updated) {
                    scope.launch(Dispatchers.IO) {
                        var success = false
                        try {
                            dao.javaClass.getMethod("update", item.javaClass).invoke(dao, item)
                            success = true
                        } catch (e: Exception) {
                            try {
                                dao.javaClass.getMethod("updateSpending", item.javaClass).invoke(dao, item)
                                success = true
                            } catch (e2: Exception) {}
                        }
                        withContext(Dispatchers.Main) {
                            if (success) onRefresh()
                            else Toast.makeText(context, "Error: Add '@Update fun update(spending: Spending)' to your DAO.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}