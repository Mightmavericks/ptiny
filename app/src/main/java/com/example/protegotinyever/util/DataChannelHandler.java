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
import java.util.Map;
import java.util.HashMap;

public class DataChannelHandler {
    private static DataChannelHandler instance;
    private Map<String, DataChannel> dataChannels;
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

    private DataChannelHandler() {
        this.dataChannels = new HashMap<>();
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
        if (currentPeerUsername == null) {
            Log.e("WebRTC", "Cannot set DataChannel: currentPeerUsername is null");
            return;
        }

        if (dataChannel != null) {
            Log.d("WebRTC", "Setting DataChannel for peer: " + currentPeerUsername);
            dataChannels.put(currentPeerUsername, dataChannel);
            registerDataChannelObserver(currentPeerUsername, dataChannel);
            
            // Notify about initial state
            if (stateChangeListener != null) {
                stateChangeListener.onStateChange(dataChannel.state());
            }
        } else {
            Log.d("WebRTC", "Removing DataChannel for peer: " + currentPeerUsername);
            dataChannels.remove(currentPeerUsername);
        }
    }

    private void registerDataChannelObserver(String peerUsername, DataChannel dataChannel) {
        dataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long l) {}

            @Override
            public void onStateChange() {
                Log.d("WebRTC", "DataChannel state changed for " + peerUsername + ": " + dataChannel.state());
                if (stateChangeListener != null) {
                    stateChangeListener.onStateChange(dataChannel.state());
                }
                // Notify WebRTCClient about state change
                if (webRTCClient != null) {
                    webRTCClient.onDataChannelStateChange(peerUsername, dataChannel.state());
                }
            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                byte[] bytes;
                if (buffer.data.hasArray()) {
                    bytes = buffer.data.array();
                } else {
                    bytes = new byte[buffer.data.remaining()];
                    buffer.data.get(bytes);
                }

                String message = new String(bytes, StandardCharsets.UTF_8);
                Log.d("WebRTC", "Message received from " + peerUsername + ": " + message);
                
                // Save message to database
                databaseExecutor.execute(() -> {
                    try {
                        MessageEntity messageEntity = new MessageEntity(peerUsername, message, System.currentTimeMillis(), peerUsername);
                        messageDao.insert(messageEntity);
                        Log.d("WebRTC", "Message saved to database from: " + peerUsername);
                    } catch (Exception e) {
                        Log.e("WebRTC", "Error saving message to database: " + e.getMessage());
                    }
                });

                // Notify listeners about the message
                if (messageReceivedListener != null) {
                    messageReceivedListener.onMessageReceived(message);
                }

                // Forward to WebRTCClient for notification handling
                if (webRTCClient != null) {
                    webRTCClient.onMessageReceived(message, peerUsername);
                }
            }
        });
    }

    public void sendMessage(String message, String peerUsername) {
        DataChannel channel = dataChannels.get(peerUsername);
        if (channel != null && channel.state() == DataChannel.State.OPEN) {
            try {
                ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
                channel.send(new DataChannel.Buffer(buffer, false));
                Log.d("WebRTC", "Message sent to " + peerUsername + ": " + message);

                // Save sent message to database
                databaseExecutor.execute(() -> {
                    try {
                        MessageEntity messageEntity = new MessageEntity("You", message, System.currentTimeMillis(), peerUsername);
                        messageDao.insert(messageEntity);
                        Log.d("WebRTC", "Sent message saved to database for: " + peerUsername);
                    } catch (Exception e) {
                        Log.e("WebRTC", "Error saving sent message to database: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                Log.e("WebRTC", "Error sending message: " + e.getMessage());
            }
        } else {
            Log.e("WebRTC", "Cannot send message: DataChannel is null or not open for peer " + peerUsername);
        }
    }

    public List<MessageEntity> getMessageHistory(String peerUsername) {
        try {
            List<MessageEntity> messages = messageDao.getMessagesForPeer(peerUsername);
            Log.d("WebRTC", "Retrieved " + messages.size() + " messages for peer: " + peerUsername);
            return messages;
        } catch (Exception e) {
            Log.e("WebRTC", "Error retrieving message history: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public void setOnMessageReceivedListener(OnMessageReceivedListener listener) {
        this.messageReceivedListener = listener;
    }

    public void setStateChangeListener(OnStateChangeListener listener) {
        this.stateChangeListener = listener;
    }

    public DataChannel getDataChannel(String peerUsername) {
        return dataChannels.get(peerUsername);
    }

    public interface OnMessageReceivedListener {
        void onMessageReceived(String message);
    }

    public interface OnStateChangeListener {
        void onStateChange(DataChannel.State state);
    }

    public void cleanup() {
        for (DataChannel channel : dataChannels.values()) {
            if (channel != null) {
                channel.close();
            }
        }
        dataChannels.clear();
        messageReceivedListener = null;
        stateChangeListener = null;
    }
}
