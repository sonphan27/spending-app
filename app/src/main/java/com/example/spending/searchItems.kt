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
            setPadding(32, 24, 32, 48)
        }

        mainLayout.addView(UITheme.createHeaderTextView(context, "Search Items"))

        // Search Input Bar
        val searchInput = UITheme.createStyledEditText(context, "Search items (e.g., EGGS)", InputType.TYPE_CLASS_TEXT)
        val searchButton = UITheme.createPrimaryButton(context, "Search").apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16, 0, 0, 16) }
        }

        val searchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        searchRow.addView(searchButton)

        val resultsScrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val resultsLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        resultsScrollView.addView(resultsLayout)

        mainLayout.addView(searchRow)
        mainLayout.addView(resultsScrollView)

        fun displayItems(items: List<SpendingItem>) {
            resultsLayout.removeAllViews()
            if (items.isEmpty()) {
                resultsLayout.addView(TextView(context).apply {
                    text = "No items found."
                    setTextColor(UITheme.COLOR_TEXT_MUTED)
                })
            } else {
                for (item in items) {
                    val itemCard = UITheme.createCardLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }

                    val textInfo = TextView(context).apply {
                        text = "${item.itemName}\nPrice: ${item.itemPrice}"
                        textSize = 15f
                        setTextColor(UITheme.COLOR_TEXT_DARK)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val deleteBtn = Button(context).apply {
                        text = "✕"
                        setTextColor(UITheme.COLOR_DANGER)
                        textSize = 14f
                        background = UITheme.createRoundedDrawable(Color.TRANSPARENT, 8f)
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
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            val initialItems = dao.getRecentItems()
            withContext(Dispatchers.Main) { displayItems(initialItems) }
        }

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