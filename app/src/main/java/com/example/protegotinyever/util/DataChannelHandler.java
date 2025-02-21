package com.example.protegotinyever.util;

import android.content.Context;
import android.util.Log;
import androidx.room.Room;
import com.example.protegotinyever.db.ChatDatabase;
import com.example.protegotinyever.db.MessageDao;
import com.example.protegotinyever.adapt.MessageEntity;
import com.example.protegotinyever.webrtc.WebRTCClient;

import org.webrtc.DataChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;

public class DataChannelHandler {
    private static DataChannelHandler instance;
    private DataChannel dataChannel;
    private OnMessageReceivedListener messageReceivedListener;
    private OnStateChangeListener stateChangeListener;
    private ChatDatabase chatDatabase;
    private MessageDao messageDao;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private String currentPeerUsername;
    private WebRTCClient webRTCClient;

    public static synchronized DataChannelHandler getInstance(Context context) {
        if (instance == null) {
            instance = new DataChannelHandler();
            instance.setContext(context);
        }
        return instance;
    }

    public void setWebRTCClient(WebRTCClient client) {
        this.webRTCClient = client;
    }

    public void setCurrentPeer(String peerUsername) {
        Log.d("WebRTC", "Setting current peer to: " + peerUsername);
        this.currentPeerUsername = peerUsername;
    }

    private void setContext(Context context) {
        chatDatabase = ChatDatabase.getInstance(context);
        messageDao = chatDatabase.messageDao();
    }

    public void setDataChannel(DataChannel dataChannel) {
        this.dataChannel = dataChannel;
        registerDataChannelObserver();
        if (dataChannel != null && stateChangeListener != null) {
            stateChangeListener.onStateChange(dataChannel.state());
        }
    }

    public DataChannel getDataChannel() {
        return dataChannel;
    }

    private void registerDataChannelObserver() {
        if (dataChannel == null) {
            Log.e("WebRTC", "❌ DataChannel is NULL, cannot register observer!");
            return;
        }

        dataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                byte[] data = new byte[buffer.data.remaining()];
                buffer.data.get(data);
                String message = new String(data, StandardCharsets.UTF_8);
                
                // Get peer username from WebRTCClient if not set
                if (currentPeerUsername == null && webRTCClient != null) {
                    currentPeerUsername = webRTCClient.getPeerUsername();
                    Log.d("WebRTC", "📱 Got peer username from WebRTCClient: " + currentPeerUsername);
                }
                
                Log.d("WebRTC", "📥 Message received: " + message + " from peer: " + currentPeerUsername);
                
                // Save message synchronously and then notify
                MessageEntity messageEntity = new MessageEntity(
                    currentPeerUsername != null ? currentPeerUsername : "Unknown",
                    message,
                    System.currentTimeMillis(),
                    currentPeerUsername != null ? currentPeerUsername : "Unknown"
                );
                
                try {
                    // Execute database operation synchronously
                    databaseExecutor.submit(() -> {
                        messageDao.insertMessage(messageEntity);
                        return null;
                    }).get(); // Wait for completion
                    
                    Log.d("WebRTC", "💾 Message saved to database - From: " + currentPeerUsername);
                    
                    // After successful save, notify listeners on main thread
                    final String finalPeer = currentPeerUsername != null ? currentPeerUsername : "Unknown";
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        // First notify message listener for UI updates
                        if (messageReceivedListener != null) {
                            messageReceivedListener.onMessageReceived(message);
                        }
                        
                        // Then notify WebRTCClient for notification handling
                        if (webRTCClient != null) {
                            webRTCClient.onMessageReceived(message, finalPeer);
                            Log.d("WebRTC", "🔔 Forwarded message to WebRTCClient for notification");
                        } else {
                            Log.e("WebRTC", "❌ WebRTCClient reference is null, cannot forward for notification");
                        }
                    });
                } catch (Exception e) {
                    Log.e("WebRTC", "Error saving message: " + e.getMessage());
                }
            }

            @Override
            public void onStateChange() {
                Log.d("WebRTC", "⚡ DataChannel state changed: " + dataChannel.state());
                if (stateChangeListener != null) {
                    stateChangeListener.onStateChange(dataChannel.state());
                }
            }

            @Override
            public void onBufferedAmountChange(long previousAmount) {}
        });
    }

    public void setOnMessageReceivedListener(OnMessageReceivedListener listener) {
        this.messageReceivedListener = listener;
    }

    public void setStateChangeListener(OnStateChangeListener listener) {
        this.stateChangeListener = listener;
        if (dataChannel != null && listener != null) {
            listener.onStateChange(dataChannel.state());
        }
    }

    private void notifyMessageReceived(String message) {
        if (messageReceivedListener != null) {
            messageReceivedListener.onMessageReceived(message);
        }
    }

    public void saveMessageToDatabase(String sender, String message, String peerUsername) {
        if (message == null || sender == null || peerUsername == null) {
            Log.e("WebRTC", "❌ Cannot save message - null parameters");
            return;
        }

        databaseExecutor.execute(() -> {
            try {
                MessageEntity messageEntity = new MessageEntity(
                    sender,
                    message,
                    System.currentTimeMillis(),
                    peerUsername
                );
                messageDao.insertMessage(messageEntity);
                Log.d("WebRTC", "💾 Message saved to database - From: " + sender + ", To: " + peerUsername);
            } catch (Exception e) {
                Log.e("WebRTC", "Error saving message: " + e.getMessage());
            }
        });
    }

    public void sendMessage(String message, String peerUsername) {
        if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
            Log.d("WebRTC", "📤 Sending message to: " + peerUsername);
            
            // Save locally first and wait for completion
            MessageEntity messageEntity = new MessageEntity(
                "You",
                message,
                System.currentTimeMillis(),
                peerUsername
            );
            
            try {
                // Execute database operation synchronously
                databaseExecutor.submit(() -> {
                    messageDao.insertMessage(messageEntity);
                    return null;
                }).get(); // Wait for completion
                
                Log.d("WebRTC", "💾 Message saved to database - From: You, To: " + peerUsername);
                
                // After successful save, send through DataChannel
                byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
                DataChannel.Buffer buffer = new DataChannel.Buffer(ByteBuffer.wrap(messageBytes), false);
                dataChannel.send(buffer);
                Log.d("WebRTC", "✅ Message sent successfully");
            } catch (Exception e) {
                Log.e("WebRTC", "Error in message handling: " + e.getMessage());
            }
        } else {
            Log.e("WebRTC", "❌ DataChannel is NULL or NOT OPEN!");
        }
    }

    public List<MessageEntity> getMessageHistory(String peerUsername) {
        try {
            Log.d("WebRTC", "📚 Getting message history for chat with: " + peerUsername);
            List<MessageEntity> messages = messageDao.getMessagesForPeer(peerUsername);
            Log.d("WebRTC", "📚 Found " + messages.size() + " messages in chat history");
            return messages;
        } catch (Exception e) {
            Log.e("WebRTC", "Error getting message history: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public interface OnMessageReceivedListener {
        void onMessageReceived(String message);
    }

    public interface OnStateChangeListener {
        void onStateChange(DataChannel.State state);
    }

    public void close() {
        if (dataChannel != null) {
            dataChannel.close();
            dataChannel = null;
        }
    }
}
