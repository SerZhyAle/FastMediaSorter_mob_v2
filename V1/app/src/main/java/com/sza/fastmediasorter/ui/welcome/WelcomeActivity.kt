// Package declaration for the welcome UI components
package com.sza.fastmediasorter.ui.welcome

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.sza.fastmediasorter.MainActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityWelcomeBinding
import com.sza.fastmediasorter.ui.base.LocaleActivity
import com.sza.fastmediasorter.utils.PreferenceManager

/**
 * WelcomeActivity displays an onboarding flow for first-time users.
 * It guides users through the initial setup process with a series of informational screens
 * that explain how to configure the app for optimal use.
 *
 * The activity shows 7 pages of setup instructions and handles both first launch
 * and manual access from settings scenarios.
 */
class WelcomeActivity : LocaleActivity() {
    // View binding instance for accessing UI elements without findViewById
    private lateinit var binding: ActivityWelcomeBinding

    // Preference manager for storing user settings and app state
    private lateinit var preferenceManager: PreferenceManager

    // Adapter for managing the ViewPager's content (welcome pages)
    private lateinit var adapter: WelcomePagerAdapter

    // List to hold indicator dots that show current page position
    private val indicators = mutableListOf<ImageView>()

    // Static list of welcome pages containing setup instructions
    // Each page has an icon, title, description, and list of features to display
    private val pages =
        listOf(
            // Page 1: Introduction and importance of setup
            WelcomePage(
                icon = "👋",
                title = "Welcome to FastMediaSorter!",
                description = "Before you start, let's configure the app properly",
                features =
                    listOf(
                        "⚠️ IMPORTANT: The app needs initial setup",
                        "⚠️ Without setup, main screens will be empty",
                        "✓ Follow these steps to configure everything",
                        "✓ Takes only 2-3 minutes",
                    ),
            ),
            // Page 2: Settings overview
            WelcomePage(
                icon = "⚙️",
                title = "STEP 1: Open Settings",
                description = "All configuration happens in Settings tab",
                features =
                    listOf(
                        "📍 Tap Settings icon (gear ⚙️) at the bottom",
                        "📍 This is where you configure everything",
                        "📍 You MUST do this before using other tabs",
                        "⚠️ Skip this = empty main screen",
                    ),
            ),
            // Page 3: Network connection setup
            WelcomePage(
                icon = "🌐",
                title = "STEP 2: Add Network Connection",
                description = "Connect to your NAS or network share",
                features =
                    listOf(
                        "📍 In Settings → tap 'Network' section",
                        "📍 Enter server: \\\\192.168.1.100\\Photos",
                        "📍 Add username/password if needed",
                        "📍 Tap 'Test Connection' to verify",
                        "📍 Tap Save (💾) to store connection",
                    ),
            ),
            // Page 4: Sort destinations configuration
            WelcomePage(
                icon = "🎯",
                title = "STEP 3: Configure Destinations",
                description = "Set up folders for sorting photos",
                features =
                    listOf(
                        "📍 In Settings → tap 'Sort to..' section",
                        "📍 Create 2-10 colored destinations",
                        "📍 Each destination = folder for sorted files",
                        "📍 Example: 'Family', 'Work', 'Trash'",
                        "⚠️ Without destinations, sorting won't work",
                    ),
            ),
            // Page 5: Local folders access (optional)
            WelcomePage(
                icon = "📱",
                title = "STEP 4 (Optional): Local Folders",
                description = "Access device photos - optional if using network",
                features =
                    listOf(
                        "📍 In Settings → tap 'Grant Media Access'",
                        "📍 Allow permission to see Camera, Screenshots",
                        "📍 Or add custom folders via '+' button",
                        "✓ Skip if only using network shares",
                    ),
            ),
            // Page 6: Setup completion
            WelcomePage(
                icon = "✅",
                title = "Setup Complete!",
                description = "Now you can use the app",
                features =
                    listOf(
                        "🎬 Slideshow tab: View photos automatically",
                        "📤 Sort tab: Organize photos to destinations",
                        "⚠️ If tabs are empty → check Settings again",
                        "💡 Tip: Use 'Test' button to verify connections",
                    ),
            ),
            // Page 7: Final checklist
            WelcomePage(
                icon = "🚀",
                title = "Quick Start Checklist",
                description = "Did you complete these steps?",
                features =
                    listOf(
                        "☐ Opened Settings tab (⚙️)",
                        "☐ Added at least 1 network connection OR granted media access",
                        "☐ Created at least 2 sort destinations",
                        "☐ Tested connection with 'Test' button",
                        "✅ If YES to all → tap 'Start Using App' below",
                    ),
            ),
        )

    /**
     * Called when the activity is first created.
     * Initializes the UI, sets up the ViewPager with welcome pages,
     * configures navigation indicators and buttons, and handles first launch logic.
     *
     * @param savedInstanceState Bundle containing the activity's previously saved state, or null if none exists
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call parent class onCreate to perform standard activity initialization
        super.onCreate(savedInstanceState)

        // Inflate the layout using View Binding and set it as the content view
        // This replaces the traditional setContentView(R.layout.activity_welcome) approach
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide the action bar to provide a full-screen welcome experience
        supportActionBar?.hide()

        // Initialize the preference manager for storing user settings and app state
        preferenceManager = PreferenceManager(this)

        // Determine if this is a first launch or manual access from settings
        // Default to false if the extra is not provided
        val isFirstLaunch = intent.getBooleanExtra("isFirstLaunch", false)

        // Show close button only if NOT first launch (allows users to exit when accessed from settings)
        // Hide close button on first launch to force completion of onboarding
        binding.closeButton.visibility = if (isFirstLaunch) View.GONE else View.VISIBLE

        // Set up the ViewPager with welcome pages and navigation
        setupViewPager()

        // Create and configure the page indicator dots
        setupIndicators()

        // Configure the navigation buttons (Next/Skip)
        setupButtons()

        // Set up click listener for the close button (only visible when not first launch)
        binding.closeButton.setOnClickListener {
            // Simply finish the activity without marking welcome as shown
            finish()
        }
    }

    /**
     * Sets up the ViewPager with the welcome pages adapter and page change callbacks.
     * The ViewPager displays the sequence of onboarding screens.
     */
    private fun setupViewPager() {
        // Create and set the adapter that manages the welcome pages
        adapter = WelcomePagerAdapter(pages)
        binding.viewPager.adapter = adapter

        // Register a callback to handle page changes
        // This updates indicators and buttons when user navigates between pages
        binding.viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                // Called when a new page is selected (either by user swipe or programmatic change)
                override fun onPageSelected(position: Int) {
                    // Call parent implementation (required)
                    super.onPageSelected(position)

                    // Update the indicator dots to show current page
                    updateIndicators(position)

                    // Update button visibility/text based on current page
                    updateButtons(position)
                }
            },
        )
    }

    /**
     * Creates and configures the page indicator dots.
     * Indicators show which page is currently active and total number of pages.
     */
    private fun setupIndicators() {
        // Clear any existing indicators (in case of recreation)
        indicators.clear()

        // Remove all existing views from the indicator layout
        binding.indicatorLayout.removeAllViews()

        // Calculate sizes in pixels using device density for consistent appearance
        val size = (8 * resources.displayMetrics.density).toInt() // 8dp indicator size
        val margin = (4 * resources.displayMetrics.density).toInt() // 4dp margin between indicators

        // Create an indicator dot for each welcome page
        for (i in pages.indices) {
            // Create new ImageView for the indicator
            val indicator = ImageView(this)

            // Set layout parameters with calculated size and margins
            val params = LinearLayout.LayoutParams(size, size)
            params.setMargins(margin, 0, margin, 0)
            indicator.layoutParams = params

            // Set the indicator icon (using settings icon as dot)
            indicator.setImageResource(R.drawable.ic_settings)

            // Apply color filter: primary color for first page, gray for others
            indicator.setColorFilter(
                ContextCompat.getColor(
                    this,
                    if (i == 0) R.color.primary else android.R.color.darker_gray,
                ),
            )

            // Add the indicator to the layout and tracking list
            binding.indicatorLayout.addView(indicator)
            indicators.add(indicator)
        }
    }

    /**
     * Updates the visual state of indicator dots based on current page position.
     * Active page shows primary color, inactive pages show gray.
     *
     * @param position The current page position (0-based index)
     */
    private fun updateIndicators(position: Int) {
        // Update each indicator's color based on whether it represents the current page
        indicators.forEachIndexed { index, indicator ->
            indicator.setColorFilter(
                ContextCompat.getColor(
                    this,
                    if (index == position) R.color.primary else android.R.color.darker_gray,
                ),
            )
        }
    }

    /**
     * Updates button visibility and text based on current page position.
     * Last page shows "Get Started" instead of "Next" and hides skip button.
     *
     * @param position The current page position (0-based index)
     */
    private fun updateButtons(position: Int) {
        // Check if we're on the last page
        when {
            position == pages.size - 1 -> {
                // Last page: hide skip button and change next to "Get Started"
                binding.skipButton.visibility = View.GONE
                binding.nextButton.text = "Get Started"
            }
            else -> {
                // Other pages: show skip button and keep "Next" text
                binding.skipButton.visibility = View.VISIBLE
                binding.nextButton.text = "Next"
            }
        }
    }

    /**
     * Sets up click listeners for the navigation buttons (Next and Skip).
     * Handles page navigation and completion of the welcome flow.
     */
    private fun setupButtons() {
        // Set up click listener for the next/get started button
        binding.nextButton.setOnClickListener {
            // Check if there are more pages to show
            if (binding.viewPager.currentItem < pages.size - 1) {
                // Move to next page
                binding.viewPager.currentItem = binding.viewPager.currentItem + 1
            } else {
                // Last page reached - complete the welcome flow
                finishWelcome()
            }
        }

        // Set up click listener for the skip button
        binding.skipButton.setOnClickListener {
            // Skip to the end - complete welcome flow immediately
            finishWelcome()
        }
    }

    /**
     * Completes the welcome flow and handles post-welcome navigation.
     * Marks welcome as shown, sets appropriate flags, and navigates to MainActivity if first launch.
     */
    private fun finishWelcome() {
        // Check if this was a first launch (passed from MainActivity)
        val isFirstLaunch = intent.getBooleanExtra("isFirstLaunch", false)

        // Mark that the user has seen the welcome screens
        // This prevents showing welcome again on subsequent app launches
        preferenceManager.setWelcomeShown()

        // Handle first launch vs. manual access differently
        if (isFirstLaunch) {
            // First launch: set flag so MainActivity knows to handle post-welcome setup
            preferenceManager.setReturnedFromWelcome(true)

            // Start MainActivity to continue with permission requests and settings
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        // For non-first launch, just finish without starting MainActivity

        // Close the welcome activity
        finish()
    }
}

/**
 * Data class representing a single welcome page in the onboarding flow.
 * Each page contains visual and textual content to guide users through app setup.
 *
 * @property icon Emoji or visual identifier for the page (e.g., "👋", "⚙️")
 * @property title Main heading text displayed prominently on the page
 * @property description Subtitle or additional context for the page content
 * @property features List of bullet points explaining key features or steps for this page
 */
data class WelcomePage(
    val icon: String,
    val title: String,
    val description: String,
    val features: List<String>,
)
