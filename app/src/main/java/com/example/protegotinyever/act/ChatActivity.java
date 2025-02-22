package com.example.protegotinyever.act;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.activity.OnBackPressedCallback;

import com.example.protegotinyever.R;
import com.example.protegotinyever.adapt.MessageEntity;
import com.example.protegotinyever.tt.MessageAdapter;
import com.example.protegotinyever.mode.MessageModel;
import com.example.protegotinyever.util.DataChannelHandler;
import java.util.ArrayList;
import java.util.List;
import org.webrtc.DataChannel;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView chatRecyclerView;
    private EditText messageInput;
    private MessageAdapter messageAdapter;
    private List<MessageModel> messageList;
    private DataChannelHandler dataChannelHandler;
    private String currentUser;
    private String peerUsername;
    private TextView connectionStatus;
    private Button sendButton;
    private int rea = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Initialize views
        chatRecyclerView = findViewById(R.id.crv);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        connectionStatus = findViewById(R.id.connectionStatus);

        // Get data from intent
        currentUser = "You";
        peerUsername = getIntent().getStringExtra("peerUsername");
        if (peerUsername == null || peerUsername.isEmpty()) {
            peerUsername = "Peer";
        }

        // Setup back press handling
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Remove listeners before finishing to prevent memory leaks and crashes
                if (dataChannelHandler != null) {
                    dataChannelHandler.setOnMessageReceivedListener(null);
                    dataChannelHandler.setStateChangeListener(null);
                }
                // Just finish the activity, the connection will be preserved
                finish();
            }
        });

        // Setup toolbar
        setupToolbar();

        // Setup DataChannel
        setupDataChannel();

        // Setup RecyclerView
        setupRecyclerView();

        // Setup click listeners
        sendButton.setOnClickListener(view -> sendMessage());

        // Load message history
        loadMessageHistory();
    }

    private void setupToolbar() {
        TextView peerUsernameView = findViewById(R.id.peerUsername);
        peerUsernameView.setText(peerUsername.toUpperCase());
        
        // Enable back button in toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }

    private void setupDataChannel() {
        dataChannelHandler = DataChannelHandler.getInstance(getApplicationContext());
        dataChannelHandler.setCurrentPeer(peerUsername);
        
        dataChannelHandler.setOnMessageReceivedListener(message -> {
            addMessageToUI(new MessageModel(peerUsername, message, System.currentTimeMillis()));
        });
        
        dataChannelHandler.setStateChangeListener(state -> {
            runOnUiThread(() -> updateConnectionStatus(state));
        });

        // Initial state check
        DataChannel channel = dataChannelHandler.getDataChannel(peerUsername);
        if (channel != null) {
            updateConnectionStatus(channel.state());
        }
    }

    private void setupRecyclerView() {
        messageList = new ArrayList<>();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);
        messageAdapter = new MessageAdapter(messageList, currentUser);
        chatRecyclerView.setAdapter(messageAdapter);
    }

    private void updateConnectionStatus(DataChannel.State state) {
        String statusText;
        int statusColor;
        boolean enableSend = true; // Always allow sending messages

        switch (state) {
            case OPEN:
                statusText = "SECURE CONNECTION ACTIVE";
                statusColor = getColor(R.color.success_green);
                break;
            case CONNECTING:
                statusText = "ESTABLISHING CONNECTION - MESSAGES WILL BE DELIVERED WHEN PEER IS ONLINE";
                statusColor = getColor(R.color.warning_yellow);
                break;
            case CLOSING:
            case CLOSED:
                statusText = "OFFLINE - MESSAGES WILL BE DELIVERED WHEN PEER IS ONLINE";
                statusColor = getColor(R.color.warning_yellow);
                break;
            default:
                statusText = "CONNECTION ERROR - MESSAGES WILL BE SAVED";
                statusColor = getColor(R.color.error_red);
                break;
        }

        connectionStatus.setText(statusText);
        connectionStatus.setTextColor(statusColor);
        sendButton.setEnabled(enableSend);
        messageInput.setEnabled(enableSend);
    }

    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();
        if (!messageText.isEmpty()) {
            dataChannelHandler.sendMessage(messageText, peerUsername);
            messageInput.setText("");
            
            // Show offline message indicator if needed
            DataChannel channel = dataChannelHandler.getDataChannel(peerUsername);
            if (channel == null || channel.state() != DataChannel.State.OPEN) {
                showOfflineMessageIndicator();
            }
            
            // Refresh message history to show the new message
            loadMessageHistory();
        }
    }

    private void showOfflineMessageIndicator() {
        // Show a brief message indicating the message will be delivered later
        android.widget.Toast.makeText(
            this,
            "Message will be delivered when peer comes online",
            android.widget.Toast.LENGTH_SHORT
        ).show();
    }

    private void loadMessageHistory() {
        new Thread(() -> {
            List<MessageEntity> history = dataChannelHandler.getMessageHistory(peerUsername);
            runOnUiThread(() -> {
                for (MessageEntity msg : history) {
                    messageList.add(new MessageModel(msg.getSender(), msg.getMessage(), msg.getTimestamp()));
                }
                messageAdapter.notifyDataSetChanged();
                chatRecyclerView.scrollToPosition(messageList.size() - 1);
            });
        }).start();
    }

    public void addMessageToUI(MessageModel message) {
        runOnUiThread(() -> {
            messageList.add(message);
            messageAdapter.notifyItemInserted(messageList.size() - 1);
            chatRecyclerView.scrollToPosition(messageList.size() - 1);
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't close the DataChannel when going back to ConnectActivity
        // Just remove the message listener to prevent memory leaks
        if (dataChannelHandler != null) {
            dataChannelHandler.setOnMessageReceivedListener(null);
            dataChannelHandler.setStateChangeListener(null);
        }
    }

    public String getPeerUsername() {
        return peerUsername;
    }
}
