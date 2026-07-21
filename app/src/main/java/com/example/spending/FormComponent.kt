package com.example.spending

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FormComponent(val context: Context, val dao: SpendingDao, val scope: CoroutineScope) {

    val view: ScrollView = ScrollView(context)

    // Data structure to keep track of the dynamic UI input rows
    private class ItemInputRow(
        val nameInput: EditText,
        val priceInput: EditText,
        val containerLayout: LinearLayout
    )

    private val dynamicItemRows = mutableListOf<ItemInputRow>()

    private val merchantInput = EditText(context).apply { hint = "Merchant Name" }
    private val amountInput = EditText(context).apply {
        hint = "Total Amount"
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }
    private val noteInput = EditText(context).apply { hint = "Note (Optional)" }

    private val currencies = arrayOf("AED", "USD", "VND")
    private val currencySpinner = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, currencies) }

    private val categories = arrayOf("Groceries", "Dining", "Transport", "Bills", "Entertainment", "Other")
    private val categorySpinner = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, categories) }

    private val sources = arrayOf("Credit Card", "Cash", "Google Pay", "Bank Transfer")
    private val sourceSpinner = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, sources) }

    // Dynamic container where scanned receipt item rows will be added
    private val itemsSectionHeader = TextView(context).apply {
        text = "Itemized Products"
        textSize = 18f
        setPadding(0, 32, 0, 16)
        visibility = View.GONE // Hidden until OCR scans items
    }
    private val itemsContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    init {
        val formLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 64)
        }

        val saveButton = Button(context).apply {
            text = "Save Record"
            setPadding(0, 32, 0, 32)
            setOnClickListener { saveToDatabase() }
        }

        formLayout.addView(merchantInput)
        formLayout.addView(amountInput)
        formLayout.addView(TextView(context).apply { text = "Currency"; setPadding(0, 24, 0, 0) })
        formLayout.addView(currencySpinner)
        formLayout.addView(TextView(context).apply { text = "Category"; setPadding(0, 24, 0, 0) })
        formLayout.addView(categorySpinner)
        formLayout.addView(TextView(context).apply { text = "Paid From"; setPadding(0, 24, 0, 0) })
        formLayout.addView(sourceSpinner)
        formLayout.addView(noteInput)

        // Inject the dynamic items section right into the form
        formLayout.addView(itemsSectionHeader)
        formLayout.addView(itemsContainer)

        formLayout.addView(Space(context).apply { minimumHeight = 64 })
        formLayout.addView(saveButton)
        view.addView(formLayout)
    }

    // --- Dynamic UI Generator for Scanned Items ---
    fun fillFromOcr(data: ReceiptParser.ParsedData) {
        merchantInput.setText(data.merchantName)
        amountInput.setText(data.totalAmount.toString())
        noteInput.setText("Scanned via OCR")

        // Clear previous item rows if re-scanning
        itemsContainer.removeAllViews()
        dynamicItemRows.clear()

        if (data.items.isNotEmpty()) {
            itemsSectionHeader.visibility = View.VISIBLE

            for (item in data.items) {
                addItemRow(item.name, item.price.toString())
            }
        } else {
            itemsSectionHeader.visibility = View.GONE
        }
    }

    // Helper method to dynamically create an editable Item + Price row
    private fun addItemRow(initialName: String, initialPrice: String) {
        val rowLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }

        val nameField = EditText(context).apply {
            hint = "Item Name"
            setText(initialName)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
        }

        val priceField = EditText(context).apply {
            hint = "Price"
            setText(initialPrice)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        rowLayout.addView(nameField)
        rowLayout.addView(priceField)

        itemsContainer.addView(rowLayout)
        dynamicItemRows.add(ItemInputRow(nameField, priceField, rowLayout))
    }

    private fun saveToDatabase() {
        val amountText = amountInput.text.toString()
        val merchantText = merchantInput.text.toString()

        if (amountText.isEmpty() || merchantText.isEmpty()) {
            Toast.makeText(context, "Missing Merchant or Amount", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch(Dispatchers.IO) {
            // 1. Insert parent spending record
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

            // 2. Read values directly from the user's updated input fields!
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

            // 3. Save all items linked to the inserted spending ID
            if (itemsToSave.isNotEmpty()) {
                dao.insertSpendingItems(itemsToSave)
            }

            withContext(Dispatchers.Main) {
                // Reset form
                merchantInput.text.clear()
                amountInput.text.clear()
                noteInput.text.clear()
                itemsContainer.removeAllViews()
                dynamicItemRows.clear()
                itemsSectionHeader.visibility = View.GONE

                Toast.makeText(context, "Saved Record & ${itemsToSave.size} Items!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}