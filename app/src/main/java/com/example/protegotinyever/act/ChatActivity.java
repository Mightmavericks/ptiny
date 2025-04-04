package com.example.protegotinyever.act;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.protegotinyever.R;
import com.example.protegotinyever.adapt.MessageEntity;
import com.example.protegotinyever.mode.MessageModel;
import com.example.protegotinyever.tt.MessageAdapter;
import com.example.protegotinyever.util.DataChannelHandler;
import com.example.protegotinyever.webrtc.WebRTCClient;

import org.webrtc.DataChannel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity implements WebRTCClient.ProgressListener {
    private static final int YOUR_PERMISSION_REQUEST_CODE = 122;
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
    private static final int FILE_PICKER_REQUEST_CODE = 1;
    private Dialog progressDialog;
    private ProgressBar progressBar;
    private TextView progressText;
    private List<String> messageQueue = new ArrayList<>();
    private boolean isFileTransferInProgress = false;
    private ExecutorService messageQueueExecutor = Executors.newSingleThreadExecutor();

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
        webRTCClient.setProgressListener(this);
        dataChannelHandler = DataChannelHandler.getInstance(getApplicationContext());

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (dataChannelHandler != null) {
                    dataChannelHandler.setOnMessageReceivedListener(null);
                    dataChannelHandler.setStateChangeListener(null);
                }
                finish();
            }
        });

        requestPermissions();
        setupToolbar();
        setupDataChannel();
        setupRecyclerView();
        setupProgressDialog();

        sendButton.setOnClickListener(view -> sendMessage());
        loadMessageHistory();

        findViewById(R.id.sendFileButton).setOnClickListener(v -> pickFile());
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions = new String[]{
                        android.Manifest.permission.READ_MEDIA_IMAGES,
                        android.Manifest.permission.READ_MEDIA_VIDEO,
                        android.Manifest.permission.READ_MEDIA_AUDIO
                };
            } else {
                permissions = new String[]{
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                };
            }
            requestPermissions(permissions, YOUR_PERMISSION_REQUEST_CODE);
        }
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

    private void setupProgressDialog() {
        progressDialog = new Dialog(this, R.style.TransparentDialog);
        progressDialog.setContentView(R.layout.dialog_progress);
        progressDialog.setCancelable(false);
        progressBar = progressDialog.findViewById(R.id.progressBar);
        progressText = progressDialog.findViewById(R.id.progressText);
        progressBar.setMax(100);
    }

    private void setupDataChannel() {
        dataChannelHandler.setCurrentPeer(peerUsername);
        dataChannelHandler.setOnMessageReceivedListener(message -> {
            Log.d("ChatActivity", "Received message: " + message);
            addMessageToUI(new MessageModel(peerUsername.equals(currentUser) ? currentUser : peerUsername, message, System.currentTimeMillis()));
            if (progressDialog.isShowing() && message.contains("Received file:")) {
                progressDialog.dismiss();
            }
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
                runOnUiThread(() -> {
                    updateConnectionStatus(DataChannel.State.CLOSED);
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                        Toast.makeText(ChatActivity.this, "Connection lost during file transfer", Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onMessageReceived(String message, String peerUsername) {
                Log.d("ChatActivity", "WebRTC message received from " + peerUsername + ": " + message + " (ignored for UI)");
            }

            @Override
            public void onFileSent(String filePath, String fileName) {
                Log.d("ChatActivity", "File sent successfully: " + filePath);
                runOnUiThread(() -> {
                    addMessageToUI(new MessageModel(currentUser, "Sent file: " + fileName + " at " + filePath, System.currentTimeMillis()));
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(ChatActivity.this, "File sent: " + fileName, Toast.LENGTH_SHORT).show();
                });
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
            if (isFileTransferInProgress) {
                // Queue the message if file transfer is in progress
                messageQueue.add(messageText);
                messageInput.setText("");
                Toast.makeText(this, "Message will be sent after file transfer completes", Toast.LENGTH_SHORT).show();
            } else {
                // Send message immediately if no file transfer is in progress
                Log.d("ChatActivity", "Sending message to " + peerUsername + ": " + messageText);
                webRTCClient.sendEncryptedMessage(messageText, peerUsername);
                messageInput.setText("");
                addMessageToUI(new MessageModel(currentUser, messageText, System.currentTimeMillis()));

                DataChannel channel = dataChannelHandler.getDataChannel(peerUsername);
                if (channel == null || channel.state() != DataChannel.State.OPEN) {
                    showOfflineMessageIndicator();
                }
            }
        }
    }

    private void processMessageQueue() {
        if (!messageQueue.isEmpty() && !isFileTransferInProgress) {
            messageQueueExecutor.execute(() -> {
                while (!messageQueue.isEmpty() && !isFileTransferInProgress) {
                    String message = messageQueue.remove(0);
                    webRTCClient.sendEncryptedMessage(message, peerUsername);
                    runOnUiThread(() -> addMessageToUI(new MessageModel(currentUser, message, System.currentTimeMillis())));
                    try {
                        Thread.sleep(100); // Small delay between messages
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
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
        messageQueueExecutor.shutdown();
        if (dataChannelHandler != null) {
            dataChannelHandler.setOnMessageReceivedListener(null);
            dataChannelHandler.setStateChangeListener(null);
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
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
            String fileName = getFileNameFromUri(fileUri);
            String fileType = getContentResolver().getType(fileUri);
            if (fileType == null) fileType = "application/octet-stream";

            long fileSize = getContentResolver().openFileDescriptor(fileUri, "r").getStatSize();
            if (fileSize > 1024 * 1024 * 1024) { // 1 GB limit
                throw new IOException("File size exceeds 1 GB limit: " + (fileSize / (1024 * 1024)) + " MB");
            }

            // Show progress dialog immediately
            progressDialog.show();
            progressBar.setProgress(0);
            progressText.setText(String.format("Preparing to send '%s': 0%%", fileName, 0));

            // Offload file sending to a background thread
            ExecutorService executor = Executors.newSingleThreadExecutor();
            String finalFileType = fileType;
            executor.execute(() -> {
                try {
                    webRTCClient.sendFile(fileUri, peerUsername, fileName, finalFileType);
                } catch (Exception e) {
                    Log.e("ChatActivity", "Error sending file: " + e.getMessage());
                    runOnUiThread(() -> {
                        Toast.makeText(ChatActivity.this, "Failed to send file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e("ChatActivity", "Error initiating file send: " + e.getMessage());
            Toast.makeText(this, "Failed to send file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
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
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                Toast.makeText(this, "File not found: " + filePath, Toast.LENGTH_LONG).show();
                return;
            }

            Uri fileUri;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".provider",
                    file
                );
            } else {
                fileUri = Uri.fromFile(file);
            }

            String mimeType = getContentResolver().getType(fileUri);
            if (mimeType == null) {
                mimeType = getMimeTypeFromExtension(filePath);
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Create chooser to handle the file
            Intent chooser = Intent.createChooser(intent, "Open file with");
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(chooser);
            } else {
                Toast.makeText(this, "No app found to open this file type", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("ChatActivity", "Error opening file: " + e.getMessage());
            Toast.makeText(this, "Error opening file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getMimeTypeFromExtension(String filePath) {
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(filePath);
        if (extension != null) {
            return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        }
        return "application/octet-stream";
    }

    @Override
    public void onProgress(String operation, int progress, String fileName) {
        runOnUiThread(() -> {
            isFileTransferInProgress = progress < 100;
            if (progress == 0) {
                progressDialog.show();
            }
            progressBar.setProgress(progress);
            progressText.setText(operation + ": " + progress + "% - " + fileName);
            
            if (progress >= 100) {
                progressDialog.dismiss();
                // Process queued messages after file transfer completes
                processMessageQueue();
            }
        });
    }
}