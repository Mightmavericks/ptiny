package com.example.protegotinyever;

import android.app.Application;
import com.example.protegotinyever.util.ThemeManager;

public class ProtegoTinyEverApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize theme
        ThemeManager.getInstance(this).initializeTheme();
    }
} 