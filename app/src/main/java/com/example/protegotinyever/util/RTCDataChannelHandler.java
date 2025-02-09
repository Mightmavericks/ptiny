package com.example.protegotinyever.util;

import android.util.Log;
import org.webrtc.DataChannel;
import org.webrtc.PeerConnection;
import java.nio.ByteBuffer;

public class RTCDataChannelHandler {
    private static final String TAG = "RTCDataChannelHandler";
    private DataChannel dataChannel;
    private OnMessageReceivedListener listener;

    public interface OnMessageReceivedListener {
        void onMessageReceived(String message);
    }

    public RTCDataChannelHandler(PeerConnection peerConnection, OnMessageReceivedListener listener) {
        this.listener = listener;
        DataChannel.Init init = new DataChannel.Init();
        this.dataChannel = peerConnection.createDataChannel("chat", init);

        if (this.dataChannel != null) {
            this.dataChannel.registerObserver(new DataChannel.Observer() {
                @Override
                public void onBufferedAmountChange(long previousAmount) {}

                @Override
                public void onStateChange() {
                    Log.d(TAG, "DataChannel state changed: " + dataChannel.state());
                }

                @Override
                public void onMessage(DataChannel.Buffer buffer) {
                    String receivedMessage = bufferToString(buffer);
                    Log.d(TAG, "Message received: " + receivedMessage);
                    if (listener != null) {
                        listener.onMessageReceived(receivedMessage);
                    }
                }
            });
        }
    }

    public void sendMessage(String message) {
        if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
            ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
            DataChannel.Buffer dataBuffer = new DataChannel.Buffer(buffer, false);
            dataChannel.send(dataBuffer);
            Log.d(TAG, "Message sent: " + message);
        } else {
            Log.e(TAG, "DataChannel is not open. Message not sent.");
        }
    }

    private String bufferToString(DataChannel.Buffer buffer) {
        byte[] data = new byte[buffer.data.remaining()];
        buffer.data.get(data);
        return new String(data);
    }

    public void close() {
        if (dataChannel != null) {
            dataChannel.close();
        }
    }
}
