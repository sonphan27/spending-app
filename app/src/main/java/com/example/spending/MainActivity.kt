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

        val pickMedia = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                OcrScanner.processImage(this, uri) { parsedData ->
                    formComponent.fillFromOcr(parsedData)
                }
            }
        }

        // Main Container
        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UITheme.COLOR_BACKGROUND)
        }

        val contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setPadding(0, 64, 0, 0)
        }

        // Modern Navigation Bar
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

        val btnForm = createNavBtn("New Spending")
        val btnSearch = createNavBtn("Items")
        val btnHistory = createNavBtn("History")
        val btnScan = createNavBtn("Upload bill").apply {
            setTextColor(UITheme.COLOR_PRIMARY)
            setOnClickListener { pickMedia.launch("image/*") }
        }

        navLayout.addView(btnForm)
        navLayout.addView(btnScan)
        navLayout.addView(btnHistory)
        navLayout.addView(btnSearch)

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

        highlightTab(btnForm) // Default active tab

        // Toggle Logic
        btnForm.setOnClickListener {
            highlightTab(btnForm)
            formComponent.view.visibility = View.VISIBLE
            historyView.visibility = View.GONE
            searchView.visibility = View.GONE
        }
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