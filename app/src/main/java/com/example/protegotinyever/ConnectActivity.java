package com.example.protegotinyever;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

import com.example.protegotinyever.tt.DataModelType;
import com.example.protegotinyever.util.CustomSdpObserver;
import com.example.protegotinyever.util.DataChannelHandler;

import org.webrtc.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class ConnectActivity extends AppCompatActivity {
    private EditText peerUsernameInput;
    private FirebaseClient firebaseClient;
    private PeerConnection peerConnection;
    private DataChannel dataChannel;
    private DataChannelHandler.OnMessageReceivedListener messageReceivedListener;
    private String peerUsername;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        peerUsernameInput = findViewById(R.id.peerUsernameInput);
        Button connectButton = findViewById(R.id.connectButton);

        String username = getIntent().getStringExtra("username");
        firebaseClient = new FirebaseClient(username);

        initializePeerConnectionFactory();

        connectButton.setOnClickListener(v -> {
            peerUsername = peerUsernameInput.getText().toString().trim();
            if (!peerUsername.isEmpty()) {
                setupPeerConnection();
                createOffer();
            }
        });



        firebaseClient.listenForSignaling((type, data) -> {
            switch (type) {
                case DataModelType.OFFER:
                    peerUsername = peerUsernameInput.getText().toString().trim();
                    setupPeerConnection();
                    receiveOffer(data);
                    break;
                case DataModelType.ANSWER:
                    receiveAnswer(data);
                    break;
                case DataModelType.ICE:
                    receiveIceCandidate(data);
                    break;
            }
        });
    }

    private void initializePeerConnectionFactory() {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions()
        );
    }

    private void setupPeerConnection() {

        PeerConnectionFactory peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        // ✅ Add STUN server
        PeerConnection.IceServer stunServer = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer();

        // ✅ Use the STUN server in RTC configuration
        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(stunServer);

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                if (peerConnection == null) return;

                // ❌ Don't send ICE candidates if WebRTC is already connected
                if (peerConnection.iceConnectionState() == PeerConnection.IceConnectionState.CONNECTED) {
                    Log.d("WebRTC", "✅ ICE connection is already established, skipping ICE candidate.");
                    return;
                }

                Log.d("WebRTC", "📤 Sending ICE candidate to " + peerUsername);
                firebaseClient.sendSignalingData(peerUsername, DataModelType.ICE,
                        iceCandidate.sdp + "," + iceCandidate.sdpMid + "," + iceCandidate.sdpMLineIndex);
            }



            @Override
            public void onDataChannel(DataChannel dc) {
                Log.d("WebRTC", "✅ DataChannel established!");
                dataChannel = dc;
                DataChannelHandler.getInstance().setDataChannel(dc);

                // ✅ Ensure DataChannel is correctly observed for messages
                dc.registerObserver(new DataChannel.Observer() {
                    @Override
                    public void onBufferedAmountChange(long l) {}

                    @Override
                    public void onStateChange() {
                        Log.d("WebRTC", "🔄 DataChannel state changed: " + dc.state());
                    }

                    @Override
                    public void onMessage(DataChannel.Buffer buffer) {
                        byte[] data = new byte[buffer.data.remaining()];
                        buffer.data.get(data);
                        String receivedMessage = new String(data, StandardCharsets.UTF_8);

                        Log.d("WebRTC", "📩 Message received via DataChannel: " + receivedMessage);

                        if (messageReceivedListener != null) {
                            messageReceivedListener.onMessageReceived(receivedMessage);
                        } else {
                            Log.e("WebRTC", "❌ ERROR: messageReceivedListener is NULL! Message lost.");
                        }
                    }


                });
            }


            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {}

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d("WebRTC", "ICE connection state changed: " + iceConnectionState);

                if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                    Log.d("WebRTC", "✅ WebRTC connection successfully established!");

                    // ✅ Ensure UI update is done in main thread
                    runOnUiThread(() -> {
                        Intent intent = new Intent(ConnectActivity.this, ChatActivity.class);
                        intent.putExtra("peerUsername", peerUsername);
                        Log.d("WebRTC", "📌 Navigating to ChatActivity with peer: " + peerUsername);

                        if (peerUsername == null || peerUsername.isEmpty()) {
                            Log.e("WebRTC", "❌ Error: peerUsername is NULL or EMPTY! Cannot open ChatActivity.");
                            return;
                        }
                        startActivity(intent);
                        finish(); // Close ConnectActivity so it doesn’t interfere with ChatActivity
                    });
                }

                if (iceConnectionState == PeerConnection.IceConnectionState.FAILED) {
                    Log.e("WebRTC", "❌ WebRTC connection failed! Retrying...");
                    retryIceConnection();
                }
            }



            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                Log.d("WebRTC", "Peer connection state changed: " + newState);
            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {}

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}

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
        });

        // ✅ Create DataChannel
        assert peerConnection != null;
        dataChannel = peerConnection.createDataChannel("chat", new DataChannel.Init());
        DataChannelHandler.getInstance().setDataChannel(dataChannel);
    }

    private void retryIceConnection() {
        if (peerConnection != null) {
            Log.d("WebRTC", "Retrying ICE connection...");
            peerConnection.close();
            setupPeerConnection();
            createOffer(); // Restart WebRTC connection
        }
    }

    private void createOffer() {
        Log.d("WebRTC", "Creating WebRTC offer...");

        peerConnection.createOffer(new CustomSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d("WebRTC", "Offer created: " + sessionDescription.description);

                peerConnection.setLocalDescription(new CustomSdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        Log.d("WebRTC", "Local description set. Sending offer to " + peerUsername);
                        firebaseClient.sendSignalingData(peerUsername, DataModelType.OFFER, sessionDescription.description);
                    }
                }, sessionDescription);
            }
        }, new MediaConstraints());
    }

    private void receiveOffer(String sdp) {
        Log.d("WebRTC", "Received offer: " + sdp);

        // 🚨 Ensure peerConnection is initialized before setting remote description
        if (peerConnection == null) {
            Log.e("WebRTC", "❌ peerConnection is null in receiveOffer! Calling setupPeerConnection...");
            setupPeerConnection();
        }

        SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, sdp);
        peerConnection.setRemoteDescription(new CustomSdpObserver() {
            @Override
            public void onSetSuccess() {
                Log.d("WebRTC", "Remote offer set successfully. Creating answer...");

                peerConnection.createAnswer(new CustomSdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        Log.d("WebRTC", "Answer created: " + sessionDescription.description);

                        peerConnection.setLocalDescription(new CustomSdpObserver() {
                            @Override
                            public void onSetSuccess() {
                                Log.d("WebRTC", "Local description set. Sending answer to " + peerUsername);
                                firebaseClient.sendSignalingData(peerUsername, DataModelType.ANSWER, sessionDescription.description);
                            }
                        }, sessionDescription);
                    }
                }, new MediaConstraints());
            }
        }, offer);
    }

    private void receiveIceCandidate(String data) {
        if (peerConnection == null) return;

        // ❌ Don't process ICE candidates if WebRTC is already connected
        if (peerConnection.iceConnectionState() == PeerConnection.IceConnectionState.CONNECTED) {
            Log.d("WebRTC", "✅ ICE connection is established, ignoring new ICE candidates.");
            return;
        }

        String[] parts = data.split(",");
        if (parts.length == 3) {
            IceCandidate iceCandidate = new IceCandidate(parts[1], Integer.parseInt(parts[2]), parts[0]);
            peerConnection.addIceCandidate(iceCandidate);
            Log.d("WebRTC", "✅ Added ICE Candidate: " + iceCandidate.sdp);
        } else {
            Log.e("WebRTC", "❌ Invalid ICE candidate format: " + data);
        }
    }




    private void receiveAnswer(String sdp) {
        SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
        peerConnection.setRemoteDescription(new CustomSdpObserver(), answer);
    }

}
