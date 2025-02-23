package com.example.protegotinyever.act;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
    private static final int FILE_PICKER_REQUEST_CODE = 1;

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

        findViewById(R.id.sendFileButton).setOnClickListener(v -> pickFile());
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
        messageAdapter = new MessageAdapter(messageList, currentUser, this, this::openFile);
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

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, FILE_PICKER_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            sendFile(uri);
        }
    }

    private void sendFile(Uri fileUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                throw new IOException("Unable to open input stream for URI: " + fileUri);
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            byte[] fileBytes = byteArrayOutputStream.toByteArray();

            String fileName = getFileNameFromUri(fileUri);
            String fileType = getContentResolver().getType(fileUri);
            if (fileType == null) fileType = "application/octet-stream";

            webRTCClient.sendEncryptedMessage(fileBytes, peerUsername, true, fileName, fileType);
            addMessageToUI(new MessageModel(currentUser, "Sent file: " + fileName, System.currentTimeMillis()));
            Toast.makeText(this, "File sent: " + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("ChatActivity", "Error sending file: " + e.getMessage());
            Toast.makeText(this, "Failed to send file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = "unknown_file";
        try {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("ChatActivity", "Error getting file name: " + e.getMessage());
        }
        return fileName;
    }

    private void openFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            Uri uri = Uri.fromFile(file);
            String mimeType = getContentResolver().getType(uri);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(intent);
            } catch (Exception e) {
                Log.e("ChatActivity", "Error opening file: " + e.getMessage());
                Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.e("ChatActivity", "File not found: " + filePath);
            Toast.makeText(this, "File not found: " + filePath, Toast.LENGTH_SHORT).show();
        }
    }
}