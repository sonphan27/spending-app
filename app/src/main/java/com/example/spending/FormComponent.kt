package com.example.spending

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.View
import android.widget.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FormComponent(val context: Context, val dao: SpendingDao, val scope: CoroutineScope) {

    val view: ScrollView = ScrollView(context)

    // Tracks dynamic UI input rows for items
    private class ItemInputRow(
        val nameInput: EditText,
        val priceInput: EditText,
        val containerLayout: LinearLayout
    )

    private val dynamicItemRows = mutableListOf<ItemInputRow>()

    // Inputs using UITheme styling
    private val merchantInput = UITheme.createStyledEditText(context, "Merchant Name (e.g., West Zone)", InputType.TYPE_CLASS_TEXT)
    private val amountInput = UITheme.createStyledEditText(context, "Total Amount (e.g., 40.80)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
    private val noteInput = UITheme.createStyledEditText(context, "Note (Optional)", InputType.TYPE_CLASS_TEXT)

    private val currencies = arrayOf("AED", "USD", "VND")
    private val currencySpinner = Spinner(context).apply {
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, currencies)
    }

    private val categories = arrayOf("Groceries", "Dining", "Transport", "Bills", "Entertainment", "Other")
    private val categorySpinner = Spinner(context).apply {
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, categories)
    }

    private val sources = arrayOf("Credit Card", "Debit Card", "Cash", "Google Pay", "Bank Transfer")
    private val sourceSpinner = Spinner(context).apply {
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, sources)
    }

    // Dynamic Items Container
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

        // Merchant & Amount
        formLayout.addView(UITheme.createLabelTextView(context, "MERCHANT NAME"))
        formLayout.addView(merchantInput)

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

        // Category & Payment Source Row
        val metaRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val categoryCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 8, 0) }
            addView(UITheme.createLabelTextView(context, "CATEGORY"))
            val frame = FrameLayout(context).apply {
                background = UITheme.createRoundedDrawable(UITheme.COLOR_CARD_BG, 12f, UITheme.COLOR_BORDER, 1)
                setPadding(16, 12, 16, 12)
                addView(categorySpinner)
            }
            addView(frame)
        }

        val sourceCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8, 0, 0, 0) }
            addView(UITheme.createLabelTextView(context, "PAID FROM"))
            val frame = FrameLayout(context).apply {
                background = UITheme.createRoundedDrawable(UITheme.COLOR_CARD_BG, 12f, UITheme.COLOR_BORDER, 1)
                setPadding(16, 12, 16, 12)
                addView(sourceSpinner)
            }
            addView(frame)
        }

        metaRow.addView(categoryCard)
        metaRow.addView(sourceCard)
        formLayout.addView(metaRow)

        // Note Input
        formLayout.addView(UITheme.createLabelTextView(context, "NOTE"))
        formLayout.addView(noteInput)

        // Itemized Products Section
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
    }

    // --- Dynamic UI Generator for Scanned / Added Items ---
    fun fillFromOcr(data: ReceiptParser.ParsedData) {
        merchantInput.setText(data.merchantName)
        amountInput.setText(if (data.totalAmount > 0f) data.totalAmount.toString() else "")
        noteInput.setText("Scanned via OCR")

        itemsContainer.removeAllViews()
        dynamicItemRows.clear()

        for (item in data.items) {
            addItemRow(item.name, item.price.toString())
        }
    }

    // Helper to dynamically add a clean editable Item row
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
        val merchantText = merchantInput.text.toString()

        if (amountText.isEmpty() || merchantText.isEmpty()) {
            Toast.makeText(context, "Please enter Merchant and Total Amount", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch(Dispatchers.IO) {
            val newSpending = Spending(
                merchantName = merchantText,
                category = categorySpinner.selectedItem.toString(),
                totalAmount = amountText.toFloat(),
                currency = currencySpinner.selectedItem.toString(),
                paymentSource = sourceSpinner.selectedItem.toString(),
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
            }
        }
    }
}