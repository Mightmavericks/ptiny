package com.example.protegotinyever.act;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.activity.OnBackPressedCallback;

import com.example.protegotinyever.R;
import com.example.protegotinyever.adapt.MessageEntity;
import com.example.protegotinyever.tt.MessageAdapter;
import com.example.protegotinyever.mode.MessageModel;
import com.example.protegotinyever.util.DataChannelHandler;
import com.example.protegotinyever.webrtc.WebRTCClient;

import java.util.ArrayList;
import java.util.List;
import org.webrtc.DataChannel;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView chatRecyclerView;
    private EditText messageInput;
    private MessageAdapter messageAdapter;
    private List<MessageModel> messageList;
    private DataChannelHandler dataChannelHandler;
    private WebRTCClient webRTCClient;
    private String currentUser;
    private String peerUsername;
    private TextView connectionStatus;
    private Button sendButton;
    private int rea = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        chatRecyclerView = findViewById(R.id.crv);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        connectionStatus = findViewById(R.id.connectionStatus);

        currentUser = "You";
        peerUsername = getIntent().getStringExtra("peerUsername");
        if (peerUsername == null || peerUsername.isEmpty()) {
            peerUsername = "Peer";
        }
        Log.d("ChatActivity", "Opened chat with peer: " + peerUsername);

        webRTCClient = WebRTCClient.getInstance(this, null);
        dataChannelHandler = DataChannelHandler.getInstance(getApplicationContext());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (dataChannelHandler != null) {
                    dataChannelHandler.setOnMessageReceivedListener(null);
                    dataChannelHandler.setStateChangeListener(null);
                }
                finish();
            }
        });

        setupToolbar();
        setupDataChannel();
        setupRecyclerView();

        sendButton.setOnClickListener(view -> sendMessage());
        loadMessageHistory();
    }

    private void setupToolbar() {
        TextView peerUsernameView = findViewById(R.id.peerUsername);
        peerUsernameView.setText(peerUsername.toUpperCase());
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }

    private void setupDataChannel() {
        dataChannelHandler.setCurrentPeer(peerUsername);
        dataChannelHandler.setOnMessageReceivedListener(message -> {
            Log.d("ChatActivity", "Received message: " + message);
            addMessageToUI(new MessageModel(peerUsername, message, System.currentTimeMillis()));
        });
        dataChannelHandler.setStateChangeListener(state -> {
            Log.d("ChatActivity", "DataChannel state: " + state);
            runOnUiThread(() -> updateConnectionStatus(state));
        });

        DataChannel channel = dataChannelHandler.getDataChannel(peerUsername);
        if (channel != null) {
            Log.d("ChatActivity", "Initial DataChannel state: " + channel.state());
            updateConnectionStatus(channel.state());
        } else {
            Log.w("ChatActivity", "No DataChannel for " + peerUsername);
        }

        webRTCClient.setWebRTCListener(new WebRTCClient.WebRTCListener() {
            @Override
            public void onConnected() {
                Log.d("ChatActivity", "WebRTC connected");
                runOnUiThread(() -> updateConnectionStatus(DataChannel.State.OPEN));
            }

            @Override
            public void onConnectionFailed() {
                Log.d("ChatActivity", "WebRTC connection failed");
                runOnUiThread(() -> updateConnectionStatus(DataChannel.State.CLOSED));
            }

            @Override
            public void onMessageReceived(String message, String peerUsername) {
                Log.d("ChatActivity", "WebRTC message received from " + peerUsername + ": " + message + " (ignored for UI)");
            }
        });
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
        boolean enableSend = true;

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
            Log.d("ChatActivity", "Sending message to " + peerUsername + ": " + messageText);
            webRTCClient.sendEncryptedMessage(messageText, peerUsername);
            messageInput.setText("");
            addMessageToUI(new MessageModel(currentUser, messageText, System.currentTimeMillis()));

            DataChannel channel = dataChannelHandler.getDataChannel(peerUsername);
            if (channel == null || channel.state() != DataChannel.State.OPEN) {
                showOfflineMessageIndicator();
            }
        } else {
            Log.d("ChatActivity", "Empty message not sent");
        }
    }

    private void showOfflineMessageIndicator() {
        Toast.makeText(this, "Message will be delivered when peer comes online", Toast.LENGTH_SHORT).show();
    }

    private void loadMessageHistory() {
        new Thread(() -> {
            List<MessageEntity> history = dataChannelHandler.getMessageHistory(peerUsername);
            Log.d("ChatActivity", "Loading message history for " + peerUsername + ", size: " + history.size());
            runOnUiThread(() -> {
                messageList.clear();
                for (MessageEntity msg : history) {
                    messageList.add(new MessageModel(msg.getSender(), msg.getMessage(), msg.getTimestamp()));
                }
                messageAdapter.notifyDataSetChanged();
                chatRecyclerView.scrollToPosition(messageList.size() - 1);
            });
        }).start();
    }

    public void addMessageToUI(MessageModel message) {
        Log.d("ChatActivity", "Adding message to UI: " + message.getText() + " from " + message.getSender());
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
        if (dataChannelHandler != null) {
            dataChannelHandler.setOnMessageReceivedListener(null);
            dataChannelHandler.setStateChangeListener(null);
        }
    }

    public String getPeerUsername() {
        return peerUsername;
    }
}