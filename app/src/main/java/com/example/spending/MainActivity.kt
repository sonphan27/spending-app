package com.example.spending

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var formComponent: FormComponent
    private var useGeminiForNextScan = false // Stores the user's scanner choice
    private var tempImageUri: Uri? = null // Temporarily stores the camera photo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getDatabase(this)
        val dao = db.spendingDao()

        // Main Container
        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UITheme.COLOR_BACKGROUND)
            setPadding(0, 140, 0, 0) // Added padding to avoid status bar overlap
        }

        val contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // Navigation Bar
        val navLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 20, 24, 48)
            background = UITheme.createRoundedDrawable(UITheme.COLOR_CARD_BG, 0f, UITheme.COLOR_BORDER, 1)
        }

        val btnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(8, 0, 8, 0)
        }

        fun createNavBtn(textStr: String): Button {
            return Button(this).apply {
                text = textStr
                textSize = 13f
                isAllCaps = false
                setTextColor(UITheme.COLOR_TEXT_DARK)
                background = UITheme.createRoundedDrawable(Color.TRANSPARENT, 10f)
                layoutParams = btnParams
            }
        }

        val btnForm = createNavBtn("Form")
        val btnSearch = createNavBtn("Items")
        val btnHistory = createNavBtn("History")

        // Inject Views
        formComponent = FormComponent(this, dao, lifecycleScope)
        val historyView = HistoryView.create(this, dao, lifecycleScope).apply { visibility = View.GONE }
        val searchView = ItemSearchView.create(this, dao, lifecycleScope).apply { visibility = View.GONE }

        fun highlightTab(selectedBtn: Button) {
            val buttons = listOf(btnForm, btnSearch, btnHistory)
            for (b in buttons) {
                if (b == selectedBtn) {
                    b.background = UITheme.createRoundedDrawable(UITheme.COLOR_PRIMARY, 10f)
                    b.setTextColor(Color.WHITE)
                } else {
                    b.background = UITheme.createRoundedDrawable(Color.TRANSPARENT, 10f)
                    b.setTextColor(UITheme.COLOR_TEXT_DARK)
                }
            }
        }

        fun switchToFormTab() {
            highlightTab(btnForm)
            formComponent.view.visibility = View.VISIBLE
            historyView.visibility = View.GONE
            searchView.visibility = View.GONE
        }

        // --- Unified Photo/Camera Picker Handler ---
        val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // If data or data.data is null, it means the user took a picture with the Camera
                val isCamera = result.data == null || result.data?.data == null
                val selectedUri = if (isCamera) tempImageUri else result.data?.data

                if (selectedUri != null) {
                    OcrScanner.processImage(this, selectedUri, lifecycleScope, useGeminiForNextScan) { parsedData ->
                        switchToFormTab()
                        formComponent.fillFromOcr(parsedData)
                    }
                }
            }
        }

        // --- Scan Button with Selection Dialog ---
        val btnScan = createNavBtn("Scan").apply {
            setTextColor(UITheme.COLOR_PRIMARY)
            setOnClickListener {
                val options = arrayOf("Offline Scan (ML Kit)", "Cloud AI Scan (Gemini)")

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Select Scanner Engine")
                    .setItems(options) { _, which ->
                        useGeminiForNextScan = (which == 1)

                        try {
                            // Setup temporary URI for the Camera intent
                            val tmpFile = File.createTempFile("receipt_tmp_", ".jpg", cacheDir).apply {
                                deleteOnExit()
                            }
                            tempImageUri = FileProvider.getUriForFile(
                                this@MainActivity,
                                "${applicationContext.packageName}.provider",
                                tmpFile
                            )

                            // Intent to pick from gallery
                            val galleryIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "image/*"
                            }

                            // Intent to capture from camera with EXPLICIT permissions attached
                            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                                putExtra(MediaStore.EXTRA_OUTPUT, tempImageUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                clipData = ClipData.newUri(contentResolver, "photo", tempImageUri)
                            }

                            // Create a chooser that merges both intents (Camera will appear as an option)
                            val chooserIntent = Intent.createChooser(galleryIntent, "Select Receipt Image").apply {
                                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
                            }

                            pickImageLauncher.launch(chooserIntent)

                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Failed to start picker: FileProvider missing?", Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        navLayout.addView(btnForm)
        navLayout.addView(btnSearch)
        navLayout.addView(btnHistory)
        navLayout.addView(btnScan)

        highlightTab(btnForm) // Default active tab

        // Toggle Listeners
        btnForm.setOnClickListener { switchToFormTab() }
        btnHistory.setOnClickListener {
            highlightTab(btnHistory)
            formComponent.view.visibility = View.GONE
            historyView.visibility = View.VISIBLE
            searchView.visibility = View.GONE
        }
        btnSearch.setOnClickListener {
            highlightTab(btnSearch)
            formComponent.view.visibility = View.GONE
            historyView.visibility = View.GONE
            searchView.visibility = View.VISIBLE
        }

        contentContainer.addView(formComponent.view)
        contentContainer.addView(historyView)
        contentContainer.addView(searchView)

        mainContainer.addView(contentContainer)
        mainContainer.addView(navLayout)

        setContentView(mainContainer)
    }
}