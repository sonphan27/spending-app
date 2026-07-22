package com.example.spending

object ReceiptParser {

    data class ParsedItem(val name: String, val price: Float)

    data class ParsedData(
        val merchantName: String,
        val totalAmount: Float,
        val items: List<ParsedItem>,
        val rawText: String
    )

    fun parse(rawText: String): ParsedData {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // --- 1. Smart Merchant Extraction (Bypasses top noise like "CH") ---
        var merchant = "Unknown Merchant"
        val merchantKeywords = listOf("SUPERMARKET", "HYPERMARKET", "LLC", "STORE", "MART", "GROCERY", "RESTAURANT", "CAFE", "TRADING")

        // Search top 15 lines for business indicators
        val foundKeywordLine = lines.take(15).firstOrNull { line ->
            merchantKeywords.any { keyword -> line.contains(keyword, ignoreCase = true) }
        }

        if (foundKeywordLine != null) {
            merchant = foundKeywordLine
        } else {
            // Fallback: Pick the first line that is longer than 3 characters
            val firstValidLine = lines.firstOrNull { it.length > 3 && !it.matches(Regex("\\d+")) }
            if (firstValidLine != null) {
                merchant = firstValidLine
            }
        }

        // --- 2. Total Amount ---
        var total = 0.0f
        val totalIndex = lines.indexOfLast { it.equals("Total", ignoreCase = true) }
        if (totalIndex != -1 && totalIndex + 1 < lines.size) {
            val totalStr = lines[totalIndex + 1].replace(Regex("[^0-9.]"), "")
            total = totalStr.toFloatOrNull() ?: 0.0f
        }

        if (total == 0.0f) {
            val decimalRegex = Regex("\\d+\\.\\d{2}")
            val allDecimals = decimalRegex.findAll(rawText).mapNotNull { it.value.toFloatOrNull() }.toList()
            if (allDecimals.isNotEmpty()) {
                total = allDecimals.maxOrNull() ?: 0.0f
            }
        }

        // --- 3. Find Column Headers ---
        val itemHeaderIndex = lines.indexOfFirst { it.contains("Item Description", ignoreCase = true) }
        val amountHeaderIndex = lines.indexOfLast {
            it.equals("Amout", ignoreCase = true) || it.equals("Amount", ignoreCase = true)
        }

        // --- 4. Extract Item Names ---
        val names = mutableListOf<String>()
        val billAmountIndex = lines.indexOfFirst { it.contains("Bill Amount", ignoreCase = true) }
        val namesEndIndex = if (billAmountIndex != -1) billAmountIndex else (if (amountHeaderIndex != -1) amountHeaderIndex else lines.size)

        if (itemHeaderIndex != -1) {
            for (i in itemHeaderIndex + 1 until namesEndIndex) {
                val line = lines[i]
                if (line.matches(Regex(".*\\d%.*")) || line.startsWith("Items-", ignoreCase = true)) {
                    continue
                }
                names.add(line)
            }
        }

        // --- 5. Extract Item Prices ---
        val prices = mutableListOf<Float>()
        if (amountHeaderIndex != -1) {
            for (i in amountHeaderIndex + 1 until lines.size) {
                val line = lines[i]

                if (line.equals("Total", ignoreCase = true)) {
                    break
                }

                val cleanStr = line.replace(Regex("[^0-9.]"), "")
                var priceVal = cleanStr.toFloatOrNull()

                if (priceVal != null) {
                    if (total > 0f && priceVal == total) {
                        continue
                    }

                    if (!cleanStr.contains(".") && priceVal > 50f && total > 0f && priceVal > total) {
                        priceVal /= 10f
                    }

                    prices.add(priceVal)
                }
            }
        }

        // --- 6. Zip Names & Prices ---
        val parsedItems = mutableListOf<ParsedItem>()
        val minSize = minOf(names.size, prices.size)
        for (i in 0 until minSize) {
            parsedItems.add(ParsedItem(names[i], prices[i]))
        }

        return ParsedData(merchant, total, parsedItems, rawText)
    }
}