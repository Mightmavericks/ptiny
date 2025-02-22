package com.example.protegotinyever.inte;

public interface SignalingCallback {
    int rea = 1;
    void onSignalingReceived(String type, String data, String sender); // ✅ Add sender
}
