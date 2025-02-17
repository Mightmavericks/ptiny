package com.example.protegotinyever.util;

import android.util.Log;
import org.webrtc.DataChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class DataChannelHandler {
    private static DataChannelHandler instance;
    private DataChannel dataChannel;
    private OnMessageReceivedListener messageReceivedListener;

    // 🔥 Store chat history per peer
    private final HashMap<String, List<String>> messageHistory = new HashMap<>();

    public static DataChannelHandler getInstance() {
        if (instance == null) {
            instance = new DataChannelHandler();
        }
        return instance;
    }

    public void setDataChannel(DataChannel dataChannel) {
        this.dataChannel = dataChannel;
        registerDataChannelObserver();
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

                Log.d("WebRTC", "📩 Received message from peer: " + message);

                // 🔥 Store received message
                addMessageToHistory("Peer", message);

                notifyMessageReceived(message);
            }

            @Override
            public void onStateChange() {
                Log.d("WebRTC", "⚡ DataChannel state changed: " + dataChannel.state());
            }

            @Override
            public void onBufferedAmountChange(long previousAmount) {}
        });
    }

    public void notifyMessageReceived(String message) {
        if (messageReceivedListener != null) {
            Log.d("WebRTC", "📩 Notifying ChatActivity of received message: " + message);
            messageReceivedListener.onMessageReceived(message);
        } else {
            Log.e("WebRTC", "❌ No listener set in ChatActivity! Message lost.");
        }
    }

    public void sendMessage(String message, String peerUsername) {
        if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            DataChannel.Buffer buffer = new DataChannel.Buffer(ByteBuffer.wrap(messageBytes), false);

            Log.d("WebRTC", "📤 Sending message via DataChannel: " + message);
            dataChannel.send(buffer);

            // 🔥 Store sent message
            addMessageToHistory("You", message);
        } else {
            Log.e("WebRTC", "❌ DataChannel is NULL or NOT OPEN!");
        }
    }

    // 🔥 Store messages for a user
    private void addMessageToHistory(String sender, String message) {
        String peer = sender.equals("You") ? "Peer" : sender;
        messageHistory.putIfAbsent(peer, new ArrayList<>());
        messageHistory.get(peer).add(sender + ": " + message);
    }

    // 🔥 Retrieve stored messages
    public List<String> getMessageHistory(String peerUsername) {
        return messageHistory.getOrDefault(peerUsername, new ArrayList<>());
    }

    public void setOnMessageReceivedListener(OnMessageReceivedListener listener) {
        this.messageReceivedListener = listener;
        registerDataChannelObserver();
    }

    public interface OnMessageReceivedListener {
        void onMessageReceived(String message);
    }

    public void close() {
        if (dataChannel != null) {
            dataChannel.close();
            dataChannel = null;
        }
    }
}
