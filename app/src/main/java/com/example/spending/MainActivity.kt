package com.example.spending

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope

class MainActivity : AppCompatActivity() {

    private lateinit var formComponent: FormComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getDatabase(this)
        val dao = db.spendingDao()

        // --- Our updated Image Picker ---
        val pickMedia = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                // When scan finishes, auto-fill the form and switch to the Form tab!
                OcrScanner.processImage(this, uri) { parsedData ->
                    formComponent.fillFromOcr(parsedData)
                }
            }
        }

        val mainContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setPadding(16, 64, 16, 16)
            setBackgroundColor(Color.parseColor("#d4ecfc"))
        }

        val navLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 48)
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
        val btnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val btnForm = Button(this).apply { text = "Form"; layoutParams = btnParams }
        val btnHistory = Button(this).apply { text = "History"; layoutParams = btnParams }
        val btnSearch = Button(this).apply { text = "Items"; layoutParams = btnParams } // New Tab!
        val btnScan = Button(this).apply {
            text = "Scan"
            layoutParams = btnParams
            setOnClickListener { pickMedia.launch("image/*") }
        }

        navLayout.addView(btnForm)
        navLayout.addView(btnSearch)
        navLayout.addView(btnHistory)
        navLayout.addView(btnScan)

        // Inject our Views
        formComponent = FormComponent(this, dao, lifecycleScope)
        val historyView = HistoryView.create(this, dao, lifecycleScope).apply { visibility = View.GONE }
        val searchView = ItemSearchView.create(this, dao, lifecycleScope).apply { visibility = View.GONE }

        // Toggle Logic
        btnForm.setOnClickListener {
            formComponent.view.visibility = View.VISIBLE
            historyView.visibility = View.GONE
            searchView.visibility = View.GONE
        }
        btnHistory.setOnClickListener {
            formComponent.view.visibility = View.GONE
            historyView.visibility = View.VISIBLE
            searchView.visibility = View.GONE
        }
        btnSearch.setOnClickListener {
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