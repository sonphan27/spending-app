package com.example.spending

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ItemSearchView {
    fun create(context: Context, dao: SpendingDao, scope: CoroutineScope): LinearLayout {
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 64)
        }

        // Search Input Bar
        val searchInput = EditText(context).apply {
            hint = "Search items (e.g., EGGS)"
            inputType = InputType.TYPE_CLASS_TEXT
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val searchButton = Button(context).apply { text = "Search" }

        val searchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        searchRow.addView(searchButton)

        // Results Container
        val resultsScrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val resultsLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        resultsScrollView.addView(resultsLayout)

        mainLayout.addView(searchRow)
        mainLayout.addView(resultsScrollView)

        // Function to render results
        fun displayItems(items: List<SpendingItem>) {
            resultsLayout.removeAllViews()
            if (items.isEmpty()) {
                resultsLayout.addView(TextView(context).apply { text = "No items found." })
            } else {
                for (item in items) {
                    val itemCard = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(24, 24, 24, 24)
                        setBackgroundColor(Color.parseColor("#E8F4F8"))
                    }

                    val textInfo = TextView(context).apply {
                        text = "${item.itemName}\nPrice: ${item.itemPrice}"
                        textSize = 16f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val deleteBtn = Button(context).apply {
                        text = "X"
                        setTextColor(Color.RED)
                        setOnClickListener {
                            AlertDialog.Builder(context)
                                .setTitle("Delete Item")
                                .setMessage("Delete '${item.itemName}'?")
                                .setPositiveButton("Delete") { _, _ ->
                                    scope.launch(Dispatchers.IO) {
                                        dao.deleteSpendingItem(item)
                                        val updated = if (searchInput.text.isEmpty()) dao.getRecentItems() else dao.searchItems(searchInput.text.toString())
                                        withContext(Dispatchers.Main) { displayItems(updated) }
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }

                    itemCard.addView(textInfo)
                    itemCard.addView(deleteBtn)
                    resultsLayout.addView(itemCard)
                    resultsLayout.addView(Space(context).apply { minimumHeight = 16 })
                }
            }
        }

        // Default Load: Load top 50 items immediately
        scope.launch(Dispatchers.IO) {
            val initialItems = dao.getRecentItems()
            withContext(Dispatchers.Main) { displayItems(initialItems) }
        }

        // Manual Search Listener
        searchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            scope.launch(Dispatchers.IO) {
                val results = if (query.isEmpty()) dao.getRecentItems() else dao.searchItems(query)
                withContext(Dispatchers.Main) { displayItems(results) }
            }
        }

        return mainLayout
    }
}