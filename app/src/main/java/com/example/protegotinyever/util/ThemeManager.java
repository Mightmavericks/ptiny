package com.example.protegotinyever.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {
    private static final String PREFS_NAME = "ThemePrefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static ThemeManager instance;
    private final SharedPreferences prefs;

    private ThemeManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context);
        }
        return instance;
    }

    public boolean isDarkMode() {
        return prefs.getBoolean(KEY_DARK_MODE, true);
    }

    public void setDarkMode(boolean isDark) {
        prefs.edit().putBoolean(KEY_DARK_MODE, isDark).apply();
        applyTheme(isDark);
    }

    public void applyTheme(boolean isDark) {
        AppCompatDelegate.setDefaultNightMode(
            isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }

    public void initializeTheme() {
        applyTheme(isDarkMode());
    }
} 