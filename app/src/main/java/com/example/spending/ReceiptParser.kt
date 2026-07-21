package com.example.spending

object ReceiptParser {

    // A simple data class to hold an individual item and its price
    data class ParsedItem(val name: String, val price: Float)

    data class ParsedData(
        val merchantName: String,
        val totalAmount: Float,
        val items: List<ParsedItem>,
        val rawText: String
    )

    fun parse(rawText: String): ParsedData {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // 1. Extract Merchant (Usually the first line)
        var merchant = "Unknown Merchant"
        if (lines.isNotEmpty()) {
            merchant = lines[0]
        }

        // 2. Extract Total Amount
        var total = 0.0f
        val totalIndex = lines.indexOfFirst { it.equals("Total", ignoreCase = true) }
        if (totalIndex != -1 && totalIndex + 1 < lines.size) {
            val totalStr = lines[totalIndex + 1].replace(Regex("[^0-9.]"), "")
            total = totalStr.toFloatOrNull() ?: 0.0f
        }

        // --- 3. Extract Item Names ---
        val names = mutableListOf<String>()
        val itemHeaderIndex = lines.indexOfFirst { it.contains("Item Description", ignoreCase = true) }
        val amountHeaderIndex = lines.indexOfFirst { it.contains("Amout", ignoreCase = true) || it.contains("Amount", ignoreCase = true) }

        if (itemHeaderIndex != -1) {
            // Read until we hit the amount column or the end
            val endLimit = if (amountHeaderIndex != -1) amountHeaderIndex else lines.size
            for (i in itemHeaderIndex + 1 until endLimit) {
                val line = lines[i]
                // Filter out common noise lines found in the item column
                if (line.contains("Bill Amount", ignoreCase = true) ||
                    line.contains("VAT", ignoreCase = true) ||
                    line.matches(Regex(".*\\d%.*")) || // Catches "5%"
                    line.startsWith("Items-", ignoreCase = true) // Catches "Items- 7"
                ) {
                    continue
                }
                names.add(line)
            }
        }

        // --- 4. Extract Prices ---
        val prices = mutableListOf<Float>()
        if (amountHeaderIndex != -1) {
            // Read prices until we hit the "Total" row
            val endLimit = if (totalIndex != -1) totalIndex else lines.size
            for (i in amountHeaderIndex + 1 until endLimit) {
                val line = lines[i]
                val cleanStr = line.replace(Regex("[^0-9.]"), "")
                val priceVal = cleanStr.toFloatOrNull()

                // Exclude sub-totals that equal the total amount
                if (priceVal != null && priceVal != total) {
                    prices.add(priceVal)
                }
            }
        }

        // --- 5. Zip them together ---
        val parsedItems = mutableListOf<ParsedItem>()
        // We take the minimum size to prevent crashes if OCR missed a price or item
        val minSize = minOf(names.size, prices.size)
        for (i in 0 until minSize) {
            parsedItems.add(ParsedItem(names[i], prices[i]))
        }

        return ParsedData(merchant, total, parsedItems, rawText)
    }
}