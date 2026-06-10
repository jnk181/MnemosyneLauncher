package com.mnemosynesuite.mnemosynelauncher

import android.graphics.drawable.Drawable

data class GridItem(
    val title: String,
    val iconResId: Int,
    val iconDrawable: Drawable? = null,
    val iconDrawableHover: Drawable? = null,
    val packageName: String? = null
)