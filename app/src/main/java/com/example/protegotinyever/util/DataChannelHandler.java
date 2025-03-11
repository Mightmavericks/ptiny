package com.example.protegotinyever.util;

import android.content.Context;
import android.util.Log;

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
    private int rea = 1;

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
            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                byte[] bytes = new byte[buffer.data.remaining()];
                buffer.data.get(bytes);
                String message = new String(bytes, StandardCharsets.UTF_8);
                Log.d("WebRTC", "📩 Plaintext message received from: " + peerUsername + ": " + message);
                onMessageReceived(peerUsername, message);
            }
        });
    }

    public void sendMessage(String message, String peerUsername) {
        databaseExecutor.execute(() -> {
            try {
                MessageEntity messageEntity = new MessageEntity("You", message, System.currentTimeMillis(), peerUsername);
                messageDao.insert(messageEntity);
                Log.d("WebRTC", "Message saved to database for: " + peerUsername);
            } catch (Exception e) {
                Log.e("WebRTC", "Error saving sent message to database: " + e.getMessage());
            }
        });

        DataChannel channel = dataChannels.get(peerUsername);
        if (channel != null && channel.state() == DataChannel.State.OPEN) {
            try {
                ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
                channel.send(new DataChannel.Buffer(buffer, false));
                Log.d("WebRTC", "Plaintext message sent to " + peerUsername + ": " + message);
            } catch (Exception e) {
                Log.e("WebRTC", "Error sending message: " + e.getMessage());
            }
        } else {
            Log.d("WebRTC", "Message stored for offline delivery to: " + peerUsername);
        }
    }

    public void storeMessage(String messageText, String peerUsername, String sender) {
        databaseExecutor.execute(() -> {
            try {
                MessageEntity messageEntity = new MessageEntity(sender, messageText, System.currentTimeMillis(), peerUsername);
                messageDao.insert(messageEntity);
                Log.d("WebRTC", "Stored message for " + peerUsername + " from " + sender + ": " + messageText);
            } catch (Exception e) {
                Log.e("WebRTC", "Error storing message: " + e.getMessage());
            }
        });
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

    public void onMessageReceived(String peerUsername, String message) {
        databaseExecutor.execute(() -> {
            try {
                MessageEntity messageEntity = new MessageEntity(peerUsername, message, System.currentTimeMillis(), peerUsername);
                messageDao.insert(messageEntity);
                Log.d("WebRTC", "Message saved to database from: " + peerUsername);

                if (messageReceivedListener != null && peerUsername.equals(currentPeerUsername)) {
                    messageReceivedListener.onMessageReceived(message);
                }

                if (webRTCClient != null && !peerUsername.equals(currentPeerUsername)) {
                    webRTCClient.onMessageReceived(message, peerUsername);
                }
            } catch (Exception e) {
                Log.e("WebRTC", "Error saving received message to database: " + e.getMessage());
            }
        });
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