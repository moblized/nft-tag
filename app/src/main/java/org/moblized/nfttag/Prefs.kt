package org.moblized.nfttag

import android.content.Context

/** Thin SharedPreferences wrapper for the handful of settings the app keeps. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("nfttag", Context.MODE_PRIVATE)

    /** Hex UID of the tag paired as the bedside trigger, or null if none. */
    var pairedUid: String?
        get() = sp.getString(KEY_UID, null)
        set(value) = sp.edit().putString(KEY_UID, value).apply()

    /** When true, only [pairedUid] starts playback; other tags are ignored. */
    var lockToTag: Boolean
        get() = sp.getBoolean(KEY_LOCK, false)
        set(value) = sp.edit().putBoolean(KEY_LOCK, value).apply()

    /**
     * ChromaNoise preset to request on a tap. Empty string means "whatever ChromaNoise
     * played last" — nft-tag doesn't store the sound itself, ChromaNoise does.
     */
    var presetChoice: String
        get() = sp.getString(KEY_PRESET, "") ?: ""
        set(value) = sp.edit().putString(KEY_PRESET, value).apply()

    /** Sleep-timer length in minutes passed to ChromaNoise; 0 means no timer. */
    var sleepMinutes: Int
        get() = sp.getInt(KEY_SLEEP, 0)
        set(value) = sp.edit().putInt(KEY_SLEEP, value).apply()

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
        set(value) = sp.edit().putString(KEY_THEME, value.name).apply()

    companion object {
        private const val KEY_UID = "paired_uid"
        private const val KEY_LOCK = "lock_to_tag"
        private const val KEY_PRESET = "preset_choice"
        private const val KEY_SLEEP = "sleep_minutes"
        private const val KEY_THEME = "theme_mode"
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK }
