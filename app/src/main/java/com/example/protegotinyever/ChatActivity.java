package com.example.protegotinyever;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.protegotinyever.util.DataChannelHandler;

public class ChatActivity extends AppCompatActivity {
    private LinearLayout chatLayout;
    private EditText messageInput;
    private Button sendButton;
    private ScrollView scrollView;
    private DataChannelHandler dataChannelHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        chatLayout = findViewById(R.id.chatLayout);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        scrollView = findViewById(R.id.chatScrollView);

        dataChannelHandler = DataChannelHandler.getInstance();

        sendButton.setOnClickListener(view -> sendMessage());

        String peerUsername = getIntent().getStringExtra("peerUsername");
        if (peerUsername == null || peerUsername.isEmpty()) {
            peerUsername = "Peer"; // Default fallback
        }

        // ✅ Handle incoming messages
        dataChannelHandler.setOnMessageReceivedListener(this::addMessageToUI);
    }

    private void sendMessage() {
        String message = messageInput.getText().toString().trim();
        if (!message.isEmpty()) {
            dataChannelHandler.sendMessage(message);
            addMessageToUI("You: " + message);
            messageInput.setText("");
        }
    }

    // ✅ Ensure messages are displayed correctly
    private void addMessageToUI(String message) {
        runOnUiThread(() -> {
            TextView messageView = new TextView(this);
            messageView.setText(message);
            messageView.setTextSize(16);
            messageView.setPadding(10, 5, 10, 5);
            chatLayout.addView(messageView);

            // ✅ Scroll to bottom so new messages are always visible
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dataChannelHandler.close();
    }
}
