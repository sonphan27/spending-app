package com.example.spending

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.widget.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FormComponent(val context: Context, val dao: SpendingDao, val scope: CoroutineScope) {

    val view: ScrollView = ScrollView(context)

    private class ItemInputRow(
        val nameInput: EditText,
        val priceInput: EditText,
        val containerLayout: LinearLayout
    )

    private val dynamicItemRows = mutableListOf<ItemInputRow>()

    // Default Fallback Suggestions
    private val defaultCategories = listOf("Groceries", "Dining", "Transport", "Bills", "Entertainment", "Other")
    private val defaultSources = listOf("Credit Card", "Cash", "Google Pay", "Bank Transfer")
    private val defaultMerchants = listOf("West Zone Supermarket", "Carrefour", "Lulu Hypermarket")

    // Autocomplete Fields
    private val merchantInput = UITheme.createStyledAutoCompleteTextView(context, "Merchant Name (e.g., West Zone)")
    private val categoryInput = UITheme.createStyledAutoCompleteTextView(context, "Category (e.g., Groceries)")
    private val sourceInput = UITheme.createStyledAutoCompleteTextView(context, "Paid From (e.g., Credit Card)")

    private val amountInput = UITheme.createStyledEditText(context, "Total Amount (e.g., 40.80)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
    private val noteInput = UITheme.createStyledEditText(context, "Note (Optional)", InputType.TYPE_CLASS_TEXT)

    private val currencies = arrayOf("AED", "USD", "VND")
    private val currencySpinner = Spinner(context).apply {
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, currencies)
    }

    private val itemsHeader = UITheme.createLabelTextView(context, "ITEMIZED PRODUCTS")
    private val itemsContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    private val addItemButton = Button(context).apply {
        text = "+ Add Item"
        textSize = 13f
        isAllCaps = false
        setTextColor(UITheme.COLOR_PRIMARY)
        background = UITheme.createRoundedDrawable(Color.TRANSPARENT, 8f, UITheme.COLOR_PRIMARY, 1)
        setPadding(24, 16, 24, 16)
        setOnClickListener { addItemRow("", "") }
    }

    init {
        val formLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 64)
        }

        formLayout.addView(UITheme.createHeaderTextView(context, "New Spending Record"))

        // Merchant Name Input
        formLayout.addView(UITheme.createLabelTextView(context, "MERCHANT NAME"))
        formLayout.addView(merchantInput)

        // Amount & Currency
        formLayout.addView(UITheme.createLabelTextView(context, "TOTAL AMOUNT & CURRENCY"))
        val amountRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val spinnerCard = FrameLayout(context).apply {
            background = UITheme.createRoundedDrawable(UITheme.COLOR_CARD_BG, 12f, UITheme.COLOR_BORDER, 1)
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16, 8, 0, 16) }
            addView(currencySpinner)
        }
        amountRow.addView(amountInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        amountRow.addView(spinnerCard)
        formLayout.addView(amountRow)

        // Category & Source Autocomplete Inputs
        val metaRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val categoryCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 8, 0) }
            addView(UITheme.createLabelTextView(context, "CATEGORY"))
            addView(categoryInput)
        }

        val sourceCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8, 0, 0, 0) }
            addView(UITheme.createLabelTextView(context, "PAID FROM"))
            addView(sourceInput)
        }

        metaRow.addView(categoryCol)
        metaRow.addView(sourceCol)
        formLayout.addView(metaRow)

        // Note
        formLayout.addView(UITheme.createLabelTextView(context, "NOTE"))
        formLayout.addView(noteInput)

        // Items Section
        val itemHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 8)
            addView(itemsHeader, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(addItemButton)
        }
        formLayout.addView(itemHeaderRow)
        formLayout.addView(itemsContainer)

        // Save Button
        val saveButton = UITheme.createPrimaryButton(context, "Save Record")
        saveButton.setOnClickListener { saveToDatabase() }
        formLayout.addView(saveButton)

        view.addView(formLayout)

        // Initial loading of Autocomplete options
        loadAutocompleteOptions()
    }

    // --- Fetches distinct historical values and merges them with defaults ---
    fun loadAutocompleteOptions() {
        scope.launch(Dispatchers.IO) {
            val dbMerchants = dao.getDistinctMerchants()
            val dbCategories = dao.getDistinctCategories()
            val dbSources = dao.getDistinctPaymentSources()

            val allMerchants = (defaultMerchants + dbMerchants).distinct()
            val allCategories = (defaultCategories + dbCategories).distinct()
            val allSources = (defaultSources + dbSources).distinct()

            withContext(Dispatchers.Main) {
                merchantInput.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, allMerchants))
                categoryInput.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, allCategories))
                sourceInput.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, allSources))
            }
        }
    }

    fun fillFromOcr(data: ReceiptParser.ParsedData) {
        merchantInput.setText(data.merchantName, false)
        amountInput.setText(if (data.totalAmount > 0f) data.totalAmount.toString() else "")
        noteInput.setText("Scanned via OCR")

        itemsContainer.removeAllViews()
        dynamicItemRows.clear()

        for (item in data.items) {
            addItemRow(item.name, item.price.toString())
        }
    }

    private fun addItemRow(initialName: String, initialPrice: String) {
        val rowCard = UITheme.createCardLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
        }

        val nameField = UITheme.createStyledEditText(context, "Item Name", InputType.TYPE_CLASS_TEXT).apply {
            setText(initialName)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f).apply { setMargins(0, 0, 8, 0) }
        }

        val priceField = UITheme.createStyledEditText(context, "Price", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply {
            setText(initialPrice)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 8, 0) }
        }

        val rowObj = ItemInputRow(nameField, priceField, rowCard)

        val removeBtn = Button(context).apply {
            text = "✕"
            setTextColor(UITheme.COLOR_DANGER)
            textSize = 14f
            background = UITheme.createRoundedDrawable(Color.TRANSPARENT, 8f)
            setOnClickListener {
                itemsContainer.removeView(rowCard)
                dynamicItemRows.remove(rowObj)
            }
        }

        rowCard.addView(nameField)
        rowCard.addView(priceField)
        rowCard.addView(removeBtn)

        itemsContainer.addView(rowCard)
        dynamicItemRows.add(rowObj)
    }

    private fun saveToDatabase() {
        val amountText = amountInput.text.toString()
        val merchantText = merchantInput.text.toString().trim()
        val categoryText = categoryInput.text.toString().trim().ifEmpty { "Other" }
        val sourceText = sourceInput.text.toString().trim().ifEmpty { "Cash" }

        if (amountText.isEmpty() || merchantText.isEmpty()) {
            Toast.makeText(context, "Please enter Merchant and Total Amount", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch(Dispatchers.IO) {
            val newSpending = Spending(
                merchantName = merchantText,
                category = categoryText,
                totalAmount = amountText.toFloat(),
                currency = currencySpinner.selectedItem.toString(),
                paymentSource = sourceText,
                note = noteInput.text.toString(),
                status = "FINALIZED"
            )
            val insertedSpendingId = dao.insertSpending(newSpending)

            val itemsToSave = mutableListOf<SpendingItem>()
            for (row in dynamicItemRows) {
                val name = row.nameInput.text.toString().trim()
                val price = row.priceInput.text.toString().toFloatOrNull() ?: 0.0f

                if (name.isNotEmpty()) {
                    itemsToSave.add(
                        SpendingItem(
                            spendingId = insertedSpendingId.toInt(),
                            itemName = name,
                            itemPrice = price
                        )
                    )
                }
            }

            if (itemsToSave.isNotEmpty()) {
                dao.insertSpendingItems(itemsToSave)
            }

            withContext(Dispatchers.Main) {
                merchantInput.text.clear()
                amountInput.text.clear()
                noteInput.text.clear()
                itemsContainer.removeAllViews()
                dynamicItemRows.clear()

                Toast.makeText(context, "Saved Record & ${itemsToSave.size} Items!", Toast.LENGTH_SHORT).show()

                // Immediately refresh autocomplete lists so newly added values show up next time!
                loadAutocompleteOptions()
            }
        }
    }
}