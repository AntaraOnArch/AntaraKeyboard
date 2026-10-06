package com.example.antarakeyboard.data

import com.example.antarakeyboard.R
import androidx.annotation.StringRes
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Storage for emoji picker preferences:
 * - Recent/frequently used emojis
 * - Order of action buttons (Close, Backspace, Space)
 * - Category tabs position (top/bottom/left/right)
 * - Action buttons side (left/right)
 */
object EmojiPickerStorage {

    const val PREFS_NAME = "emoji_picker_prefs"
    const val KEY_RECENT_EMOJIS = "recent_emojis"
    private const val KEY_BUTTON_ORDER = "button_order"
    private const val KEY_TABS_POSITION = "tabs_position"
    private const val KEY_BUTTONS_SIDE = "buttons_side"

    private const val MAX_RECENT_EMOJIS = 30

    enum class EmojiButtonAction {
        CLOSE,
        BACKSPACE,
        SPACE
    }

    enum class TabsPosition(@param:StringRes val labelRes: Int) {
        LEFT(R.string.emoji_pos_left),
        RIGHT(R.string.emoji_pos_right),
        TOP(R.string.emoji_pos_top),
        BOTTOM(R.string.emoji_pos_bottom)
    }

    enum class ButtonsSide(@param:StringRes val labelRes: Int) {
        LEFT(R.string.emoji_pos_left),
        RIGHT(R.string.emoji_pos_right)
    }

    private val gson = Gson()

    /* ───────── RECENT EMOJIS ───────── */

    fun getRecentEmojis(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RECENT_EMOJIS, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addRecentEmoji(context: Context, emoji: String) {
        val current = getRecentEmojis(context).toMutableList()

        // Remove if already exists (will be added to front)
        current.remove(emoji)

        // Add to front
        current.add(0, emoji)

        // Limit size
        val limited = current.take(MAX_RECENT_EMOJIS)

        // Save
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_RECENT_EMOJIS, gson.toJson(limited)).apply()
    }

    fun clearRecentEmojis(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_RECENT_EMOJIS).apply()
    }

    /* ───────── BUTTON ORDER ───────── */

    /**
     * Returns the order of action buttons.
     * Default order: [CLOSE, BACKSPACE, SPACE] (top to bottom)
     */
    fun getButtonOrder(context: Context): List<EmojiButtonAction> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_BUTTON_ORDER, null)

        if (json == null) {
            return defaultButtonOrder()
        }

        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val names: List<String> = gson.fromJson(json, type)
            names.mapNotNull { name ->
                try {
                    EmojiButtonAction.valueOf(name)
                } catch (e: Exception) {
                    null
                }
            }.takeIf { it.size == 3 } ?: defaultButtonOrder()
        } catch (e: Exception) {
            defaultButtonOrder()
        }
    }

    fun saveButtonOrder(context: Context, order: List<EmojiButtonAction>) {
        if (order.size != 3) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val names = order.map { it.name }
        prefs.edit().putString(KEY_BUTTON_ORDER, gson.toJson(names)).apply()
    }

    fun defaultButtonOrder(): List<EmojiButtonAction> {
        return listOf(
            EmojiButtonAction.CLOSE,
            EmojiButtonAction.BACKSPACE,
            EmojiButtonAction.SPACE
        )
    }

    /**
     * Get display label for button action
     */
    fun getButtonLabel(action: EmojiButtonAction): String {
        return when (action) {
            EmojiButtonAction.CLOSE -> "✕"
            EmojiButtonAction.BACKSPACE -> "⌫"
            EmojiButtonAction.SPACE -> "␣"
        }
    }

    /**
     * Get display name for button action (for settings UI)
     */
    @StringRes
    fun getButtonDisplayName(action: EmojiButtonAction): Int {
        return when (action) {
            EmojiButtonAction.CLOSE -> R.string.emoji_btn_close
            EmojiButtonAction.BACKSPACE -> R.string.emoji_btn_backspace
            EmojiButtonAction.SPACE -> R.string.emoji_btn_space
        }
    }

    /* ───────── TABS POSITION ───────── */

    fun getTabsPosition(context: Context): TabsPosition {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_TABS_POSITION, null) ?: return TabsPosition.LEFT

        return try {
            TabsPosition.valueOf(name)
        } catch (e: Exception) {
            TabsPosition.LEFT
        }
    }

    fun setTabsPosition(context: Context, position: TabsPosition) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TABS_POSITION, position.name).apply()
    }

    /* ───────── BUTTONS SIDE ───────── */

    fun getButtonsSide(context: Context): ButtonsSide {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_BUTTONS_SIDE, null) ?: return ButtonsSide.RIGHT

        return try {
            ButtonsSide.valueOf(name)
        } catch (e: Exception) {
            ButtonsSide.RIGHT
        }
    }

    fun setButtonsSide(context: Context, side: ButtonsSide) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BUTTONS_SIDE, side.name).apply()
    }
}
