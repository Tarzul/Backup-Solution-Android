package com.rezerv.upload.ui

import android.view.View
import android.widget.Button
import com.rezerv.upload.R

class TabManager(
    private val tabs: Map<Int, View>,
    private val buttons: Map<Int, Button>
) {
    private var currentTab = 0

    fun switchTo(tab: Int, onSwitch: ((Int) -> Unit)? = null) {
        // Hide all
        tabs.values.forEach { it.visibility = View.GONE }

        // Reset buttons
        buttons.values.forEach { btn ->
            btn.setBackgroundResource(R.drawable.bg_button_secondary)
            btn.setTextColor(0xFFE0E0E0.toInt())
        }

        // Show selected
        tabs[tab]?.visibility = View.VISIBLE
        buttons[tab]?.let { btn ->
            btn.setBackgroundResource(R.drawable.bg_button_primary)
            btn.setTextColor(0xFF000000.toInt())
        }

        currentTab = tab
        onSwitch?.invoke(tab)
    }

    fun isTabVisible(tab: Int) = tabs[tab]?.visibility == View.VISIBLE
    fun getCurrentTab() = currentTab
}