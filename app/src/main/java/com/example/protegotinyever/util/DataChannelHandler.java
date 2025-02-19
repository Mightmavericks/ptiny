package com.example.protegotinyever.util;

import android.content.Context;
import android.util.Log;
import androidx.room.Room;
import com.example.protegotinyever.db.ChatDatabase;
import com.example.protegotinyever.db.MessageDao;
import com.example.protegotinyever.adapt.MessageEntity;
import org.webrtc.DataChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataChannelHandler {
    private static DataChannelHandler instance;
    private DataChannel dataChannel;
    private OnMessageReceivedListener messageReceivedListener;
    private OnStateChangeListener stateChangeListener;
    private ChatDatabase chatDatabase;
    private MessageDao messageDao;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private String currentPeerUsername;

    public static synchronized DataChannelHandler getInstance(Context context) {
        if (instance == null) {
            instance = new DataChannelHandler();
            instance.setContext(context);
        }
        return instance;
    }

    public void setCurrentPeer(String peerUsername) {
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
                saveMessageToDatabase(currentPeerUsername, message, currentPeerUsername);
                notifyMessageReceived(message);
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
        if (dataChannel != null) {
            listener.onStateChange(dataChannel.state());
        }
    }

    private void notifyMessageReceived(String message) {
        if (messageReceivedListener != null) {
            messageReceivedListener.onMessageReceived(message);
        }
    }

    public void sendMessage(String message, String peerUsername) {
        if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            DataChannel.Buffer buffer = new DataChannel.Buffer(ByteBuffer.wrap(messageBytes), false);
            dataChannel.send(buffer);
            saveMessageToDatabase("You", message, currentPeerUsername);
        } else {
            Log.e("WebRTC", "❌ DataChannel is NULL or NOT OPEN!");
        }
    }

    public void saveMessageToDatabase(String sender, String message, String peerUsername) {
        databaseExecutor.execute(() -> {
            messageDao.insertMessage(new MessageEntity(sender, message, System.currentTimeMillis(), peerUsername));
        });
    }

    public List<MessageEntity> getMessageHistory(String peerUsername) {
        return messageDao.getMessagesForPeer(peerUsername);
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
