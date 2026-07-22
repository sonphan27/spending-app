package com.example.spending

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import org.json.JSONObject

/**
 * GeminiScanner handles online receipt parsing using Google Gemini 1.5 Flash.
 *
 * It takes a Bitmap image of a receipt, sends it to Gemini Flash, and requests
 * a clean JSON response containing the merchant name, total amount, and itemized products
 * (automatically translated to English if necessary).
 */
object GeminiScanner {

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    /**
     * Sends the receipt bitmap to Gemini Flash and converts the extracted JSON response
     * into a [ReceiptParser.ParsedData] instance.
     */
    suspend fun processImageWithGemini(context: Context, bitmap: Bitmap): ReceiptParser.ParsedData {
        val prompt = """
            Analyze this receipt image and extract the transaction details.
            Return ONLY a raw JSON object without markdown formatting or code fences.
            Translate any non-English item names or merchants into English.

            Expected JSON Format:
            {
              "merchant": "Merchant Name",
              "total": 0.00,
              "items": [
                {
                  "name": "Item Name in English",
                  "price": 0.00
                }
              ]
            }
        """.trimIndent()

        val response = generativeModel.generateContent(
            content {
                image(bitmap)
                text(prompt)
            }
        )

        val rawResponseText = response.text ?: ""

        // Strip backticks/markdown formatting in case the model returns ```json ... ```
        val cleanedJsonStr = rawResponseText
            .replace("```json", "")
            .replace("```", "")
            .trim()

        return parseJsonToParsedData(cleanedJsonStr, rawResponseText)
    }

    /**
     * Safely parses the returned JSON string into [ReceiptParser.ParsedData].
     */
    private fun parseJsonToParsedData(jsonStr: String, rawText: String): ReceiptParser.ParsedData {
        return try {
            val json = JSONObject(jsonStr)
            val merchant = json.optString("merchant", "Unknown Merchant")
            val total = json.optDouble("total", 0.0).toFloat()
            val itemsArray = json.optJSONArray("items")

            val parsedItems = mutableListOf<ReceiptParser.ParsedItem>()
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    val name = itemObj.optString("name", "Item")
                    val price = itemObj.optDouble("price", 0.0).toFloat()
                    parsedItems.add(ReceiptParser.ParsedItem(name, price))
                }
            }

            ReceiptParser.ParsedData(merchant, total, parsedItems, rawText)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback if JSON parsing fails unexpectedly
            ReceiptParser.ParsedData("Unknown Merchant", 0.0f, emptyList(), rawText)
        }
    }
}