package com.example.protegotinyever.act;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.protegotinyever.R;
import com.example.protegotinyever.adapt.MessageEntity;
import com.example.protegotinyever.tt.MessageAdapter;
import com.example.protegotinyever.mode.MessageModel;
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

        // ✅ Setup Toolbar with Back Button
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("Chat with " + getIntent().getStringExtra("peerUsername"));
        }

        chatRecyclerView = findViewById(R.id.crv);
        messageInput = findViewById(R.id.messageInput);
        Button sendButton = findViewById(R.id.sendButton);

        dataChannelHandler = DataChannelHandler.getInstance(getApplicationContext());
        currentUser = "You";
        peerUsername = getIntent().getStringExtra("peerUsername");
        dataChannelHandler.setCurrentPeer(peerUsername);
        if (peerUsername == null || peerUsername.isEmpty()) {
            peerUsername = "Peer";
        }

        // ✅ Setup RecyclerView
        messageList = new ArrayList<>();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);
        messageAdapter = new MessageAdapter(messageList, currentUser);
        chatRecyclerView.setAdapter(messageAdapter);
        sendButton.setOnClickListener(view -> sendMessage());

        Log.d("RecyclerView", "Message List Size: " + messageList.size());

        // ✅ Ensure DataChannel observer is always registered
        rebindDataChannelObserver();
        loadMessageHistory();
    }

    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();
        if (!messageText.isEmpty()) {
            Log.d("WebRTC", "📤 Sending message: " + messageText);
            dataChannelHandler.sendMessage(messageText, peerUsername);

            // ✅ Add sent message to UI
            addMessageToUI(new MessageModel(currentUser, messageText, System.currentTimeMillis()));
            messageInput.setText("");
        }
    }

    private void loadMessageHistory() {
        new Thread(() -> {
            List<MessageEntity> history = dataChannelHandler.getMessageHistory(peerUsername);
            runOnUiThread(() -> {
                for (MessageEntity msg : history) {
                    messageList.add(new MessageModel(msg.getSender(), msg.getMessage(), msg.getTimestamp()));
                }
                messageAdapter.notifyDataSetChanged();
                chatRecyclerView.scrollToPosition(messageList.size() - 1); // Scroll to the latest message
            });
        }).start();
    }



    public void addMessageToUI(MessageModel message) {
        runOnUiThread(() -> {
            messageList.add(message);
            messageAdapter.notifyItemInserted(messageList.size() - 1);
            chatRecyclerView.scrollToPosition(messageList.size() - 1);
            Log.d("RecyclerView", "Message List Size: " + messageList.size());
        });

        Log.d("WebRTC", "📩 UI Updated with message: " + message.getText());
    }

    private void rebindDataChannelObserver() {
        if (dataChannelHandler.getDataChannel() != null) {
            Log.d("ChatActivity", "✅ Rebinding DataChannel observer");
            dataChannelHandler.setOnMessageReceivedListener(message -> {
                addMessageToUI(new MessageModel(peerUsername, message, System.currentTimeMillis()));
            });
        } else {
            Log.e("ChatActivity", "❌ DataChannel is NULL on re-entry!");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // ✅ Do NOT close DataChannel here to keep connection alive
    }

    // ✅ Handle Back Button
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
