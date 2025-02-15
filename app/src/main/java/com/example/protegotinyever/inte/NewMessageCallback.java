package com.example.protegotinyever.inte;

import com.example.protegotinyever.tt.ChatMessage;

public interface NewMessageCallback {
    void onNewMessageReceived(ChatMessage message);
}
