package com.example.protegotinyever;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.protegotinyever.tt.MessageAdapter;
import com.example.protegotinyever.tt.MessageModel;
import com.example.protegotinyever.util.DataChannelHandler;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView chatRecyclerView;
    private EditText messageInput;
    private MessageAdapter messageAdapter;
    private List<MessageModel> messageList;
    private DataChannelHandler dataChannelHandler;
    private String currentUser;
    private String peerUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        chatRecyclerView = findViewById(R.id.crv);
        messageInput = findViewById(R.id.messageInput);
        Button sendButton = findViewById(R.id.sendButton);

        dataChannelHandler = DataChannelHandler.getInstance();
        currentUser = "You"; // This will be changed later to the actual username.
        peerUsername = getIntent().getStringExtra("peerUsername");
        if (peerUsername == null || peerUsername.isEmpty()) {
            peerUsername = "Peer"; // Default fallback
        }

        // Setup RecyclerView
        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(messageList, currentUser);

        chatRecyclerView.setAdapter(messageAdapter);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        sendButton.setOnClickListener(view -> sendMessage());

        // ✅ Handle incoming messages
        dataChannelHandler.setOnMessageReceivedListener(message -> {
            addMessageToUI(new MessageModel(peerUsername, message, System.currentTimeMillis()));
        });

    }

    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();
        if (!messageText.isEmpty()) {
            Log.d("WebRTC", "📤 Sending message: " + messageText);
            dataChannelHandler.sendMessage(messageText);

            // Add sent message to UI
            addMessageToUI(new MessageModel(currentUser, messageText, System.currentTimeMillis()));
            messageInput.setText("");
        }
    }

    public void addMessageToUI(MessageModel message) {
        runOnUiThread(() -> {
            if (messageList == null || messageAdapter == null || chatRecyclerView == null) {
                Log.e("WebRTC", "❌ ERROR: messageList, messageAdapter, or chatRecyclerView is NULL!");
                return;
            }

            messageList.add(message);
            messageAdapter.notifyItemInserted(messageList.size() - 1);
            chatRecyclerView.scrollToPosition(messageList.size() - 1);
        });

        Log.d("WebRTC", "📩 UI Updated with message: " + message.getText());
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        dataChannelHandler.close();
    }
}
