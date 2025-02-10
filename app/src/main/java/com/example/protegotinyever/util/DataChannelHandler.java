package com.example.protegotinyever.util;

import android.util.Log;

import com.example.protegotinyever.ChatActivity;
import org.webrtc.DataChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class DataChannelHandler {
    private static DataChannelHandler instance;
    private DataChannel dataChannel;

    private OnMessageReceivedListener messageReceivedListener;

    public static DataChannelHandler getInstance() {
        if (instance == null) {
            instance = new DataChannelHandler();
        }
        return instance;
    }


    public void setDataChannel(DataChannel dataChannel) {
        this.dataChannel = dataChannel;
        this.dataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                byte[] data = new byte[buffer.data.remaining()];
                buffer.data.get(data);
                String message = new String(data, StandardCharsets.UTF_8);

                Log.d("WebRTC", "📩 Received message from peer: " + message);

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

    public void sendMessage(String message) {
        if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            DataChannel.Buffer buffer = new DataChannel.Buffer(ByteBuffer.wrap(messageBytes), false);

            Log.d("WebRTC", "📤 Sending message via DataChannel: " + message);

            dataChannel.send(buffer);

            Log.d("WebRTC", "✅ DataChannel send() executed successfully");
        } else {
            Log.e("WebRTC", "❌ DataChannel is NULL or NOT OPEN!");
        }
    }



    public void setOnMessageReceivedListener(OnMessageReceivedListener listener) {
        this.messageReceivedListener = listener;
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
