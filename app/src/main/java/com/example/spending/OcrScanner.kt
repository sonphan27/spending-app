package com.example.spending

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object OcrScanner {
    // Added a callback parameter!
    fun processImage(context: Context, imageUri: Uri, onSuccess: (ReceiptParser.ParsedData) -> Unit) {
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            Toast.makeText(context, "Scanning Receipt...", Toast.LENGTH_SHORT).show()

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    Log.d("OCR_RAW", "\n=== START ===\n${visionText.text}\n=== END ===")
                    val parsedData = ReceiptParser.parse(visionText.text)

                    // Pass the data back to MainActivity instead of showing a popup
                    Log.d("OCR_PARSED", "\n=== START ===\n${parsedData}\n=== END ===")
                    onSuccess(parsedData)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "OCR Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error loading image", Toast.LENGTH_SHORT).show()
        }
    }
}