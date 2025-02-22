package com.example.protegotinyever.inte;

import com.example.protegotinyever.tt.ChatMessage;

public interface NewMessageCallback {
    int rea = 1;
    void onNewMessage(String message, String fromPeer);
}
