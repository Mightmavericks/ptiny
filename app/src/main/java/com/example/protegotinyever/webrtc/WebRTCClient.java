package com.example.protegotinyever.webrtc;

import android.content.Context;
import android.util.Log;

import com.example.protegotinyever.adapt.MessageEntity;
import com.example.protegotinyever.service.ConnectionManager;
import com.example.protegotinyever.service.WebRTCService;
import com.example.protegotinyever.tt.DataModelType;
import com.example.protegotinyever.util.CustomSdpObserver;
import com.example.protegotinyever.util.DataChannelHandler;
import com.example.protegotinyever.util.FirebaseClient;
import org.webrtc.*;
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
    private final Context context; // ✅ Store Context
    private Map<String, Boolean> hasSentOffers;
    private boolean isBackgroundMode = false; // Track if app is in background
    private WebRTCService webRTCService;
    private int rea = 1;

    public static WebRTCClient getInstance(Context context, FirebaseClient firebaseClient) {
        if (instance == null) {
            instance = new WebRTCClient(context.getApplicationContext(), firebaseClient);
        } else {
            if (instance.firebaseClient == null) {
                instance.firebaseClient = firebaseClient;
                instance.listenForSignaling();
            }
        }
        return instance;
    }

    private WebRTCClient(Context context, FirebaseClient firebaseClient) {
        this.context = context.getApplicationContext();
        this.firebaseClient = firebaseClient;
        this.peerConnections = new HashMap<>();
        this.dataChannels = new HashMap<>();
        this.hasSentOffers = new HashMap<>();
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
            
            // Store the sender's username for this signaling session
            currentPeerUsername = sender;
            
            switch (type) {
                case "OFFER":
                    Log.d("WebRTC", "📩 Received Offer from " + currentPeerUsername);
                    // Auto-accept if previously connected, otherwise show notification
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
        // Only setup a new connection if one doesn't exist for this peer
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
                            break;
                    }
                }

                @Override
                public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState newState) {
                    PeerConnection.Observer.super.onStandardizedIceConnectionChange(newState);
                }

                @Override
                public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                    PeerConnection.Observer.super.onConnectionChange(newState);
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
                public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {
                    PeerConnection.Observer.super.onSelectedCandidatePairChanged(event);
                }

                @Override
                public void onAddStream(MediaStream mediaStream) {}
                @Override
                public void onRemoveStream(MediaStream mediaStream) {}

                @Override
                public void onDataChannel(DataChannel dataChannel) {
                    Log.d("WebRTC", "DataChannel received for peer: " + peerUsername);
                    DataChannelHandler.getInstance(context).setCurrentPeer(peerUsername);
                    DataChannelHandler.getInstance(context).setDataChannel(dataChannel);
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
            
            DataChannelHandler.getInstance(context).setCurrentPeer(peerUsername);
            DataChannelHandler.getInstance(context).setDataChannel(dataChannel);
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

    private void receiveOffer(String sdp) {
        if (currentPeerUsername == null) {
            Log.e("WebRTC", "❌ Cannot process offer: currentPeerUsername is null");
            return;
        }
        
        // Setup peer connection if it doesn't exist
        setupPeerConnection(currentPeerUsername);
        
        PeerConnection peerConnection = peerConnections.get(currentPeerUsername);
        if (peerConnection != null) {
            Log.d("WebRTC", "📩 Processing offer from " + currentPeerUsername);
            SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, sdp);
            peerConnection.setRemoteDescription(new CustomSdpObserver() {
                @Override
                public void onSetSuccess() {
                    createAnswer(currentPeerUsername);
                }
            }, offer);
        }
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
            // Create proper ICE candidate with mid and line index
            IceCandidate iceCandidate = new IceCandidate("audio", 0, sdp);
            peerConnection.addIceCandidate(iceCandidate);
        }
    }

    public boolean isConnected(String peerUsername) {
        PeerConnection peerConnection = peerConnections.get(peerUsername);
        DataChannel dataChannel = dataChannels.get(peerUsername);
        
        if (peerConnection == null || dataChannel == null) return false;
        
        // Check if we have both local and remote descriptions and data channel is open
        boolean isConnected = peerConnection.getLocalDescription() != null && 
                            peerConnection.getRemoteDescription() != null &&
                            dataChannel.state() == DataChannel.State.OPEN;
        
        Log.d("WebRTC", "Connection status for " + peerUsername + ": " + isConnected);
        return isConnected;
    }

    public String getCurrentPeerUsername() {
        return currentPeerUsername;
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
        
        // Check if we have a connection but no remote description yet
        return peerConnection.getRemoteDescription() == null;
    }

    public interface WebRTCListener {
        void onConnected();
        void onConnectionFailed();
    }

    public static void cleanup() {
        if (instance != null) {
            instance.disconnect();
            instance = null;
        }
    }

    // Call this when app goes to background
    public void onBackground() {
        isBackgroundMode = true;
        // Don't disconnect, just update state
        if (dataChannels != null && !dataChannels.isEmpty()) {
            Log.d("WebRTC", "App going to background, maintaining connections");
        }
    }

    // Call this when app comes to foreground
    public void onForeground() {
        isBackgroundMode = false;
        if (!dataChannels.isEmpty()) {
            Log.d("WebRTC", "App returning to foreground, connections maintained");
            if (webrtcListener != null) {
                for (PeerConnection peerConnection : peerConnections.values()) {
                    if (peerConnection != null && peerConnection.getRemoteDescription() != null) {
                        webrtcListener.onConnected(); // Notify UI of existing connections
                    }
                }
            }
        }
    }

    public void onMessageReceived(String message, String fromPeer) {
        Log.d("WebRTC", "Message received from " + fromPeer + ": " + message);
        // Forward to service for notification handling
        if (webRTCService != null) {
            webRTCService.handleMessageNotification(message, fromPeer);
        }
    }

    public void setWebRTCService(WebRTCService service) {
        this.webRTCService = service;
        Log.d("WebRTC", "🔗 WebRTCService reference set");
    }

    // Add getter for peer connections map
    public Map<String, PeerConnection> getPeerConnections() {
        return peerConnections;
    }

    // Add getter for data channels map
    public Map<String, DataChannel> getDataChannels() {
        return dataChannels;
    }

    // Add getter for current peer username
    public String getPeerUsername() {
        return currentPeerUsername;
    }

    // For backward compatibility
    public boolean isConnected() {
        // Return true if connected to any peer
        for (String peerUsername : peerConnections.keySet()) {
            if (isConnected(peerUsername)) return true;
        }
        return false;
    }

    // For backward compatibility
    public boolean isAttemptingConnection() {
        // Return true if attempting connection with any peer
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
                    // Trigger delivery of any stored messages
                    deliverStoredMessages(peerUsername);
                    break;
                case CLOSED:
                case CLOSING:
                    webrtcListener.onConnectionFailed();
                    break;
            }
        }
    }

    private void deliverStoredMessages(String peerUsername) {
        DataChannelHandler dataChannelHandler = DataChannelHandler.getInstance(context);
        List<MessageEntity> storedMessages = dataChannelHandler.getMessageHistory(peerUsername);
        
        for (MessageEntity message : storedMessages) {
            if (message.getSender().equals("You")) {
                // Attempt to resend own messages that might not have been delivered
                dataChannelHandler.sendMessage(message.getMessage(), peerUsername);
            }
        }
    }

    // New method to handle connection acceptance
    public void acceptConnection(String peerUsername, String offerSdp) {
        Log.d("WebRTC", "Accepting connection from: " + peerUsername);
        currentPeerUsername = peerUsername;
        
        // Always setup a new peer connection for the acceptance
        setupPeerConnection(peerUsername);
        
        // Process the offer and create answer
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

    // New method to handle connection rejection
    public void rejectConnection(String peerUsername) {
        Log.d("WebRTC", "Rejecting connection from: " + peerUsername);
        // Clean up any existing connection attempts
        disconnectPeer(peerUsername);
        // Optionally send rejection message through Firebase
        firebaseClient.sendSignalingData(peerUsername, "REJECT", "Connection rejected");
    }

    /*public void handleSignalingData(String fromUsername, DataModelType type, String data) {
        // Set current peer username before processing signaling data
        this.currentPeerUsername = fromUsername;
        
        switch (type) {
            case "OFFER":
                receiveOffer(data);
                break;
            case ANSWER:
                receiveAnswer(data);
                break;
            case ICE:
                receiveIceCandidate(data);
                break;
        }
    }*/
}
