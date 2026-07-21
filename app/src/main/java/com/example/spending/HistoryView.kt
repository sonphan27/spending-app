package com.example.spending

import android.content.Context
import android.graphics.Color
import android.widget.*
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HistoryView {
    fun create(context: Context, dao: SpendingDao, scope: CoroutineScope): ScrollView {
        val historyScrollView = ScrollView(context)
        val historyLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 48)
        }
        historyScrollView.addView(historyLayout)

        val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())

        scope.launch(Dispatchers.IO) {
            dao.getAllSpendings().collect { spendings ->
                withContext(Dispatchers.Main) {
                    historyLayout.removeAllViews()

                    historyLayout.addView(UITheme.createHeaderTextView(context, "Spending History"))

                    if (spendings.isEmpty()) {
                        historyLayout.addView(TextView(context).apply {
                            text = "No recorded spendings yet."
                            setTextColor(UITheme.COLOR_TEXT_MUTED)
                        })
                    } else {
                        for (spending in spendings) {
                            val recordCard = UITheme.createCardLayout(context).apply {
                                isClickable = true
                                isFocusable = true
                                setOnClickListener {
                                    showDetailsDialog(context, spending, dao, scope)
                                }
                            }

                            val topRow = LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                            }

                            val merchantText = TextView(context).apply {
                                text = spending.merchantName
                                textSize = 16f
                                setTextColor(UITheme.COLOR_TEXT_DARK)
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            }

                            val amountText = TextView(context).apply {
                                text = "${spending.totalAmount} ${spending.currency}"
                                textSize = 16f
                                setTextColor(UITheme.COLOR_PRIMARY)
                            }

                            topRow.addView(merchantText)
                            topRow.addView(amountText)

                            val bottomRow = TextView(context).apply {
                                text = "${spending.category} • ${spending.paymentSource}\n${dateFormat.format(Date(spending.dateTime))}"
                                textSize = 12f
                                setTextColor(UITheme.COLOR_TEXT_MUTED)
                                setPadding(0, 12, 0, 0)
                            }

                            recordCard.addView(topRow)
                            recordCard.addView(bottomRow)
                            historyLayout.addView(recordCard)
                        }
                    }
                }
            }
        }
        return historyScrollView
    }

    private fun showDetailsDialog(context: Context, spending: Spending, dao: SpendingDao, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val items = dao.getItemsForSpending(spending.id)

            withContext(Dispatchers.Main) {
                var message = "Merchant: ${spending.merchantName}\n" +
                        "Amount: ${spending.totalAmount} ${spending.currency}\n" +
                        "Category: ${spending.category}\n" +
                        "Payment: ${spending.paymentSource}\n" +
                        "Note: ${spending.note.ifEmpty { "None" }}\n\n" +
                        "--- Itemized List ---\n"

                if (items.isEmpty()) {
                    message += "(No itemized products stored)"
                } else {
                    for (item in items) {
                        message += "• ${item.itemName}: ${item.itemPrice}\n"
                    }
                }

                AlertDialog.Builder(context)
                    .setTitle("Spending Details")
                    .setMessage(message)
                    .setNegativeButton("Delete") { _, _ ->
                        scope.launch(Dispatchers.IO) {
                            dao.deleteSpending(spending)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Deleted record!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setPositiveButton("Close", null)
                    .show()
            }
        }
    }
}