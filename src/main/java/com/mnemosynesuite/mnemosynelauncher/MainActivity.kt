package com.mnemosynesuite.mnemosynelauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.net.Uri
import android.provider.ContactsContract
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat

class MainActivity : AppCompatActivity() {

    data class AppEntry(val name: String, val packageName: String, val icon: Drawable?)

    // Keep track of whether the menu screen is currently open
    private val selectedLayout: String
        get() = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).getString("home_screen_layout", "timedate_simple") as String

    private val clockHandler = Handler(Looper.getMainLooper())
    private lateinit var clockRunnable: Runnable
    private var isMenuOpen = false
    private val frequent_apps_bar_enabled: Boolean
        get() = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            .getBoolean("frequent_apps_bar_enabled", false)

    private val category_apps_labels_enabled: Boolean
        get() = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            .getBoolean("category_apps_labels_enabled", false)

    private val dpad_enabled: Boolean
        get() = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).getBoolean("dpad_enabled", false)

    private val selected_app_label_enabled: Boolean
        get() = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            .getBoolean("mainmenu_selected_app_label_enabled", false)

    private val two_row_frequent_apps: Boolean
        get() = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            .getBoolean("two_row_frequent_apps", false)

    val selectedIconPack: String
        get() = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        .getString("chosen_icon_pack", "default") as String

    val selectedMainMenuOpenAnimation: String
        get() = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            .getString("main_menu_open_animation", "zoom") as String

    private lateinit var defaultMessagingApp: String
    private lateinit var defaultContactsApp: String
    private lateinit var defaultPhoneCallingApp: String

    private lateinit var frequentPackages: List<String>

    private lateinit var menuGridHighlight: ImageView
    private var isFirstFocus = true

    val recentappbar_icon_size_row=32
    val recentappbar_icon_size_grid=48

    val ANDROID_HOME_SCREEN_COLS = 4
    val ANDROID_HOME_SCREEN_ROWS = 6

    private val ANDROID_HOME_SCREEN_shortcutFrames = mutableMapOf<String, WidgetFrame>()

    enum class AppCategory(
        val stringResId: Int
    ) {
        INTERNET(R.string.menu_category_internet),
        GAMES(R.string.menu_category_games),
        MEDIA(R.string.menu_category_media),
        COMMUNICATIONS(R.string.menu_category_communications),
        ORGANIZER(R.string.menu_category_organizer),
        SETTINGS(R.string.menu_category_other);

        companion object {
            // Safe fallback helper in case you still need to parse a String from a database/API
            fun fromString(value: String?): AppCategory? {
                return entries.find { it.name == value || "CAT_${it.name}" == value }
            }
        }
    }

    val leftPkg: String
        get() = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            .getString("dock_left_package", "default") as String

    val rightPkg: String
        get() = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            .getString("dock_right_package", "default") as String

    private fun persistShortcuts() {
        val states = ANDROID_HOME_SCREEN_shortcutFrames.map { (pkg, f) ->
            ShortcutState(pkg, f.cellX, f.cellY)
        }
        WidgetStateStore.saveShortcuts(this, states)
    }

    private val ANDROID_HOME_SCREEN_widgetFrames = mutableMapOf<Int, WidgetFrame>()

    private fun persistWidgets() {
        val states = ANDROID_HOME_SCREEN_widgetFrames.map { (id, f) ->
            WidgetState(id, f.cellX, f.cellY, f.cellW, f.cellH)
        }
        WidgetStateStore.save(this, states)
    }

    companion object {
        // Hardcoded unique ID for your application's widget host
        private const val APP_WIDGET_HOST_ID = 1024
        private const val REQUEST_PICK_APPWIDGET = 1
        private const val REQUEST_BIND_APPWIDGET = 2
        private const val REQUEST_CREATE_APPWIDGET = 3
    }

    private lateinit var menuApps: List<GridItem>

    private fun getIconForCategory(_appCat: AppCategory): Int {

        return when (_appCat) {
            AppCategory.INTERNET  -> R.drawable.menuicon_internet
            AppCategory.GAMES     -> R.drawable.menuicon_games
            AppCategory.MEDIA     -> R.drawable.menuicon_media
            AppCategory.COMMUNICATIONS     -> R.drawable.menuicon_comm
            AppCategory.ORGANIZER  -> R.drawable.menuicon_calendar
            AppCategory.SETTINGS  -> R.drawable.menuicon_settings
            else        -> android.R.drawable.btn_star // Safe fallback icon if a name doesn't match
        }
    }

//    private val subCategoryMap = mapOf(
//        "CAT_MEDIA" to SubCategory("CAT_MEDIA", listOf(
//            GridItem("Spotify", R.drawable.menuicon_walkman),
//            GridItem("YouTube Music", R.drawable.menuicon_media)
//        )),
//        "CAT_COMMUNICATIONS" to SubCategory("CAT_COMMUNICATIONS", listOf(
//            GridItem("Viber", R.drawable.menuicon_comm),      // Ensure these exist
//            GridItem("WhatsApp", R.drawable.menuicon_comm),   // Replace with your icons
//            GridItem("Telegram", R.drawable.menuicon_comm)    // Replace with your icons
//        ))
//        // Add more categories here
//    )

    private lateinit var subCategoryMap: Map<AppCategory, SubCategory>

    private fun startLiveClock() {
        val clockText = findViewById<TextView>(R.id.homeClock)
        val calendarText = findViewById<TextView>(R.id.homeCalendar)

        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        // Formats the date cleanly (e.g., "December 31, 2025")
        val dateFormatter = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

        clockRunnable = Runnable {
            val currentTime = Calendar.getInstance().time
            clockText.text = timeFormatter.format(currentTime)
            calendarText.text = dateFormatter.format(currentTime)

            // Sync update checks to fire every single second to maintain clock precision
            clockHandler.postDelayed(clockRunnable, 1000)
        }

        clockHandler.post(clockRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Prevent background memory leaks by detaching the loop handler when the app stops
        if (::clockRunnable.isInitialized) {
            clockHandler.removeCallbacks(clockRunnable)
        }
    }

    private fun setupHomeScreenLayout() {
        val simpleContainer = findViewById<ConstraintLayout>(R.id.layout_timedate_simple)
        val symbianContainer = findViewById<ConstraintLayout>(R.id.layout_symbian_s60)
        val androidContainer = findViewById<ConstraintLayout>(R.id.layout_android)

        // Turn everything off first
        simpleContainer.visibility = View.GONE
        symbianContainer.visibility = View.GONE
        androidContainer.visibility = View.GONE

        // Turn on only the container specified in the configuration
        Log.w("Home screen","Selected layout: $selectedLayout")
        when (selectedLayout) {
            "timedate_simple" -> {
                simpleContainer.visibility = View.VISIBLE
                startLiveClock() // Initialize time engine loop
            }
            "symbian_s60" -> symbianContainer.visibility = View.VISIBLE
            "android" -> {
                androidContainer.visibility = View.VISIBLE
            }
            else -> Log.w("Launcher", "Unknown home screen layout specified: $selectedLayout")
        }
    }

    private fun trackAppLaunch(packageName: String) {
        val trackPrefs = getSharedPreferences("frequently_used_apps_list", Context.MODE_PRIVATE)

        // Fetch current count (defaulting to 0) and increment it
        val currentCount = trackPrefs.getInt(packageName, 0)
        trackPrefs.edit().putInt(packageName, currentCount + 1).apply()
        frequentPackages=getSortedFrequentlyUsedApps()
    }

    /**
     * Reads all tracking counts and returns a list of package names sorted from most to least launched
     */
    private fun getSortedFrequentlyUsedApps(): List<String> {
        val trackPrefs = getSharedPreferences("frequently_used_apps_list", Context.MODE_PRIVATE)

        // 1. Get all recorded package entries from the XML file map
        val allEntries = trackPrefs.all as? Map<String, Int> ?: emptyMap()

        // 2. Sort entries by their value (count) descending, and map back to just the package name keys
        return allEntries.entries
            .sortedByDescending { it.value }
            .map { it.key }
    }

    private fun launchApplicationActivity(packageName: String, activityName: String = "") {
        try {
            val intent = Intent().apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

                if (activityName.isNotEmpty()) {
                    component = ComponentName(packageName, activityName)
                } else {
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null && launchIntent.component != null) {
                        component = launchIntent.component
                    } else {
                        throw Exception("No main launcher activity found for package: $packageName")
                    }
                }
            }
            startActivity(intent)
            if (isMenuOpen) {
                exitMenu()
            }

            // 🟢 Track the successful launch count using only the package name
            trackAppLaunch(packageName)
            populateRecentApps()

        } catch (e: Exception) {
            Toast.makeText(this, "Unable to launch shortcut target", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun populateMenu() {
        if (frequent_apps_bar_enabled) {
            populateRecentApps()

            val recentItemsContainerCont = findViewById<android.widget.HorizontalScrollView>(R.id.recentAppsItemsContainerCont)
            val recentAppsGridContainerCont = findViewById<android.widget.ScrollView>(R.id.recentAppsGridContainerCont)

            if(two_row_frequent_apps) {
                recentAppsGridContainerCont.visibility=View.VISIBLE;
                recentItemsContainerCont.visibility=View.GONE;

                val params = recentAppsGridContainerCont.layoutParams
                val scale = resources.displayMetrics.density
                params.height = (recentappbar_icon_size_grid * 2 * scale).toInt()
                recentAppsGridContainerCont.layoutParams = params
            }
            else {
                recentAppsGridContainerCont.visibility=View.GONE;
                recentItemsContainerCont.visibility=View.VISIBLE;
            }
        }

        val selectedTitle = findViewById<TextView>(R.id.selectedCategoryTitle) // Find the header TextView

        // 1. Determine landscape ratio via screen dimension pixels
        val displayMetrics = resources.displayMetrics
        val landscape = displayMetrics.widthPixels >= displayMetrics.heightPixels
        Log.d("MnemosyneLauncher", "Is landscape mode active? -> $landscape")

        val columns: Int
        if (landscape) {
            columns = 4

            // 2. Transpose the 3x4 layout to a 4x3 layout (Rotated left)
            // Original positions map to a grid of 3 columns, 4 rows.
            // We read down the original columns to build the new horizontal rows.
            val transposedList = mutableListOf<GridItem>()
            for (col in 2 downTo 0) {       // Start from 'CAT_GAMES' column (index 2) down to 'App Store' column (index 0)
                for (row in 0 until 4) {    // Read top to bottom through each row
                    val originalIndex = (row * 3) + col
                    transposedList.add(menuApps[originalIndex])
                }
            }
            menuApps = transposedList
        } else {
            columns = 3
        }

        // 3. Set up the RecyclerView with the dynamic column count
        val recyclerView = findViewById<RecyclerView>(R.id.menuRecyclerView)
        recyclerView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, columns)
        recyclerView.adapter = GridAdapter(this,menuApps, category_apps_labels_enabled,
            onItemSelected = { appName ->
                selectedTitle.text = appName // Updates text on focus
            },
            onItemClicked = { appName ->
                // Navigate on click
                Log.d("clicked","- Clicked app name: $appName")
                when (appName) {
                    getString(AppCategory.COMMUNICATIONS.stringResId) -> populateSubCat(AppCategory.COMMUNICATIONS, "list_view")
                    getString(AppCategory.MEDIA.stringResId) -> populateSubCat(AppCategory.MEDIA, "list_view")
                    getString(AppCategory.ORGANIZER.stringResId) -> populateSubCat(AppCategory.ORGANIZER, "list_view")
                    getString(AppCategory.INTERNET.stringResId) -> populateSubCat(AppCategory.INTERNET, "list_view")
                    getString(AppCategory.GAMES.stringResId) -> populateSubCat(AppCategory.GAMES, "list_view")
                    getString(AppCategory.SETTINGS.stringResId) -> populateSubCat(AppCategory.SETTINGS, "list_view")
                    getString(R.string.menu_item_appstore) -> launchApplicationActivity(
                        packageName = "com.android.vending",
                        activityName = ""
                    )
                    getString(R.string.menu_item_camera) -> launchApplicationActivity(
                        packageName = "com.google.android.GoogleCameraEng"
                    )
                    getString(R.string.menu_item_messages) -> launchApplicationActivity(
                        packageName = defaultMessagingApp
                    )
                    getString(R.string.menu_item_files) -> launchApplicationActivity(
                        packageName = "com.mixplorer"
                    )
                    getString(R.string.menu_item_contacts) -> launchApplicationActivity(
                        packageName = defaultContactsApp
                    )
                    getString(R.string.menu_item_music) -> launchApplicationActivity(
                        packageName = "com.aimp.player"
                    )
                }
            },
            onItemLongClicked = { appname -> Log.d("TODO",appname) }
        )
    }

    fun getIconFromPackByDrawableName(
        context: Context,
        iconPackPackage: String,
        drawableName: String
    ): Drawable? {
        if (iconPackPackage.isEmpty() || iconPackPackage == "default" || drawableName.isEmpty()) {
            return null
        }
        try {
            val pm = context.packageManager
            val iconPackResources = pm.getResourcesForApplication(iconPackPackage)

            // Lookup resource ID matching the string name entry from the icon pack's internal tables
            val iconResId = iconPackResources.getIdentifier(
                drawableName.trim(),
                "drawable",
                iconPackPackage
            )

            if (iconResId != 0) {
                return ResourcesCompat.getDrawable(iconPackResources, iconResId, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getIconFromPack(context: Context, iconPackPackage: String, targetPackage: String): Drawable {
        val pm = context.packageManager

        // Safe fallback generator function
        val getDefaultIcon = {
            try {
                pm.getApplicationIcon(targetPackage)
            } catch (e: Exception) {
                // Ultimate fallback if the target app package itself doesn't exist or is uninstalled
                pm.defaultActivityIcon
            }
        }

        // If "Default" is selected in settings, bypass parsing and return the stock icon immediately
        if (iconPackPackage.isEmpty() || iconPackPackage == "default") {
            return getDefaultIcon()
        }

        // 1. Get the main launch intent component for the target app
        val launchIntent = pm.getLaunchIntentForPackage(targetPackage) ?: return getDefaultIcon()
        val componentName = launchIntent.component ?: return getDefaultIcon()

        // Convert to standard format: "ComponentInfo{com.package/com.package.Activity}"
        // Most appfilter.xml files look for: "ComponentInfo{com.package/com.package.Activity}" or just "com.package/com.package.Activity"
        val componentString = componentName.toString()

        try {
            // 2. Fetch the icon pack's resources context
            val iconPackResources = pm.getResourcesForApplication(iconPackPackage)
            val resId = iconPackResources.getIdentifier("appfilter", "xml", iconPackPackage)

            if (resId != 0) {
                val parser = iconPackResources.getXml(resId)
                var eventType = parser.eventType
                var drawableResourceName: String? = null

                // 3. Parse appfilter.xml to find the component mapping
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        val componentAttr = parser.getAttributeValue(null, "component")

                        // Better matching: matches the exact signature component string format
                        if (componentAttr != null && componentString.contains(componentAttr.trim())) {
                            drawableResourceName = parser.getAttributeValue(null, "drawable")
                            break
                        }
                    }
                    eventType = parser.next()
                }

                // 4. If found, load the drawable resource from the icon pack directly
                if (!drawableResourceName.isNullOrEmpty()) {
                    val iconResId = iconPackResources.getIdentifier(drawableResourceName, "drawable", iconPackPackage)
                    if (iconResId != 0) {
                        val drawable = androidx.core.content.res.ResourcesCompat.getDrawable(iconPackResources, iconResId, null)
                        if (drawable != null) return drawable
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback if appfilter parsed but icon asset wasn't found inside the pack
        return getDefaultIcon()
    }

    private fun populateRecentApps() {
        val recentItemsContainer = findViewById<android.widget.LinearLayout>(R.id.recentAppsItemsContainer)
        val recentAppsGridContainer = findViewById<android.widget.GridLayout>(R.id.recentAppsGridContainer)

        // Clear any previous iterations before rendering the tracked apps
        recentItemsContainer.removeAllViews()
        recentAppsGridContainer.removeAllViews()

        val scale = resources.displayMetrics.density
        val sizeInDp = if (two_row_frequent_apps) recentappbar_icon_size_grid else recentappbar_icon_size_row
        val sizeInPx = (sizeInDp * scale).toInt()

        frequentPackages.forEach { pkgName ->
            try {
                // Pull the application icon directly from the system package manager using the tracked key
                val appIcon: Drawable = getIconFromPack(this, selectedIconPack, pkgName)

                val imageView = ImageView(this).apply {
                    setImageDrawable(appIcon)
                    scaleType = ImageView.ScaleType.FIT_CENTER

                    if (two_row_frequent_apps) {
                        val paddingInPx = (5 * scale).toInt()
                        setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx)
                    }

                    // Launch your updated explicit activity system on click using only the package name
                    setOnClickListener {
                        launchApplicationActivity(pkgName)
                    }
                }

                // Push our programmatically built view node straight into the corresponding active parent container
                if (two_row_frequent_apps) {
                    val params = GridLayout.LayoutParams().apply {
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 0.2f) // 20% width grid cell distribution
                        width = 0 // Required for weight tracking parameters to distribute cleanly
                        height = sizeInPx
                    }
                    recentAppsGridContainer.addView(imageView, params)
                } else {
                    imageView.layoutParams = android.widget.LinearLayout.LayoutParams(sizeInPx, sizeInPx).apply {
                        setMargins(0, 0, (12 * scale).toInt(), 0) // Right margin separation spacing
                    }
                    recentItemsContainer.addView(imageView)
                }

            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                // Safe fallback logic: Handles edge-case where an app was tracked but since uninstalled from the system
                e.printStackTrace()
            }
        }
    }

    private fun populateSubCat(category: AppCategory, viewType: String) {
        val subCat = subCategoryMap[category] ?: return

        // 1. Get references
        val subMenuContainer = findViewById<LinearLayout>(R.id.SubCatMenuScreenContainer)
        val mainMenuContainer = findViewById<LinearLayout>(R.id.MainMenuScreenContainer)
        val recyclerView = findViewById<RecyclerView>(R.id.subCatRecyclerView)
        val iconView = findViewById<ImageView>(R.id.subCatIcon)
        val labelView = findViewById<TextView>(R.id.subCatLabel)

        // 2. Update Header (You might need to map category name to a master icon)
        labelView.text = getString(subCat.appCategory.stringResId)
        iconView.setImageResource(getIconForCategory(subCat.appCategory))

        val tmp = menuApps.find{ it.title == getString(subCat.appCategory.stringResId)}
        if(tmp!=null && tmp.iconDrawable!=null) {
            iconView.setImageDrawable(tmp.iconDrawable)
        }

        // 3. Switch Layouts
        // mainMenuContainer.visibility = View.GONE

        // 4. Configure Layout Manager (Grid vs List)
        if (viewType == "grid_view") {
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            recyclerView.layoutManager = GridLayoutManager(this, if (isLandscape) 4 else 3)
        } else {
            recyclerView.layoutManager = LinearLayoutManager(this)
        }

        // 5. Set the adapter
        // If you need a different layout for List view, pass a flag to your Adapter
        val selectedTitle = findViewById<TextView>(R.id.selectedCategoryTitle)
        recyclerView.adapter = GridAdapter(this,
            subCat.items,
            category_apps_labels_enabled,
            isListView=(viewType != "grid_view"),
            launchApp=true,
            onItemSelected={},
            onItemClicked={ clickedPackageName -> // This is the string passed by your Adapter
                launchApplicationActivity(clickedPackageName)
            },
            onItemLongClicked = { clickedPackageName ->
                val options = mutableListOf<String>().apply {
                    if (selectedLayout == "android") add("Add to home screen")
                    add("App info")
                }.toTypedArray()

                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(options.let {
                        // Show app name as dialog title
                        try {
                            packageManager.getApplicationLabel(
                                packageManager.getApplicationInfo(clickedPackageName, 0)
                            ).toString()
                        } catch (e: Exception) { clickedPackageName }
                    })
                    .setItems(options) { _, which ->
                        val addIndex = if (selectedLayout == "android") 0 else -1
                        when {
                            which == addIndex -> createShortcutView(clickedPackageName)
                            else -> {
                                // Open system App Info screen
                                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", clickedPackageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(intent)
                            }
                        }
                    }
                    .show()
                true
            }
        )

        subMenuContainer.alpha = 0f
        subMenuContainer.translationY = 650f // Slide starting position (50 pixels down)
        subMenuContainer.visibility = View.VISIBLE

        // 2. Animate: Fade to 1 and slide to original position (0)
        subMenuContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200L) // Adjust speed as needed
            .setListener(null)
    }

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost

    private var pendingWidgetId = -1
    private fun selectWidget() {
        pendingWidgetId = appWidgetHost.allocateAppWidgetId()

        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)

            // Mandatory empty collection parameters for the system picker
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, ArrayList<AppWidgetProviderInfo>())
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList<Bundle>())
        }
        startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Fall back to our saved class property if the system returned data bundle is empty
        val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
        Log.d("appWidgetId", "Processing Code: $requestCode | Result: $resultCode | Extracted ID: $appWidgetId")

        if (resultCode == Activity.RESULT_OK) {
            if (appWidgetId == -1) return

            when (requestCode) {
                REQUEST_PICK_APPWIDGET -> {
                    val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                    if (info != null) {
                        if (appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)) {
                            configureOrCreateWidget(appWidgetId, info)
                        } else {
                            // Persist the ID in our class tracker before stepping out to request permission
                            pendingWidgetId = appWidgetId

                            // SECURE BIND FIX: Modern Android requires explicit Provider and Profile extras
                            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                                // Passes the current user profile handle to bypass automatic system cancellation
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, android.os.Process.myUserHandle())
                            }
                            startActivityForResult(intent, REQUEST_BIND_APPWIDGET)
                        }
                    }
                }
                REQUEST_BIND_APPWIDGET -> {
                    val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                    if (info != null) {
                        configureOrCreateWidget(appWidgetId, info)
                    }
                }
                REQUEST_CREATE_APPWIDGET -> {
                    val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                    if (info != null) {
                        createWidgetView(appWidgetId, info)
                    }
                }
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            if (requestCode == REQUEST_BIND_APPWIDGET && appWidgetId != -1) {
                val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                if (info != null) {
                    Log.d("appWidgetId", "Fallback creation attempt triggered for ID: $appWidgetId")
                    configureOrCreateWidget(appWidgetId, info)
                    return
                }
            }

            // NEW: bail out cleanly if the user canceled the configure step
            if (requestCode == REQUEST_CREATE_APPWIDGET) {
                if (appWidgetId != -1) appWidgetHost.deleteAppWidgetId(appWidgetId)
                return
            }

            if (appWidgetId != -1) {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
            }
        }
    }

    private fun configureOrCreateWidget(appWidgetId: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            // Launch layout provider's individual settings panel explicitly
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            startActivityForResult(intent, REQUEST_CREATE_APPWIDGET)
        } else {
            createWidgetView(appWidgetId, info)
        }
    }

    private fun restoreShortcuts() {
        val widgetContainer = findViewById<FrameLayout>(R.id.widgetContainer)
        widgetContainer.post {
            for (state in WidgetStateStore.loadShortcuts(this)) {
                // Skip if app was uninstalled
                if (packageManager.getLaunchIntentForPackage(state.packageName) == null) continue
                createShortcutView(state.packageName, state.cellX, state.cellY)
            }
        }
    }

    private fun restoreWidgets() {
        val widgetContainer = findViewById<FrameLayout>(R.id.widgetContainer)
        widgetContainer.post {
            val cols = ANDROID_HOME_SCREEN_COLS
            val rows = ANDROID_HOME_SCREEN_ROWS
            val usableWidth = widgetContainer.measuredWidth -
                    widgetContainer.paddingLeft - widgetContainer.paddingRight
            val gridCellPx = usableWidth / cols

            for (state in WidgetStateStore.load(this)) {
                val info = appWidgetManager.getAppWidgetInfo(state.appWidgetId) ?: run {
                    // Widget provider is gone (app uninstalled), clean up the orphaned ID
                    appWidgetHost.deleteAppWidgetId(state.appWidgetId)
                    continue
                }

                val widgetThemedContext = androidx.appcompat.view.ContextThemeWrapper(
                    applicationContext,
                    com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Bridge
                )
                val hostView = appWidgetHost.createView(widgetThemedContext, state.appWidgetId, info)
                hostView.setAppWidget(state.appWidgetId, info)
                hostView.isClickable = false
                hostView.isLongClickable = false
                hostView.isFocusable = false

                val frame = WidgetFrame(this).apply {
                    cellX = state.cellX; cellY = state.cellY
                    cellW = state.cellW; cellH = state.cellH
                    configure(gridCellPx, cols, rows)
                }
                frame.addView(hostView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))

                val lp = FrameLayout.LayoutParams(
                    gridCellPx * state.cellW,
                    gridCellPx * state.cellH
                ).also {
                    it.leftMargin = state.cellX * gridCellPx
                    it.topMargin  = state.cellY * gridCellPx
                }

                frame.onRemoveRequested = {
                    appWidgetHost.deleteAppWidgetId(state.appWidgetId)
                    widgetContainer.removeView(frame)
                    ANDROID_HOME_SCREEN_widgetFrames.remove(state.appWidgetId)
                    persistWidgets()
                }
                frame.onLayoutChanged = { persistWidgets() }

                ANDROID_HOME_SCREEN_widgetFrames[state.appWidgetId] = frame
                widgetContainer.addView(frame, lp)
            }
        }
    }

    private fun createShortcutView(packageName: String, startCellX: Int = 0, startCellY: Int = 0) {
        // Guard against duplicates
        if (ANDROID_HOME_SCREEN_shortcutFrames.containsKey(packageName)) {
            Toast.makeText(this, "Shortcut already on home screen", Toast.LENGTH_SHORT).show()
            return
        }

        val widgetContainer = findViewById<FrameLayout>(R.id.widgetContainer)

        widgetContainer.post {
            val cols = ANDROID_HOME_SCREEN_COLS
            val rows = ANDROID_HOME_SCREEN_ROWS
            val usableWidth = widgetContainer.measuredWidth -
                    widgetContainer.paddingLeft - widgetContainer.paddingRight
            val gridCellPx = usableWidth / cols

            // Build the icon + label view that lives inside the frame
            val icon = getIconFromPack(this, selectedIconPack, packageName)
            val appName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            } catch (e: Exception) { packageName }

            val inner = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }

            val iconView = ImageView(this).apply {
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                val iconSizePx = (gridCellPx * 0.6f).toInt()
                layoutParams = android.widget.LinearLayout.LayoutParams(iconSizePx, iconSizePx)
            }

            val labelView = TextView(this).apply {
                text = appName
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(android.graphics.Color.WHITE)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            inner.addView(iconView)
            inner.addView(labelView)

            // Wire tap to launch (only when not in edit mode — WidgetFrame handles the rest)
            inner.setOnClickListener {
                launchApplicationActivity(packageName)
            }
            inner.isClickable = true
            inner.isLongClickable = false

            val frame = WidgetFrame(this).apply {
                cellX = startCellX; cellY = startCellY
                cellW = 1; cellH = 1          // always 1×1
                configure(gridCellPx, cols, rows)
            }

            frame.addView(inner, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            val lp = FrameLayout.LayoutParams(gridCellPx, gridCellPx).also {
                it.leftMargin = startCellX * gridCellPx
                it.topMargin  = startCellY * gridCellPx
            }

            frame.onRemoveRequested = {
                widgetContainer.removeView(frame)
                ANDROID_HOME_SCREEN_shortcutFrames.remove(packageName)
                persistShortcuts()
            }
            frame.onLayoutChanged = { persistShortcuts() }

            ANDROID_HOME_SCREEN_shortcutFrames[packageName] = frame
            widgetContainer.addView(frame, lp)
            persistShortcuts()
        }
    }

    private fun createWidgetView(appWidgetId: Int, info: AppWidgetProviderInfo) {
        if (appWidgetId <= 0) {
            Log.w("appWidgetId", "Skipping createWidgetView — invalid ID: $appWidgetId")
            return
        }

        // Guard: already on screen (e.g. restored from persistence)
        if (ANDROID_HOME_SCREEN_widgetFrames.containsKey(appWidgetId)) {
            Log.w("appWidgetId", "Skipping createWidgetView — widget $appWidgetId already exists")
            return
        }

        val widgetThemedContext = ContextThemeWrapper(
            applicationContext,
            com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Bridge
        )

        val hostView = appWidgetHost.createView(widgetThemedContext, appWidgetId, info)
        hostView.setAppWidget(appWidgetId, info)

        val widgetContainer = findViewById<FrameLayout>(R.id.widgetContainer)

        widgetContainer.post {
            val cols = ANDROID_HOME_SCREEN_COLS // 4
            val rows = ANDROID_HOME_SCREEN_ROWS // 6
            // Use measuredWidth minus padding so widget fits inside the container
            val usableWidth = widgetContainer.measuredWidth - widgetContainer.paddingLeft - widgetContainer.paddingRight
            val gridCellPx = usableWidth / cols

            val frame = WidgetFrame(this).apply {
                cellX = 0; cellY = 0; cellW = 2; cellH = 2
                configure(gridCellPx, cols, rows)
            }

            // hostView fills the frame but must not intercept touch
            hostView.isClickable = false
            hostView.isLongClickable = false
            hostView.isFocusable = false

            frame.addView(hostView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            )

            val lp = FrameLayout.LayoutParams(
                gridCellPx * frame.cellW,
                gridCellPx * frame.cellH
            ).also {
                it.leftMargin = frame.cellX * gridCellPx
                it.topMargin  = frame.cellY * gridCellPx
            }

            frame.onRemoveRequested = {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
                widgetContainer.removeView(frame)
                ANDROID_HOME_SCREEN_widgetFrames.remove(appWidgetId)   // ← remove from tracker
                persistWidgets()                   // ← save after removal
            }

            frame.onLayoutChanged = { persistWidgets() }

            ANDROID_HOME_SCREEN_widgetFrames[appWidgetId] = frame      // ← register in tracker
            widgetContainer.addView(frame, lp)
            persistWidgets()
        }
    }

    // Call this if the user deletes/removes a widget from the home layout
    private fun removeWidget(appWidgetId: Int, hostView: AppWidgetHostView) {
        appWidgetHost.deleteAppWidgetId(appWidgetId)
        var widgetContainer = findViewById<FrameLayout>(R.id.widgetContainer)
        widgetContainer.removeView(hostView)
    }

    private fun openMainMenu() {
        val homeScreen = findViewById<ConstraintLayout>(R.id.homeScreenContainer)
        val menuScreen = findViewById<ConstraintLayout>(R.id.menuScreenContainer)
        val centerMenuButton = findViewById<ImageButton>(R.id.centerMenuButton)
        val toggleDrawable = centerMenuButton.drawable as? android.graphics.drawable.TransitionDrawable
        val recyclerView = findViewById<RecyclerView>(R.id.menuRecyclerView)

        crossfadeMenu(fadeOutView = homeScreen, fadeInView = menuScreen)
        toggleDrawable?.startTransition(250)
        isMenuOpen = true

        //populateRecentApps() // Usage of this function here slows down the opening of the main menu. It should be ran after an app is launched.

        // FORCE ANIMATION: Tell the grid to refresh and run onBindViewHolder animations
        // recyclerView.adapter?.notifyDataSetChanged()
        when(selectedMainMenuOpenAnimation) {
            "zoom" -> {
                (recyclerView.adapter as? GridAdapter)?.zoomAnimate(recyclerView)
            }
            "emerge" -> {
                (recyclerView.adapter as? GridAdapter)?.emergeAnimate(recyclerView)
            }
        }

        if (dpad_enabled) {
            val messagesPosition = 4

            recyclerView.post {
                val holder = recyclerView.findViewHolderForAdapterPosition(messagesPosition)
                holder?.itemView?.requestFocus()
            }
        }
    }

    private fun exitMenu() {
        val homeScreen = findViewById<ConstraintLayout>(R.id.homeScreenContainer)
        val menuScreen = findViewById<ConstraintLayout>(R.id.menuScreenContainer)
        val centerMenuButton = findViewById<ImageButton>(R.id.centerMenuButton)
        val toggleDrawable = centerMenuButton.drawable as? android.graphics.drawable.TransitionDrawable

        crossfadeMenu(fadeOutView = menuScreen, fadeInView = homeScreen)
        toggleDrawable?.reverseTransition(250)
        isMenuOpen = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // Check if the incoming intent is a standard Home screen launch trigger
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {

            val subMenuContainer = findViewById<LinearLayout>(R.id.SubCatMenuScreenContainer)

            // 1. If a subcategory overlay is open, dismiss it back to the main menu grid
            if (subMenuContainer.visibility == View.VISIBLE) {
                subMenuContainer.visibility = View.GONE
            }
            // 2. If the main menu grid is open, collapse it back to the home layout
            else if (isMenuOpen) {
                exitMenu()
            }
        }
    }

    private fun assignDefaultApps() {
        val pm = packageManager

        // 1. Resolve Default SMS/Messaging App Safely
        try {
            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            val resolveInfo = pm.resolveActivity(smsIntent, 0)
            defaultMessagingApp = resolveInfo?.activityInfo?.packageName ?: ""
        } catch (e: Exception) {
            defaultMessagingApp = ""
        }

        // 2. Resolve Default Phone/Dialer App Safely
        try {
            val dialerIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
            val resolveInfo = pm.resolveActivity(dialerIntent, 0)
            defaultPhoneCallingApp = resolveInfo?.activityInfo?.packageName ?: ""
        } catch (e: Exception) {
            defaultPhoneCallingApp = ""
        }

        // 3. Resolve Contacts App Safely
        try {
            val contactsIntent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
            val resolveInfo = pm.resolveActivity(contactsIntent, 0)
            defaultContactsApp = resolveInfo?.activityInfo?.packageName ?: ""
        } catch (e: Exception) {
            defaultContactsApp = ""
        }

        // 🟢 Robust Fallbacks if the device has no default set or fails resolution
        if (defaultMessagingApp.isEmpty() || defaultMessagingApp == "android") {
            defaultMessagingApp = "com.google.android.apps.messaging"
        }
        if (defaultPhoneCallingApp.isEmpty() || defaultPhoneCallingApp == "android") {
            defaultPhoneCallingApp = "com.google.android.dialer"
        }
        if (defaultContactsApp.isEmpty() || defaultContactsApp == "android") {
            defaultContactsApp = "com.google.android.contacts"
        }

        Log.d("MnemosyneLauncher", "Mapped Defaults - SMS: $defaultMessagingApp | Phone: $defaultPhoneCallingApp | Contacts: $defaultContactsApp")
    }

    fun hideMenuHighlight() {
        // Smoothly fade out the highlight frame instead of dropping it instantly
        menuGridHighlight.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                // Ensure visibility is toggled off completely once hidden
                // to stop it from handling ghost taps or blocked view frames
                menuGridHighlight.visibility = View.INVISIBLE
            }
            .start()
    }

    fun moveHighlightToView(targetView: View) {
        // 1. Calculate the expanded size (1.3x bigger than the grid cell item)
        val scaleFactor = 1.15f
        val targetWidth = (targetView.width * scaleFactor).toInt()
        val targetHeight = (targetView.height * scaleFactor).toInt()

        // Update layout params to hold the scaled up size bounding box
        val layoutParams = menuGridHighlight.layoutParams
        if (layoutParams.width != targetWidth || layoutParams.height != targetHeight) {
            layoutParams.width = targetWidth
            layoutParams.height = targetHeight
            menuGridHighlight.layoutParams = layoutParams
        }

        // 2. Fetch screen coordinate vectors relative to the parent category container
        val container = findViewById<ViewGroup>(R.id.app_category)
        val targetRect = android.graphics.Rect()
        val containerRect = android.graphics.Rect()

        targetView.getGlobalVisibleRect(targetRect)
        container.getGlobalVisibleRect(containerRect)

        // 3. Compute base alignment position coordinates
        var targetX = (targetRect.left - containerRect.left).toFloat()
        var targetY = (targetRect.top - containerRect.top).toFloat()

        // 4. CENTER THE HIGHLIGHT: Offset the coordinates by half of the extra size extension
        val widthDiff = targetWidth - targetView.width
        val heightDiff = targetHeight - targetView.height

        targetX -= (widthDiff / 2f)
        targetY -= (heightDiff / 2f)

        // 5. Fire your smooth coordinate translation transitions
        if (menuGridHighlight.visibility != View.VISIBLE) {
            // First selection instant snap placement
            menuGridHighlight.translationX = targetX
            menuGridHighlight.translationY = targetY
            menuGridHighlight.alpha = 0f
            menuGridHighlight.visibility = View.VISIBLE
            menuGridHighlight.animate()
                .alpha(1f)
                .setDuration(120)
                .start()
        } else {
            // Continuous fluid gliding movement between focus states
            menuGridHighlight.animate()
                .translationX(targetX)
                .translationY(targetY)
                .setDuration(200) // 200ms feels ideal for retro digital dashboard setups
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    private fun updateDynamicDockShortcuts() {
        val sharedPrefs = getSharedPreferences("com.mnemosynesuite.mnemosynelauncher_preferences", Context.MODE_PRIVATE)
        val pm = packageManager

        // 1. Locate your shortcut text components from activity_main.xml
        val leftShortcut = findViewById<TextView>(R.id.leftShortcut)
        val rightShortcut = findViewById<TextView>(R.id.rightShortcut)

        // ---- CONFIGURE LEFT SHORTCUT TEXT ----
        if (leftPkg != null && leftPkg != "default") {
            try {
                val appInfo = pm.getApplicationInfo(leftPkg, 0)
                val appLabel = pm.getApplicationLabel(appInfo).toString()

                leftShortcut.text = appLabel
                leftShortcut.setOnClickListener {
                    val launchIntent = pm.getLaunchIntentForPackage(leftPkg)
                    launchIntent?.let { startActivity(it) }
                }
            } catch (e: Exception) {
                setDefaultLeftDockBehavior(leftShortcut)
            }
        } else {
            setDefaultLeftDockBehavior(leftShortcut)
        }

        // ---- CONFIGURE RIGHT SHORTCUT TEXT ----
        if (rightPkg != null && rightPkg != "default") {
            try {
                val appInfo = pm.getApplicationInfo(rightPkg, 0)
                val appLabel = pm.getApplicationLabel(appInfo).toString()

                rightShortcut.text = appLabel
                rightShortcut.setOnClickListener {
                    val launchIntent = pm.getLaunchIntentForPackage(rightPkg)
                    launchIntent?.let { startActivity(it) }
                }
            } catch (e: Exception) {
                setDefaultRightDockBehavior(rightShortcut)
            }
        } else {
            setDefaultRightDockBehavior(rightShortcut)
        }
    }

    // ---- FALLBACK DEFAULT ACTIONS ----
    private fun setDefaultLeftDockBehavior(textView: TextView) {
        // Restores default plain text label
        textView.text = "Call"
        textView.setOnClickListener {
            // Your launcher's original hardcoded left button click action loop
        }
    }

    private fun setDefaultRightDockBehavior(textView: TextView) {
        // Restores original text layout state ("Viber")
        textView.text = "Viber"
        textView.setOnClickListener {
            // Your launcher's original hardcoded right button click action loop
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_MnemosyneLauncher_SonyEricsson2006)

        super.onCreate(savedInstanceState)

        assignDefaultApps()
        frequentPackages=getSortedFrequentlyUsedApps()

        enableEdgeToEdge()
        window.statusBarColor = android.graphics.Color.parseColor("#20293a")
        setContentView(R.layout.activity_main)

        updateDynamicDockShortcuts()

        menuApps = listOf(
            GridItem(getString(R.string.menu_item_appstore), R.drawable.menuicon_playstore, packageName="com.android.vending",
                iconDrawable = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_playstore"),
                iconDrawableHover = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_playstore_hover")
            ),
            GridItem(getString(AppCategory.INTERNET.stringResId),  R.drawable.menuicon_internet,
                iconDrawable = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_internet"),
                iconDrawableHover = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_internet_hover")
            ),
            GridItem(getString(AppCategory.GAMES.stringResId),     R.drawable.menuicon_games,     packageName="",
                iconDrawable = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_games"),
                iconDrawableHover = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_games_hover")
            ),

            GridItem(getString(R.string.menu_item_camera),    R.drawable.menuicon_camera,    packageName="com.google.android.GoogleCamera",
                iconDrawable = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_camera"),
                iconDrawableHover = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_camera_hover")
            ),
            GridItem(getString(R.string.menu_item_messages),  R.drawable.menuicon_messages,
                iconDrawable = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_messages"),
                iconDrawableHover = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_messages_hover")
            ),
            GridItem(getString(R.string.menu_category_media),     R.drawable.menuicon_media,     packageName="",
                iconDrawable = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_media"),
                iconDrawableHover = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_media_hover")
            ),

            GridItem(getString(R.string.menu_item_files),     R.drawable.menuicon_files,     packageName="",
                iconDrawable = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_files"),
                iconDrawableHover = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_files_hover")
            ),
            GridItem(getString(R.string.menu_item_contacts),  R.drawable.menuicon_contacts,
                iconDrawable = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_contacts"),
                iconDrawableHover = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_contacts_hover")
            ),
            GridItem(getString(R.string.menu_item_music),     R.drawable.menuicon_music,     packageName="",
                iconDrawable = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_music"),
                iconDrawableHover = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_music_hover")
            ),

            GridItem(getString(AppCategory.COMMUNICATIONS.stringResId),     R.drawable.menuicon_comm,      packageName="",
                iconDrawable = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_comm"),
                iconDrawableHover = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_comm_hover")
            ),
            GridItem(getString(AppCategory.ORGANIZER.stringResId),  R.drawable.menuicon_calendar,
                iconDrawable = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_calendar"),
                iconDrawableHover = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_calendar_hover")
            ),
            GridItem(getString(AppCategory.SETTINGS.stringResId),  R.drawable.menuicon_settings,
                iconDrawable = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_settings"),
                iconDrawableHover = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_menu_settings_hover")
            )
        )

        Log.d("menuApps", menuApps.toString())

        val bottomDock = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.bottomDock)
        bottomDock.setBackgroundResource(R.drawable.bottom_dock_bk)

        // In MainActivity.kt inside onCreate:
        val categorizedApps = categorizeApps(this)

        menuGridHighlight = findViewById(R.id.menuGridHighlight)

        val subMenuContainer = findViewById<LinearLayout>(R.id.SubCatMenuScreenContainer)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isMenuOpen) {
                    if (subMenuContainer.visibility == View.VISIBLE) {
                        closeSubCatMenu()
                    } else {
                        exitMenu()
                    }
                } else {
                    // If the menu is completely closed and the user is on the home layout,
                    // do nothing (or match normal system launcher behavior where back does nothing)
                }
            }
        })

        categorizedApps.forEach { (category, items) ->
            Log.d("AppCategorization", "Category: $category")
            items.forEach { item ->
                Log.d("AppCategorization", " - App: ${item.title}")
            }
        }

        // Transform the Map values: List<GridItem> -> SubCategory
        subCategoryMap = categorizedApps.mapValues { (appCategory, items) ->
            SubCategory(appCategory,items)
        }

        // Find our view containers from the layout
        val homeScreen = findViewById<ConstraintLayout>(R.id.homeScreenContainer)
        val menuScreen = findViewById<ConstraintLayout>(R.id.menuScreenContainer)
        val centerMenuButton = findViewById<ImageButton>(R.id.centerMenuButton)

        // 1. Fetch the custom drawables from the selected icon pack
        var menuIcon = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_home_launcher")
        var homeIcon = getIconFromPackByDrawableName(this, selectedIconPack, "mnemosynesuite_launcher_home_home")

        if (menuIcon != null && homeIcon != null) {
            // 2. Pack them into a layered array (Index 0 = Start/Menu, Index 1 = End/Home)
            val layers = arrayOf(menuIcon, homeIcon)
            val dynamicToggleDrawable = android.graphics.drawable.TransitionDrawable(layers)

            // Ensure the layers remain centered exactly like your static layout templates
            dynamicToggleDrawable.isCrossFadeEnabled = true

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                dynamicToggleDrawable.setLayerGravity(0, android.view.Gravity.CENTER)
                dynamicToggleDrawable.setLayerGravity(1, android.view.Gravity.CENTER)
            }

            // 3. Swap out the XML src asset with your runtime dynamic version
            centerMenuButton.setImageDrawable(dynamicToggleDrawable)
        }

        var scale = resources.displayMetrics.density
        val paddingInPxCMB = (10 * scale).toInt()
        centerMenuButton.setPadding(paddingInPxCMB, paddingInPxCMB, paddingInPxCMB, paddingInPxCMB)

        val toggleDrawable = centerMenuButton.drawable as? android.graphics.drawable.TransitionDrawable

        setupHomeScreenLayout()
        toggleDrawable?.isCrossFadeEnabled = true

        val recentAppsBarLayout = findViewById<ConstraintLayout>(R.id.recent_apps_bar)
        val recyclerView = findViewById<RecyclerView>(R.id.menuRecyclerView)

        scale = resources.displayMetrics.density
        val paddingInPx = (10 * scale).toInt()

        if (frequent_apps_bar_enabled) {
            recentAppsBarLayout.visibility = View.VISIBLE
        } else {
            recentAppsBarLayout.visibility = View.GONE
        }

        if(!selected_app_label_enabled) {
            val selectedCategoryTitle = findViewById<TextView>(R.id.selectedCategoryTitle)
            selectedCategoryTitle.visibility = View.GONE
        }

        if(!category_apps_labels_enabled && !selected_app_label_enabled) {
            recyclerView.setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx)
        }

        // Set the click listener to toggle between screens with a crossfade
        centerMenuButton.setOnClickListener {
            if (!isMenuOpen) {
                openMainMenu()
            } else {
                exitMenu()
            }
        }

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APP_WIDGET_HOST_ID)
        restoreWidgets()
        restoreShortcuts()

        val androidHomeScreen = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.layout_android)

        // Set the hold-tap listener
        androidHomeScreen.setOnLongClickListener {
            val options = arrayOf("Add Widget", "Change Settings", "Cancel")

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Homescreen Options")
                .setItems(options) { dialog, which ->
                    when (which) {
                        0 -> selectWidget() // Launches widget selection flow
                        1 -> {
                            val intent = Intent(this, SettingsActivity::class.java)
                            startActivity(intent)
                        }
                        2 -> dialog.dismiss()
                    }
                }
                .show()
            true
        }

        val timeDateHomeScreen = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.layout_timedate_simple)

        // Set the hold-tap listener
        timeDateHomeScreen.setOnLongClickListener {
            val options = arrayOf("Change Settings", "Cancel")

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Homescreen Options")
                .setItems(options) { dialog, which ->
                    when (which) {
                        0 -> {
                            val intent = Intent(this, SettingsActivity::class.java)
                            startActivity(intent)
                        }
                        1 -> dialog.dismiss()
                    }
                }
                .show()
            true
        }

        populateMenu();

        // Handles default padding for system status bars and camera notches
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            closeSubCatMenu()
        }
    }

    private fun closeSubCatMenu() {
        val subMenuContainer = findViewById<LinearLayout>(R.id.SubCatMenuScreenContainer)
        val mainMenuContainer = findViewById<LinearLayout>(R.id.MainMenuScreenContainer)

        subMenuContainer.visibility = View.GONE
        mainMenuContainer.visibility = View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        // 2. Start listening for widget updates (crucial for updating clock/weather info)

        appWidgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        // 3. Stop listening when the activity is not visible to preserve battery
        appWidgetHost.stopListening()
    }

    /**
     * Smoothly crossfades between two views over 250 milliseconds.
     */
    private fun crossfadeMenu(fadeOutView: View, fadeInView: View) {
        val animDuration = 250L

        // 1. Prepare incoming screen (invisible but placed on layout)
        fadeInView.alpha = 0f
        fadeInView.visibility = View.VISIBLE

        // 2. Animate incoming screen to fully opaque
        fadeInView.animate()
            .alpha(1f)
            .setDuration(animDuration)
            .setListener(null)

        // 3. Animate outgoing screen to fully transparent, then completely hide it
        fadeOutView.animate()
            .alpha(0f)
            .setDuration(animDuration)
            .withEndAction {
                fadeOutView.visibility = View.GONE
                val subMenuContainer = findViewById<LinearLayout>(R.id.SubCatMenuScreenContainer)
                subMenuContainer.visibility = View.GONE
            }
    }

    fun categorizeApps(context: Context): Map<AppCategory, List<GridItem>> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val categories = mutableMapOf(
            AppCategory.COMMUNICATIONS     to mutableListOf<GridItem>(),
            AppCategory.MEDIA     to mutableListOf<GridItem>(),
            AppCategory.ORGANIZER to mutableListOf<GridItem>(),
            AppCategory.INTERNET  to mutableListOf<GridItem>(),
            AppCategory.GAMES     to mutableListOf<GridItem>(),
            AppCategory.SETTINGS    to mutableListOf<GridItem>()
        )

        pm.queryIntentActivities(mainIntent, 0).forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val appName = resolveInfo.loadLabel(pm).toString()
            val appIcon = getIconFromPack(context, selectedIconPack, packageName)
            val item = GridItem(appName, 0, appIcon, packageName=packageName)

            val appInfo = try {
                pm.getApplicationInfo(packageName, 0)
            } catch (e: Exception) {
                categories[AppCategory.SETTINGS]?.add(item)
                return@forEach
            }

            val pName = packageName.lowercase()

            // 1. Core global overrides across ALL API levels
            val bucket = when {
                pName == "com.google.android.dialer" ||
                        pName == "com.android.dialer" ||
                        pName == defaultPhoneCallingApp.lowercase() -> AppCategory.COMMUNICATIONS

                pName == "com.google.android.apps.messaging" ||
                        pName == "com.android.mms" ||
                        pName == defaultMessagingApp.lowercase() -> AppCategory.COMMUNICATIONS

                pName == "com.google.android.contacts" ||
                        pName == "com.android.contacts" ||
                        pName == defaultContactsApp.lowercase() -> AppCategory.COMMUNICATIONS

                pName == "com.android.chrome" ||
                        pName == "org.mozilla.firefox" ||
                        pName == "com.google.android.googlequicksearchbox" ||
                        pName == "com.google.android.gm" ||
                        pName == "com.google.android.apps.maps" ||
                        pName == "com.google.android.youtube" -> AppCategory.INTERNET

                // 2. API Level Split
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    // Android 8.0+ safe property compilation check
                    when (appInfo.category) {
                        ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.COMMUNICATIONS

                        ApplicationInfo.CATEGORY_AUDIO,
                        ApplicationInfo.CATEGORY_VIDEO,
                        ApplicationInfo.CATEGORY_IMAGE         -> AppCategory.MEDIA

                        ApplicationInfo.CATEGORY_PRODUCTIVITY  -> AppCategory.ORGANIZER

                        ApplicationInfo.CATEGORY_MAPS,
                        ApplicationInfo.CATEGORY_NEWS          -> AppCategory.INTERNET

                        ApplicationInfo.CATEGORY_GAME          -> AppCategory.GAMES

                        else                                   -> AppCategory.SETTINGS
                    }
                }

                else -> {
                    // Android 7.1 and lower fallback routine using string classification rules
                    when {
                        pName.equals("ru.woesss.j2meloader") -> AppCategory.GAMES
                        // Messaging & Social packages
                        pName.contains("whatsapp") || pName.contains("viber") ||
                                pName.contains("telegram") || pName.contains("facebook") ||
                                pName.contains("instagram") || pName.contains("twitter") ||
                                pName.contains("discord") || pName.contains("skype") -> AppCategory.COMMUNICATIONS

                        // System Utilities & Tools -> Organizer
                        pName.contains("calendar") || pName.contains("deskclock") ||
                                pName.contains("calculator") || pName.contains("notes") ||
                                pName.contains("documents") || pName.contains("download") ||
                                pName.contains("setting") || pName.contains("gallery") -> AppCategory.ORGANIZER

                        // Audio, Video & Camera -> Media
                        pName.contains("music") || pName.contains("player") ||
                                pName.contains("camera") || pName.contains("video") ||
                                pName.contains("audio") || pName.contains("spotify") ||
                                pName.contains("recorder") || pName.contains("mnemosynemusicplayer") -> AppCategory.MEDIA

                        // Web & Navigation -> CAT_INTERNET
                        pName.contains("browser") || pName.contains("mail") ||
                                pName.contains("weather") -> AppCategory.INTERNET

                        else -> AppCategory.SETTINGS
                    }
                }
            }

            categories[bucket]?.add(item)
        }
        return categories
    }

    fun getAllInstalledApps(context: Context): List<AppEntry> {
        val pm = context.packageManager

        // 1. Create the intent exactly like a standard launcher
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // 2. Query with 0 flags to get ALL launchable apps, not just default ones
        val activities = pm.queryIntentActivities(mainIntent, 0)

        Log.d("AppCategorization", "Total apps found by system: ${activities.size}")

        return activities.map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val label = resolveInfo.loadLabel(pm).toString()

            Log.d("AppCategorization", "Found: $label | Package: $packageName")

            AppEntry(
                name = label,
                packageName = packageName,
                icon = resolveInfo.loadIcon(pm)
            )
        }
    }
}