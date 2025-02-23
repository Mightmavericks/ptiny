package com.example.protegotinyever.webrtc;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.example.protegotinyever.adapt.MessageEntity;
import com.example.protegotinyever.service.ConnectionManager;
import com.example.protegotinyever.service.WebRTCService;
import com.example.protegotinyever.util.CustomSdpObserver;
import com.example.protegotinyever.util.DataChannelHandler;
import com.example.protegotinyever.util.FirebaseClient;
import com.example.protegotinyever.util.MessageEncryptor;
import org.webrtc.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebRTCClient {
    private static WebRTCClient instance;
    private Map<String, PeerConnection> peerConnections;
    private Map<String, DataChannel> dataChannels;
    private PeerConnectionFactory peerConnectionFactory;
    private FirebaseClient firebaseClient;
    private String currentPeerUsername;
    private WebRTCListener webrtcListener;
    private boolean isConnected = false;
    private boolean isAttemptingConnection = false;
    private final Context context;
    private Map<String, Boolean> hasSentOffers;
    private boolean isBackgroundMode = false;
    private WebRTCService webRTCService;
    private DataChannelHandler dataChannelHandler;
    private static final int CHUNK_SIZE = 16384; // 16 KB chunks (conservative, well below 256 KB limit)
    private Map<String, byte[]> receivedChunks = new HashMap<>();
    private int rea = 1;

    public static WebRTCClient getInstance(Context context, FirebaseClient firebaseClient) {
        if (instance == null) {
            instance = new WebRTCClient(context.getApplicationContext(), firebaseClient);
        } else if (instance.firebaseClient == null) {
            instance.firebaseClient = firebaseClient;
            instance.listenForSignaling();
        }
        return instance;
    }

    private WebRTCClient(Context context, FirebaseClient firebaseClient) {
        this.context = context.getApplicationContext();
        this.firebaseClient = firebaseClient;
        this.peerConnections = new HashMap<>();
        this.dataChannels = new HashMap<>();
        this.hasSentOffers = new HashMap<>();
        this.dataChannelHandler = DataChannelHandler.getInstance(context);
        initializePeerConnectionFactory(context);
        listenForSignaling();
    }

    public void setWebRTCListener(WebRTCListener listener) {
        this.webrtcListener = listener;
    }

    private void initializePeerConnectionFactory(Context context) {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        );
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();
    }

    private void listenForSignaling() {
        firebaseClient.listenForSignaling((type, data, sender) -> {
            Log.d("WebRTC", "📩 Received signaling data: " + type + " from " + sender);
            currentPeerUsername = sender;
            switch (type) {
                case "OFFER":
                    Log.d("WebRTC", "📩 Received Offer from " + currentPeerUsername);
                    if (ConnectionManager.getInstance(context).isUserConnected(currentPeerUsername)) {
                        Log.d("WebRTC", "Auto-accepting connection from previously connected user: " + currentPeerUsername);
                        acceptConnection(currentPeerUsername, data);
                    } else if (!isConnected(currentPeerUsername)) {
                        if (webRTCService != null) {
                            webRTCService.showConnectionRequestNotification(currentPeerUsername, data);
                        } else {
                            Log.e("WebRTC", "Cannot show connection request - service not available");
                        }
                    }
                    break;
                case "ANSWER":
                    Log.d("WebRTC", "📩 Received Answer from " + currentPeerUsername);
                    receiveAnswer(data);
                    break;
                case "ICE":
                    Log.d("WebRTC", "📩 Received ICE Candidate from " + currentPeerUsername);
                    receiveIceCandidate(data);
                    break;
            }
        });
    }

    public void startConnection(String peerUsername) {
        this.currentPeerUsername = peerUsername;
        setupPeerConnection(peerUsername);
        createOffer(peerUsername);
    }

    private void setupPeerConnection(String peerUsername) {
        if (!peerConnections.containsKey(peerUsername)) {
            Log.d("WebRTC", "🔄 Setting up new PeerConnection for " + peerUsername);
            ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
            iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
            iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
            iceServers.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer());
            iceServers.add(PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer());

            PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
            rtcConfig.enableDtlsSrtp = true;
            rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
            rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
            rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
            rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

            PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
                @Override
                public void onIceCandidate(IceCandidate iceCandidate) {
                    firebaseClient.sendSignalingData(peerUsername, "ICE", iceCandidate.sdp);
                }

                @Override
                public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                    Log.d("WebRTC", "🔄 ICE connection state changed for " + peerUsername + ": " + iceConnectionState);
                    switch (iceConnectionState) {
                        case CONNECTED:
                            if (webrtcListener != null) webrtcListener.onConnected();
                            break;
                        case FAILED:
                        case DISCONNECTED:
                        case CLOSED:
                            if (webrtcListener != null) webrtcListener.onConnectionFailed();
                            // Attempt reconnection if still marked as connected
                            if (ConnectionManager.getInstance(context).isUserConnected(peerUsername)) {
                                Log.d("WebRTC", "Connection lost, attempting to reconnect to " + peerUsername);
                                startConnection(peerUsername);
                            }
                            break;
                    }
                }

                @Override
                public void onSignalingChange(PeerConnection.SignalingState signalingState) {}
                @Override
                public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}
                @Override
                public void onIceConnectionReceivingChange(boolean b) {}
                @Override
                public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}
                @Override
                public void onAddStream(MediaStream mediaStream) {}
                @Override
                public void onRemoveStream(MediaStream mediaStream) {}
                @Override
                public void onDataChannel(DataChannel dataChannel) {
                    Log.d("WebRTC", "DataChannel received for peer: " + peerUsername);
                    dataChannelHandler.setCurrentPeer(peerUsername);
                    dataChannelHandler.setDataChannel(dataChannel);
                    setupDataChannelObserver(dataChannel, peerUsername);
                }
                @Override
                public void onRenegotiationNeeded() {}
                @Override
                public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}
                @Override
                public void onTrack(RtpTransceiver transceiver) {}
            });

            DataChannel dataChannel = peerConnection.createDataChannel("chat", new DataChannel.Init());
            peerConnections.put(peerUsername, peerConnection);
            dataChannels.put(peerUsername, dataChannel);
            hasSentOffers.put(peerUsername, false);
            dataChannelHandler.setCurrentPeer(peerUsername);
            dataChannelHandler.setDataChannel(dataChannel);
            setupDataChannelObserver(dataChannel, peerUsername);
        }
    }

    private void setupDataChannelObserver(DataChannel dataChannel, String peerUsername) {
        dataChannel.registerObserver(new DataChannel.Observer() {
            private String fileName;
            private String fileType;
            private int totalLength = -1;
            private byte[] fullData;

            @Override
            public void onBufferedAmountChange(long previousAmount) {}

            @Override
            public void onStateChange() {
                Log.d("WebRTCClient", "DataChannel state changed for " + peerUsername + ": " + dataChannel.state());
                onDataChannelStateChange(peerUsername, dataChannel.state());
            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                byte[] data = new byte[buffer.data.remaining()];
                buffer.data.get(data);
                Log.d("WebRTCClient", "Received data from " + peerUsername + ", length: " + data.length);

                try {
                    String header = new String(data, 0, Math.min(100, data.length), "UTF-8");
                    if (header.startsWith("FILE:")) {
                        String[] parts = header.split(":", 5);
                        fileName = parts[1];
                        fileType = parts[2];
                        totalLength = Integer.parseInt(parts[3]);
                        fullData = new byte[totalLength];
                        Log.d("WebRTCClient", "Received file metadata from " + peerUsername + ": " + header);
                    } else if (header.startsWith("CHUNK:") && totalLength != -1) {
                        String[] parts = header.split(":", 4);
                        int chunkTotalLength = Integer.parseInt(parts[1]);
                        int offset = Integer.parseInt(parts[2]);
                        int headerLength = parts[0].length() + parts[1].length() + parts[2].length() + 3;
                        byte[] chunkData = new byte[data.length - headerLength];
                        System.arraycopy(data, headerLength, chunkData, 0, chunkData.length);

                        byte[] decryptedChunk = MessageEncryptor.decryptData(chunkData);
                        System.arraycopy(decryptedChunk, 0, fullData, offset, decryptedChunk.length);
                        Log.d("WebRTCClient", "Received chunk from " + peerUsername + ", offset: " + offset + ", length: " + decryptedChunk.length);

                        if (offset + decryptedChunk.length >= totalLength) {
                            File savedFile = saveFileToInternalStorage(fullData, fileName, fileType, peerUsername);
                            if (savedFile != null) {
                                dataChannelHandler.onMessageReceived(peerUsername, "Received file: " + fileName + " at " + savedFile.getAbsolutePath());
                                if (webrtcListener != null) {
                                    webrtcListener.onMessageReceived("Received file: " + fileName + " at " + savedFile.getAbsolutePath(), peerUsername);
                                }
                            } else {
                                throw new IOException("Failed to save decrypted file");
                            }
                            // Reset for next file
                            fileName = null;
                            fileType = null;
                            totalLength = -1;
                            fullData = null;
                        }
                    } else {
                        String message = MessageEncryptor.decryptMessage(data);
                        Log.d("WebRTCClient", "Decrypted message from " + peerUsername + ": " + message);
                        dataChannelHandler.onMessageReceived(peerUsername, message);
                        if (webrtcListener != null) {
                            webrtcListener.onMessageReceived(message, peerUsername);
                        }
                    }
                } catch (Exception e) {
                    Log.e("WebRTCClient", "Error processing message from " + peerUsername + ": " + e.getMessage());
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                            Toast.makeText(context, "Failed to process message: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            }
        });
    }

    public void sendEncryptedMessage(String message, String peerUsername) {
        try {
            sendEncryptedMessage(message.getBytes("UTF-8"), peerUsername, false, null, null);
        } catch (Exception e) {
            Log.e("WebRTCClient", "Error encoding string message: " + e.getMessage());
            dataChannelHandler.storeMessage(message, peerUsername, "You");
        }
    }

    public void sendEncryptedMessage(byte[] data, String peerUsername, boolean isFile, String fileName, String fileType) {
        DataChannel dataChannel = dataChannels.get(peerUsername);
        if (dataChannel == null || dataChannel.state() != DataChannel.State.OPEN) {
            Log.e("WebRTCClient", "No open data channel for " + peerUsername + ", storing data");
            dataChannelHandler.storeMessage(isFile ? "File: " + fileName : new String(data), peerUsername, "You");
            startConnection(peerUsername); // Attempt to reconnect
            return;
        }

        try {
            String senderPhone = firebaseClient.getCurrentUserPhone();
            String senderEmail = senderPhone + "@example.com";

            if (isFile) {
                // Send file metadata first
                String metadata = "FILE:" + fileName + ":" + fileType + ":" + data.length + ":";
                byte[] metadataBytes = metadata.getBytes("UTF-8");
                dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(metadataBytes), true));
                Log.d("WebRTCClient", "Sent file metadata to " + peerUsername + ": " + metadata);

                // Chunk and send file data
                int totalLength = data.length;
                for (int offset = 0; offset < totalLength; offset += CHUNK_SIZE) {
                    int chunkLength = Math.min(CHUNK_SIZE, totalLength - offset);
                    byte[] chunk = new byte[chunkLength];
                    System.arraycopy(data, offset, chunk, 0, chunkLength);

                    // Encrypt each chunk individually
                    MessageEncryptor.EncryptionResult result = MessageEncryptor.encryptData(chunk, senderEmail, senderPhone);

                    // Prefix with chunk header
                    String chunkHeader = "CHUNK:" + totalLength + ":" + offset + ":";
                    byte[] chunkHeaderBytes = chunkHeader.getBytes("UTF-8");
                    byte[] chunkWithHeader = new byte[chunkHeaderBytes.length + result.combinedData.length];
                    System.arraycopy(chunkHeaderBytes, 0, chunkWithHeader, 0, chunkHeaderBytes.length);
                    System.arraycopy(result.combinedData, 0, chunkWithHeader, chunkHeaderBytes.length, result.combinedData.length);

                    DataChannel.Buffer buffer = new DataChannel.Buffer(ByteBuffer.wrap(chunkWithHeader), true);
                    if (!dataChannel.send(buffer)) {
                        throw new IOException("Failed to send chunk at offset " + offset);
                    }
                    Log.d("WebRTCClient", "Sent encrypted chunk to " + peerUsername + ", offset: " + offset + ", length: " + chunkLength);
                }

                Log.d("WebRTCClient", "Sent encrypted file to " + peerUsername + ", total length: " + totalLength);
            } else {
                // Handle text messages (no chunking needed)
                MessageEncryptor.EncryptionResult result = MessageEncryptor.encryptData(data, senderEmail, senderPhone);
                dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(result.combinedData), true));
                Log.d("WebRTCClient", "Sent encrypted message to " + peerUsername + ", length: " + result.combinedData.length);
            }

            dataChannelHandler.storeMessage(isFile ? "File: " + fileName : new String(data), peerUsername, "You");
        } catch (Exception e) {
            Log.e("WebRTCClient", "Error sending encrypted data to " + peerUsername + ": " + e.getMessage());
            dataChannelHandler.storeMessage(isFile ? "File: " + fileName : new String(data), peerUsername, "You");
            startConnection(peerUsername); // Attempt to reconnect
        }
    }

    private File saveFileToInternalStorage(byte[] decryptedData, String fileName, String fileType, String peerUsername) {
        // Use Media Store API to save to Downloads folder
        ContentResolver resolver = context.getContentResolver();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, fileType);
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/From_" + peerUsername);

        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri fileUri = null;

        try {
            fileUri = resolver.insert(collection, contentValues);
            if (fileUri == null) {
                throw new IOException("Failed to create new MediaStore record for " + fileName);
            }

            try (OutputStream os = resolver.openOutputStream(fileUri)) {
                if (os == null) {
                    throw new IOException("Failed to open output stream for URI: " + fileUri);
                }
                os.write(decryptedData);
                Log.d("WebRTCClient", "File saved to Downloads folder: " + fileUri.toString());
            }

            // Convert URI to File for compatibility with existing code
            File file = new File(getRealPathFromUri(fileUri, fileName, peerUsername));
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "File saved to Downloads/From_" + peerUsername + ": " + fileName, Toast.LENGTH_LONG).show()
            );
            return file;
        } catch (IOException e) {
            Log.e("WebRTCClient", "Error saving file to Downloads folder: " + fileName + " - " + e.getMessage());
            if (fileUri != null) {
                resolver.delete(fileUri, null, null); // Clean up failed insert
            }
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "Failed to save file: " + fileName + " - " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
            return null;
        }
    }
    private void createOffer(String peerUsername) {
        if (Boolean.TRUE.equals(hasSentOffers.get(peerUsername))) {
            Log.d("WebRTC", "🚫 Offer already sent to " + peerUsername + ", skipping...");
            return;
        }

        PeerConnection peerConnection = peerConnections.get(peerUsername);
        if (peerConnection != null) {
            Log.d("WebRTC", "📡 Creating WebRTC offer for " + peerUsername);
            peerConnection.createOffer(new CustomSdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    peerConnection.setLocalDescription(new CustomSdpObserver(), sessionDescription);
                    firebaseClient.sendSignalingData(peerUsername, "OFFER", sessionDescription.description);
                    hasSentOffers.put(peerUsername, true);
                }
            }, new MediaConstraints());
        }
    }

    private void processFullMessage(byte[] data, String peerUsername) throws Exception {
        String metadataStr = new String(data, 0, Math.min(100, data.length), "UTF-8");
        if (metadataStr.startsWith("FILE:")) {
            String[] parts = metadataStr.split(":", 5);
            String fileName = parts[1];
            String fileType = parts[2];
            int fileSize = Integer.parseInt(parts[3]);
            int metadataLength = parts[0].length() + parts[1].length() + parts[2].length() + parts[3].length() + 4;
            byte[] fileData = new byte[data.length - metadataLength];
            System.arraycopy(data, metadataLength, fileData, 0, fileData.length);

            byte[] decryptedFileBytes = MessageEncryptor.decryptData(fileData);
            Log.d("WebRTCClient", "Decrypted file from " + peerUsername + ", length: " + decryptedFileBytes.length);
            File savedFile = saveFileToInternalStorage(decryptedFileBytes, fileName, fileType, peerUsername);
            if (savedFile != null) {
                dataChannelHandler.onMessageReceived(peerUsername, "Received file: " + fileName + " at " + savedFile.getAbsolutePath());
                if (webrtcListener != null) {
                    webrtcListener.onMessageReceived("Received file: " + fileName + " at " + savedFile.getAbsolutePath(), peerUsername);
                }
            } else {
                throw new IOException("Failed to save decrypted file");
            }
        } else {
            String message = MessageEncryptor.decryptMessage(data);
            Log.d("WebRTCClient", "Decrypted message from " + peerUsername + ": " + message);
            dataChannelHandler.onMessageReceived(peerUsername, message);
            if (webrtcListener != null) {
                webrtcListener.onMessageReceived(message, peerUsername);
            }
        }
    }

    private void receiveAnswer(String sdp) {
        if (currentPeerUsername == null) {
            Log.e("WebRTC", "❌ Cannot process answer: currentPeerUsername is null");
            return;
        }

        PeerConnection peerConnection = peerConnections.get(currentPeerUsername);
        if (peerConnection != null) {
            Log.d("WebRTC", "📩 Processing answer from " + currentPeerUsername);
            SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
            peerConnection.setRemoteDescription(new CustomSdpObserver(), answer);
        }
    }

    private void receiveIceCandidate(String sdp) {
        if (currentPeerUsername == null) {
            Log.e("WebRTC", "❌ Cannot process ICE candidate: currentPeerUsername is null");
            return;
        }

        PeerConnection peerConnection = peerConnections.get(currentPeerUsername);
        if (peerConnection != null) {
            Log.d("WebRTC", "🧊 Processing ICE candidate from " + currentPeerUsername);
            IceCandidate iceCandidate = new IceCandidate("audio", 0, sdp);
            peerConnection.addIceCandidate(iceCandidate);
        }
    }

    public boolean isConnected(String peerUsername) {
        PeerConnection peerConnection = peerConnections.get(peerUsername);
        DataChannel dataChannel = dataChannels.get(peerUsername);
        if (peerConnection == null || dataChannel == null) return false;
        boolean isConnected = peerConnection.getLocalDescription() != null &&
                peerConnection.getRemoteDescription() != null &&
                dataChannel.state() == DataChannel.State.OPEN;
        Log.d("WebRTC", "Connection status for " + peerUsername + ": " + isConnected);
        return isConnected;
    }

    public void disconnectPeer(String peerUsername) {
        DataChannel dataChannel = dataChannels.remove(peerUsername);
        if (dataChannel != null) {
            dataChannel.close();
        }
        PeerConnection peerConnection = peerConnections.remove(peerUsername);
        if (peerConnection != null) {
            peerConnection.close();
        }
        hasSentOffers.remove(peerUsername);
        if (peerUsername.equals(currentPeerUsername)) {
            currentPeerUsername = null;
        }
    }

    public void disconnect() {
        for (DataChannel dataChannel : dataChannels.values()) {
            if (dataChannel != null) {
                dataChannel.close();
            }
        }
        for (PeerConnection peerConnection : peerConnections.values()) {
            if (peerConnection != null) {
                peerConnection.close();
            }
        }
        dataChannels.clear();
        peerConnections.clear();
        hasSentOffers.clear();
        currentPeerUsername = null;
        firebaseClient = null;
    }

    public boolean isAttemptingConnection(String peerUsername) {
        PeerConnection peerConnection = peerConnections.get(peerUsername);
        if (peerConnection == null) return false;
        return peerConnection.getRemoteDescription() == null;
    }

    public interface WebRTCListener {
        void onConnected();
        void onConnectionFailed();
        void onMessageReceived(String message, String peerUsername);
    }

    public static void cleanup() {
        if (instance != null) {
            instance.disconnect();
            instance = null;
        }
    }

    public void onBackground() {
        isBackgroundMode = true;
        Log.d("WebRTC", "App going to background, maintaining connections");
        // Don’t close connections, just mark as background
    }

    public void onForeground() {
        isBackgroundMode = false;
        Log.d("WebRTC", "App returning to foreground, restoring connections");
        if (!dataChannels.isEmpty() && firebaseClient != null) {
            ConnectionManager connectionManager = ConnectionManager.getInstance(context);
            for (String peerUsername : connectionManager.getConnectedUsers()) {
                DataChannel dataChannel = dataChannels.get(peerUsername);
                if (dataChannel == null || dataChannel.state() != DataChannel.State.OPEN) {
                    Log.d("WebRTC", "Restoring connection to " + peerUsername);
                    startConnection(peerUsername);
                }
            }
            if (webrtcListener != null) {
                for (PeerConnection peerConnection : peerConnections.values()) {
                    if (peerConnection != null && peerConnection.getRemoteDescription() != null) {
                        webrtcListener.onConnected();
                    }
                }
            }
        }
    }

    public void onMessageReceived(String message, String fromPeer) {
        Log.d("WebRTC", "Message received from " + fromPeer + ": " + message);
        if (webRTCService != null) {
            webRTCService.handleMessageNotification(message, fromPeer);
        }
    }

    public void setWebRTCService(WebRTCService service) {
        this.webRTCService = service;
        Log.d("WebRTC", "🔗 WebRTCService reference set");
    }

    public Map<String, PeerConnection> getPeerConnections() {
        return peerConnections;
    }

    public Map<String, DataChannel> getDataChannels() {
        return dataChannels;
    }

    public String getPeerUsername() {
        return currentPeerUsername;
    }

    public boolean isConnected() {
        for (String peerUsername : peerConnections.keySet()) {
            if (isConnected(peerUsername)) return true;
        }
        return false;
    }

    public boolean isAttemptingConnection() {
        for (String peerUsername : peerConnections.keySet()) {
            if (isAttemptingConnection(peerUsername)) return true;
        }
        return false;
    }

    public void onDataChannelStateChange(String peerUsername, DataChannel.State state) {
        Log.d("WebRTC", "DataChannel state changed for " + peerUsername + ": " + state);
        if (webrtcListener != null) {
            switch (state) {
                case OPEN:
                    webrtcListener.onConnected();
                    deliverStoredMessages(peerUsername);
                    break;
                case CLOSED:
                case CLOSING:
                    webrtcListener.onConnectionFailed();
                    if (ConnectionManager.getInstance(context).isUserConnected(peerUsername)) {
                        startConnection(peerUsername); // Reconnect if still marked as connected
                    }
                    break;
            }
        }
    }

    private void deliverStoredMessages(String peerUsername) {
        List<MessageEntity> storedMessages = dataChannelHandler.getMessageHistory(peerUsername);
        for (MessageEntity message : storedMessages) {
            if (message.getSender().equals("You")) {
                sendEncryptedMessage(message.getMessage(), peerUsername);
            }
        }
    }

    public void acceptConnection(String peerUsername, String offerSdp) {
        Log.d("WebRTC", "Accepting connection from: " + peerUsername);
        currentPeerUsername = peerUsername;
        setupPeerConnection(peerUsername);
        PeerConnection peerConnection = peerConnections.get(peerUsername);
        if (peerConnection != null) {
            Log.d("WebRTC", "📩 Processing offer for acceptance from " + peerUsername);
            SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, offerSdp);
            peerConnection.setRemoteDescription(new CustomSdpObserver() {
                @Override
                public void onSetSuccess() {
                    createAnswer(peerUsername);
                }
            }, offer);
        } else {
            Log.e("WebRTC", "❌ Failed to setup peer connection for " + peerUsername);
        }
    }

    public void rejectConnection(String peerUsername) {
        Log.d("WebRTC", "Rejecting connection from: " + peerUsername);
        disconnectPeer(peerUsername);
        firebaseClient.sendSignalingData(peerUsername, "REJECT", "Connection rejected");
    }

    private void createAnswer(String peerUsername) {
        PeerConnection peerConnection = peerConnections.get(peerUsername);
        if (peerConnection != null) {
            peerConnection.createAnswer(new CustomSdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    peerConnection.setLocalDescription(new CustomSdpObserver(), sessionDescription);
                    firebaseClient.sendSignalingData(peerUsername, "ANSWER", sessionDescription.description);
                }
            }, new MediaConstraints());
        }
    }

    private File saveFile(byte[] data, String fileName, String fileType) {
        ContentResolver resolver = context.getContentResolver();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, fileType);
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

        Uri collection;
        if (fileType.startsWith("image/")) {
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else if (fileType.startsWith("audio/")) {
            collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else if (fileType.startsWith("video/")) {
            collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        }

        Uri fileUri = null;
        try {
            fileUri = resolver.insert(collection, contentValues);
            if (fileUri == null) {
                throw new IOException("Failed to create new MediaStore record");
            }

            try (OutputStream os = resolver.openOutputStream(fileUri)) {
                if (os == null) {
                    throw new IOException("Failed to open output stream for URI: " + fileUri);
                }
                os.write(data);
                Log.d("WebRTCClient", "File saved: " + fileUri.toString());
            }

            // Convert URI to File for compatibility with existing code
            File file = new File(getRealPathFromUri(fileUri));
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "File saved to " + fileName, Toast.LENGTH_LONG).show()
            );
            return file;
        } catch (IOException e) {
            Log.e("WebRTCClient", "Error saving file: " + fileName + " - " + e.getMessage());
            if (fileUri != null) {
                resolver.delete(fileUri, null, null); // Clean up failed insert
            }
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "Failed to save file: " + fileName + " - " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
            return null;
        }
    }
    private String getRealPathFromUri(Uri uri, String fileName, String peerUsername) {
        // This is an approximation since Media Store URIs don’t directly map to file paths on Android 10+
        String basePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        return basePath + "/From_" + peerUsername + "/" + fileName;
    }
    private String getRealPathFromUri(Uri uri) {
        String path = uri.getPath();
        if (path != null && path.startsWith("/document/primary:")) {
            return Environment.getExternalStorageDirectory() + "/" + path.substring("/document/primary:".length());
        }
        return path != null ? path : uri.toString();
    }
}