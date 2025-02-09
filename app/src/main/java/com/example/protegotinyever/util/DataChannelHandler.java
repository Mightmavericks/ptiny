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

                Log.d("WebRTC", "📩 Received message: " + message);

                // ✅ Update the UI in ChatActivity
                if (messageReceivedListener != null) {
                    messageReceivedListener.onMessageReceived(message);
                }
            }

            @Override
            public void onStateChange() {
                Log.d("WebRTC", "DataChannel state changed: " + dataChannel.state());
            }

            @Override
            public void onBufferedAmountChange(long previousAmount) {}
        });
    }


    public void sendMessage(String message) {
        if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            DataChannel.Buffer buffer = new DataChannel.Buffer(ByteBuffer.wrap(messageBytes), false);
            dataChannel.send(buffer);
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
