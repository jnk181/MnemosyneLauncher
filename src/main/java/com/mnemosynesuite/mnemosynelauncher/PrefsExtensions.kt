package com.mnemosynesuite.mnemosynelauncher

import android.content.Context

// Global Context extension property
var Context.dpadEnabled: Boolean
    get() = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        .getBoolean("dpad_enabled", false)
    set(value) {
        getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("dpad_enabled", value)
            .apply()
    }