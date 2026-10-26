package com.android.systemui.statusbar.phone.island.compose

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

enum class IconPackStyle {
    SYSTEM,
    HYPEROS
}

data class IslandConfig(
    val xOffsetDp: Float = 0f,
    val yOffsetDp: Float = 0f,
    val pillWidthDp: Float = 110f,
    val pillHeightDp: Float = 34f,
    val notchMarginDp: Float = 5f,
    val showGuide: Boolean = false,
    val springDamping: Float = 0.68f,
    val springStiffness: Float = 260f,
    val fireworksIntensity: Float = 1.0f,
    val activityPriorities: List<String> = defaultPriorities,
    val iconPackStyle: IconPackStyle = IconPackStyle.HYPEROS,
    val languageTag: String = ""
) {
    companion object {
        val defaultPriorities = listOf(
            "CALL",
            "NAVIGATION",
            "TIMER",
            "MEDIA",
            "DOWNLOAD",
            "CAST",
            "HOTSPOT",
            "SCREEN_RECORD",
            "TORCH",
            "PRIVACY"
        )
    }
}

object ConfigManager {
    val config = MutableStateFlow(IslandConfig())
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("island_config", Context.MODE_PRIVATE)
        val version = prefs?.getInt("config_version", 0) ?: 0
        val y = if (version < 2) {
            prefs?.edit()?.putInt("config_version", 2)?.putFloat("y_offset", 0f)?.commit()
            0f
        } else {
            prefs?.getFloat("y_offset", 0f) ?: 0f
        }
        val x = prefs?.getFloat("x_offset", 0f) ?: 0f
        val width = prefs?.getFloat("pill_width", 110f) ?: 110f
        val height = prefs?.getFloat("pill_height", 34f) ?: 34f
        val margin = prefs?.getFloat("notch_margin", 5f) ?: 5f
        val guide = prefs?.getBoolean("show_guide", false) ?: false
        val dampingRaw = prefs?.getFloat("spring_damping", 0.68f) ?: 0.68f
        val damping = if (dampingRaw == 0.55f || dampingRaw == 0.72f) 0.68f else dampingRaw
        val stiffnessRaw = prefs?.getFloat("spring_stiffness", 260f) ?: 260f
        val stiffness = if (stiffnessRaw == 380f || stiffnessRaw == 280f) 260f else stiffnessRaw
        val fireworks = prefs?.getFloat("fireworks_intensity", 1.0f) ?: 1.0f

        val savedPriosStr = prefs?.getString("activity_priorities", null)
        val priorities = if (!savedPriosStr.isNullOrBlank()) {
            savedPriosStr.split(",").filter { it.isNotBlank() }
        } else {
            IslandConfig.defaultPriorities
        }

        val iconStyleStr = prefs?.getString("icon_pack_style", IconPackStyle.HYPEROS.name) ?: IconPackStyle.HYPEROS.name
        val iconStyle = try {
            IconPackStyle.valueOf(iconStyleStr)
        } catch (_: Exception) {
            IconPackStyle.HYPEROS
        }

        val langTag = prefs?.getString("language_tag", "") ?: ""

        config.value = IslandConfig(x, y, width, height, margin, guide, damping, stiffness, fireworks, priorities, iconStyle, langTag)
    }

    fun update(newConfig: IslandConfig) {
        config.value = newConfig
        prefs?.edit()?.apply {
            putInt("config_version", 2)
            putFloat("x_offset", newConfig.xOffsetDp)
            putFloat("y_offset", newConfig.yOffsetDp)
            putFloat("pill_width", newConfig.pillWidthDp)
            putFloat("pill_height", newConfig.pillHeightDp)
            putFloat("notch_margin", newConfig.notchMarginDp)
            putBoolean("show_guide", newConfig.showGuide)
            putFloat("spring_damping", newConfig.springDamping)
            putFloat("spring_stiffness", newConfig.springStiffness)
            putFloat("fireworks_intensity", newConfig.fireworksIntensity)
            putString("activity_priorities", newConfig.activityPriorities.joinToString(","))
            putString("icon_pack_style", newConfig.iconPackStyle.name)
            putString("language_tag", newConfig.languageTag)
            apply()
        }
    }
}
