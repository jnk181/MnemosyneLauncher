package com.mnemosynesuite.mnemosynelauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner

data class SettingItemConfig(
    val title: String,
    val description: String,
    val prefKey: String,
    val defaultValue: Any,
    val type: String,
    val options: List<SettingOption>? = null // 🟢 Updated from List<String>?
)

data class SettingOption(
    val label: String,  // What the user sees (e.g., "Timedate simple")
    val value: String   // What gets saved in SharedPreferences (e.g., "timedate_simple")
) {
    // 🟢 Overriding toString tells the Spinner's ArrayAdapter exactly what text to draw
    override fun toString(): String = label
}

class SettingsActivity : AppCompatActivity() {

    private lateinit var installedAppsOptions: List<SettingOption>

    fun getInstalledIconPacks(): List<SettingOption> {
        val pm = packageManager
        val iconPacks = mutableListOf<SettingOption>()

        // Always insert the standard "Default" fallback system option at the top
        iconPacks.add(SettingOption(label = "Default", value = "default"))

        // Standard intent actions used by almost all icon packs on the Play Store
        val intentActions = arrayOf(
            "org.adw.launcher.THEMES",
            "com.gau.go.launcherex.theme"
        )

        val packagesSeen = mutableSetOf<String>()

        for (action in intentActions) {
            val intent = Intent(action)
            val resolveInfos = pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)

            for (ri in resolveInfos) {
                val packageName = ri.activityInfo.packageName
                if (!packagesSeen.contains(packageName)) {
                    packagesSeen.add(packageName)
                    val label = ri.loadLabel(pm).toString()
                    iconPacks.add(SettingOption(label = label, value = packageName))
                }
            }
        }

        return iconPacks
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        installedAppsOptions = mutableListOf<SettingOption>().apply {
            add(SettingOption("None / Default", "default"))

            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

            // Sort apps alphabetically by name to make navigation clean
            val sortedApps = resolveInfos.map {
                SettingOption(label = it.loadLabel(pm).toString(), value = it.activityInfo.packageName)
            }.sortedBy { it.label.lowercase() }

            addAll(sortedApps)
        }

        val backButton = findViewById<ImageButton>(R.id.backButton)
        val sharedPrefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        val settingsContainer = findViewById<LinearLayout>(R.id.settingsContainer)

        backButton.setOnClickListener { finish() }

        val restartButton = findViewById<Button>(R.id.restartLauncherButton)
        restartButton.setOnClickListener {
            val packageManager = packageManager
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                // Clear the entire activity back-stack so the launcher refreshes cleanly
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)

                // Kill the current instance process path
                Runtime.getRuntime().exit(0)
            }
        }

        // 1. Define your array with explicit types
        val settingsList = listOf(
            SettingItemConfig(
                title = "D-pad mode",
                description = "Enable directional pad UI optimization",
                prefKey = "dpad_enabled",
                defaultValue = false,
                type = "bool" // 🟢 Kept and processed
            ),
            SettingItemConfig(
                title = "Main menu top selected label",
                description = "Shows label of the selected item on the top of the main menu",
                prefKey = "mainmenu_selected_app_label_enabled",
                defaultValue = false,
                type = "bool" // 🟢 Kept and processed
            ),
            SettingItemConfig(
                title = "Main menu frequently used apps",
                description = "Show frequently used apps bar at the top of the main menu",
                prefKey = "frequent_apps_bar_enabled",
                defaultValue = false,
                type = "bool" // 🟢 Kept and processed
            ),
            SettingItemConfig(
                title = "Two-row frequently used apps",
                description = "Show frequently used apps bar in two rows",
                prefKey = "two_row_frequent_apps",
                defaultValue = false,
                type = "bool" // 🟢 Kept and processed
            ),
            SettingItemConfig(
                title = "Main menu labels",
                description = "Show labels under icons in the main menu",
                prefKey = "category_apps_labels_enabled",
                defaultValue = false,
                type = "bool" // 🟢 Kept and processed
            ),
            SettingItemConfig(
                title = "Home screen layout",
                description = "Choose the home screen layout",
                prefKey = "home_screen_layout",
                defaultValue = "timedate_simple", // 🟢 Default fallback value if nothing is saved yet
                type = "enum",
                options = listOf(
                    SettingOption(label = "Timedate simple", value = "timedate_simple"),
                    SettingOption(label = "Android", value = "android")
                )// 🟢 The options that will show in your Spinner
            ),
            SettingItemConfig(
                title = "Grid menu icon padding",
                description = "Adjust the padding space around icons in the grid menu",
                prefKey = "grid_menu_padding_factor",
                defaultValue = 3, // 🟢 Default fallback value if nothing is saved yet
                type = "int"
            ),
            SettingItemConfig(
                title = "Icon pack",
                description = "Choose the icon pack mapping for menu grids",
                prefKey = "chosen_icon_pack",
                defaultValue = "default",
                type = "enum",
                options = getInstalledIconPacks()
            ),
            SettingItemConfig(
                title = "Main menu open animation",
                description = "Choose the main menu open animation",
                prefKey = "main_menu_open_animation",
                defaultValue = "zoom", // 🟢 Default fallback value if nothing is saved yet
                type = "enum",
                options = listOf(
                    SettingOption(label = "Zoom", value = "zoom"),
                    SettingOption(label = "Emerge", value = "emerge")
                )// 🟢 The options that will show in your Spinner
            ),
            SettingItemConfig(
                title = "Left Dock Button Action",
                description = "Select which application opens when pressing the left dock hotkey",
                prefKey = "dock_left_package",
                defaultValue = "default",
                type = "enum",
                options = installedAppsOptions
            ),
            SettingItemConfig(
                title = "Right Dock Button Action",
                description = "Select which application opens when pressing the right dock hotkey",
                prefKey = "dock_right_package",
                defaultValue = "default",
                type = "enum",
                options = installedAppsOptions
            )
        )

        val inflater = LayoutInflater.from(this)

        // 2. Filter for "bool" types only, then dynamically inflate view_setting_item
        settingsList.forEach { config ->
            val rowView: View

            when (config.type) {
                "bool" -> {
                    rowView = inflater.inflate(R.layout.view_setting_item_checkbox, settingsContainer, false)
                    val checkbox = rowView.findViewById<CheckBox>(R.id.settingCheckbox)

                    checkbox.isChecked = sharedPrefs.getBoolean(config.prefKey, config.defaultValue as Boolean)
                    checkbox.setOnCheckedChangeListener { _, isChecked ->
                        sharedPrefs.edit().putBoolean(config.prefKey, isChecked).apply()
                    }
                }
                "enum" -> {
                    rowView = inflater.inflate(R.layout.view_setting_item_combobox, settingsContainer, false)
                    val spinner = rowView.findViewById<Spinner>(R.id.settingSpinner)

                    if (config.options != null) {
                        // 1. The adapter automatically hooks into our SettingOption.toString() override
                        val adapter = ArrayAdapter(
                            this,
                            android.R.layout.simple_spinner_item,
                            config.options
                        ).apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                        spinner.adapter = adapter

                        // 2. Fetch saved string, then locate which item inside config.options matches that raw value key
                        val savedValue = sharedPrefs.getString(config.prefKey, config.defaultValue as String)
                        val defaultIndex = config.options.indexOfFirst { it.value == savedValue }.coerceAtLeast(0)
                        spinner.setSelection(defaultIndex)

                        // 3. Save only the underlying machine string back to your preference file
                        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                val selectedOption = config.options[position]
                                sharedPrefs.edit().putString(config.prefKey, selectedOption.value).apply() // 🟢 Saves "android" instead of "Android"
                            }
                            override fun onNothingSelected(parent: AdapterView<*>?) {}
                        }
                    }
                }
                "int" -> {
                    rowView = inflater.inflate(R.layout.view_setting_item_number, settingsContainer, false)
                    val numberInput = rowView.findViewById<EditText>(R.id.settingNumberInput)

                    // 1. Grab current integer state or fallback to default value configuration safely
                    val savedIntValue = sharedPrefs.getInt(config.prefKey, config.defaultValue as Int)
                    numberInput.setText(savedIntValue.toString())

                    // 2. Commit modifications automatically whenever the user types a new value
                    numberInput.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                        override fun afterTextChanged(s: Editable?) {
                            val inputString = s.toString()
                            if (inputString.isNotEmpty()) {
                                try {
                                    val parsedValue = inputString.toInt()
                                    sharedPrefs.edit().putInt(config.prefKey, parsedValue).apply()
                                } catch (e: NumberFormatException) {
                                    // Prevent app crashes if integer overflow boundaries are tested
                                }
                            } else {
                                // Safe fallback if user leaves the field completely blank temporarily
                                sharedPrefs.edit().putInt(config.prefKey, config.defaultValue as Int).apply()
                            }
                        }
                    })
                }
                else -> return@forEach // Ignore unknown variations cleanly
            }

            // Set common Text Labels dynamically
            rowView.findViewById<TextView>(R.id.settingTitle).text = config.title
            rowView.findViewById<TextView>(R.id.settingDescription).text = config.description

            settingsContainer.addView(rowView)
        }
    }
}