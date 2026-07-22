package com.example.spending

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OcrScanner routes receipt image processing based on user selection.
 */
object OcrScanner {

    fun processImage(
        context: Context,
        imageUri: Uri,
        scope: CoroutineScope,
        useGemini: Boolean,
        onSuccess: (ReceiptParser.ParsedData) -> Unit
    ) {
        if (useGemini) {
            // --- ONLINE: Process via Gemini AI ---
            Toast.makeText(context, "Processing via Gemini AI...", Toast.LENGTH_SHORT).show()

            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(imageUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (bitmap != null) {
                        val parsedData = GeminiScanner.processImageWithGemini(context, bitmap)
                        withContext(Dispatchers.Main) {
                            onSuccess(parsedData)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to load image. Running offline scanner...", Toast.LENGTH_SHORT).show()
                            processOfflineMlKit(context, imageUri, onSuccess)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Gemini scan failed, falling back to local ML Kit...", Toast.LENGTH_SHORT).show()
                        processOfflineMlKit(context, imageUri, onSuccess)
                    }
                }
            }
        } else {
            // --- OFFLINE: Process via local ML Kit ---
            Toast.makeText(context, "Offline: Processing locally with ML Kit...", Toast.LENGTH_SHORT).show()
            processOfflineMlKit(context, imageUri, onSuccess)
        }
    }

    private fun processOfflineMlKit(
        context: Context,
        imageUri: Uri,
        onSuccess: (ReceiptParser.ParsedData) -> Unit
    ) {
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    Log.d("OCR_RAW", "\n=== START ===\n${visionText.text}\n=== END ===")
                    val parsedData = ReceiptParser.parse(visionText.text)

                    // Pass the data back to MainActivity instead of showing a popup
                    Log.d("OCR_PARSED", "\n=== START ===\n${parsedData}\n=== END ===")
                    onSuccess(parsedData)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "ML Kit Scan Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error loading image for offline scan", Toast.LENGTH_SHORT).show()
        }
    }
}