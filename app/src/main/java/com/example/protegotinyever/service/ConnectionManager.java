package com.example.protegotinyever.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.util.HashSet;
import java.util.Set;

public class ConnectionManager {
    private static ConnectionManager instance;
    private final SharedPreferences prefs;
    private final Set<String> connectedUsers;
    private static final String PREFS_NAME = "ConnectionPrefs";
    private static final String KEY_CONNECTED_USERS = "connected_users";

    private ConnectionManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        connectedUsers = new HashSet<>(prefs.getStringSet(KEY_CONNECTED_USERS, new HashSet<>()));
        Log.d("ConnectionManager", "Loaded connected users: " + connectedUsers);
    }

    public static ConnectionManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConnectionManager(context.getApplicationContext());
        }
        return instance;
    }

    public void addConnectedUser(String username) {
        connectedUsers.add(username);
        saveConnectedUsers();
        Log.d("ConnectionManager", "Added user: " + username + ", Now: " + connectedUsers);
    }

    public void removeConnectedUser(String username) {
        connectedUsers.remove(username);
        saveConnectedUsers();
        Log.d("ConnectionManager", "Removed user: " + username + ", Now: " + connectedUsers);
    }

    public Set<String> getConnectedUsers() {
        return new HashSet<>(connectedUsers);
    }

    public boolean isUserConnected(String username) {
        return connectedUsers.contains(username);
    }

    private void saveConnectedUsers() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(KEY_CONNECTED_USERS, connectedUsers);
        editor.apply();
        Log.d("ConnectionManager", "Saved connected users: " + connectedUsers);
    }

    public void clearConnectedUsers() {
        connectedUsers.clear();
        saveConnectedUsers();
        Log.d("ConnectionManager", "Cleared connected users");
    }
}