package com.example.protegotinyever.inte;

public interface SignalingCallback {
    void onSignalingReceived(String type, String data, String sender); // ✅ Add sender
}
