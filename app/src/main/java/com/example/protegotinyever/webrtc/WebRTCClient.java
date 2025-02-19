package com.example.protegotinyever.webrtc;

import android.content.Context;
import android.util.Log;
import com.example.protegotinyever.tt.DataModelType;
import com.example.protegotinyever.util.CustomSdpObserver;
import com.example.protegotinyever.util.DataChannelHandler;
import com.example.protegotinyever.util.FirebaseClient;
import org.webrtc.*;
import java.util.ArrayList;

public class WebRTCClient {
    private static WebRTCClient instance;
    private PeerConnection peerConnection;
    private DataChannel dataChannel;
    private PeerConnectionFactory peerConnectionFactory;
    private FirebaseClient firebaseClient;
    private String peerUsername;
    private WebRTCListener webrtcListener;
    private boolean isConnected = false;
    private final Context context; // ✅ Store Context
    private boolean hasSentOffer = false;

    public static WebRTCClient getInstance(Context context, FirebaseClient firebaseClient) {
        if (instance == null) {
            instance = new WebRTCClient(context, firebaseClient);
        }
        return instance;
    }

    private WebRTCClient(Context context, FirebaseClient firebaseClient) {
        this.context = context; // ✅ Store context for later use
        this.firebaseClient = firebaseClient;
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
            peerUsername = sender;
            switch (type) {
                case DataModelType.OFFER:
                    Log.d("WebRTC", "📩 Received Offer from " + peerUsername);
                    setupPeerConnection();
                    receiveOffer(data);
                    break;
                case DataModelType.ANSWER:
                    Log.d("WebRTC", "📩 Received Answer from " + peerUsername);
                    receiveAnswer(data);
                    break;
                case DataModelType.ICE:
                    Log.d("WebRTC", "📩 Received ICE Candidate from " + peerUsername);
                    receiveIceCandidate(data);
                    break;
            }
        });
    }

    public void startConnection(String peerUsername) {
        this.peerUsername = peerUsername;
        if (isConnected) {
            Log.d("WebRTC", "🔄 Resetting existing connection...");
            cleanup();
        }
        setupPeerConnection();
        createOffer();
    }

    private void setupPeerConnection() {
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
            dataChannel = null;
            hasSentOffer = false;
            isConnected = false;
            Log.d("WebRTC", "🔄 Cleaning up existing PeerConnection...");
        }

        Log.d("WebRTC", "🔄 Setting up new PeerConnection...");
        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
        // Add multiple STUN servers for better connectivity
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer());
        
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        // Enable DTLS for secure connection
        rtcConfig.enableDtlsSrtp = true;
        // Set SSL role to auto for better compatibility
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        rtcConfig.enableDtlsSrtp = true;

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                firebaseClient.sendSignalingData(peerUsername, DataModelType.ICE,
                        iceCandidate.sdp + "," + iceCandidate.sdpMid + "," + iceCandidate.sdpMLineIndex);
            }

            @Override
            public void onDataChannel(DataChannel dc) {
                Log.d("WebRTC", "✅ DataChannel established!");
                dataChannel = dc;
                DataChannelHandler.getInstance(context.getApplicationContext()).setDataChannel(dc);
                isConnected = true;
                if (webrtcListener != null) webrtcListener.onConnected();
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d("WebRTC", "🔄 ICE connection state changed: " + iceConnectionState);
                switch (iceConnectionState) {
                    case CONNECTED:
                        isConnected = true;
                        if (webrtcListener != null) webrtcListener.onConnected();
                        break;
                    case FAILED:
                    case DISCONNECTED:
                    case CLOSED:
                        isConnected = false;
                        if (webrtcListener != null) webrtcListener.onConnectionFailed();
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
            public void onRenegotiationNeeded() {}
            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}
            @Override
            public void onTrack(RtpTransceiver transceiver) {}
        });

        dataChannel = peerConnection.createDataChannel("chat", new DataChannel.Init());
        DataChannelHandler.getInstance(context.getApplicationContext()).setDataChannel(dataChannel);
    }

    private void createOffer() {
        if (hasSentOffer) {
            Log.d("WebRTC", "🚫 Offer already sent, skipping...");
            return;
        }

        Log.d("WebRTC", "📡 Creating WebRTC offer...");
        peerConnection.createOffer(new CustomSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new CustomSdpObserver(), sessionDescription);
                firebaseClient.sendSignalingData(peerUsername, DataModelType.OFFER, sessionDescription.description);
                hasSentOffer = true;
            }
        }, new MediaConstraints());
    }

    private void receiveOffer(String sdp) {
        Log.d("WebRTC", "📩 Received offer!");
        SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, sdp);
        peerConnection.setRemoteDescription(new CustomSdpObserver() {
            @Override
            public void onSetSuccess() {
                Log.d("WebRTC", "✅ Remote offer set. Creating answer...");
                peerConnection.createAnswer(new CustomSdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        peerConnection.setLocalDescription(new CustomSdpObserver(), sessionDescription);
                        firebaseClient.sendSignalingData(peerUsername, DataModelType.ANSWER, sessionDescription.description);
                        Log.d("WebRTC", "📡 Answer sent to: " + peerUsername);
                    }
                }, new MediaConstraints());
            }
        }, offer);
    }

    private void receiveAnswer(String sdp) {
        Log.d("WebRTC", "📩 Received Answer! Setting remote description...");
        if (peerConnection == null) {
            Log.e("WebRTC", "❌ PeerConnection is null in receiveAnswer! Initializing...");
            setupPeerConnection();
        }

        peerConnection.setRemoteDescription(new CustomSdpObserver(), new SessionDescription(SessionDescription.Type.ANSWER, sdp));
    }

    private void receiveIceCandidate(String data) {
        if (peerConnection == null) return;
        if (peerConnection.getRemoteDescription() == null) {
            Log.e("WebRTC", "❌ Remote description is null! Waiting to add ICE candidate.");
            return;
        }

        String[] parts = data.split(",");
        if (parts.length == 3) {
            IceCandidate iceCandidate = new IceCandidate(parts[1], Integer.parseInt(parts[2]), parts[0]);
            peerConnection.addIceCandidate(iceCandidate);
            Log.d("WebRTC", "✅ ICE Candidate added: " + iceCandidate.sdp);
        } else {
            Log.e("WebRTC", "❌ Invalid ICE candidate format: " + data);
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public String getPeerUsername() {
        return peerUsername;
    }

    public void cleanup() {
        // Only cleanup if we're explicitly told to do so
        // This should only happen when the app is being uninstalled or when we want to force a disconnect
        if (dataChannel != null) {
            dataChannel.close();
            dataChannel = null;
        }
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
        hasSentOffer = false;
        isConnected = false;
        DataChannelHandler.getInstance(context.getApplicationContext()).setDataChannel(null);
    }

    public void disconnect() {
        // Use this method when you want to explicitly close the connection
        cleanup();
    }

    public interface WebRTCListener {
        void onConnected();
        void onConnectionFailed();
    }
}
