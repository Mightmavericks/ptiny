package com.example.protegotinyever.act;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.protegotinyever.tt.UserAdapter;
import com.example.protegotinyever.tt.UserModel;
import com.example.protegotinyever.util.DataChannelHandler;
import com.example.protegotinyever.util.FirebaseClient;
import com.example.protegotinyever.tt.DataModelType;
import com.example.protegotinyever.util.CustomSdpObserver;
import com.example.protegotinyever.R;
import org.webrtc.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ConnectActivity extends AppCompatActivity {
    private FirebaseClient firebaseClient;
    private PeerConnection peerConnection;
    private DataChannel dataChannel;
    private RecyclerView contactsRecyclerView;
    private TextView noUsersText, dataChannelStatus;
    private List<UserModel> availableContacts = new ArrayList<>();
    private UserAdapter contactsAdapter;
    private String peerUsername, currentUser, currentUserPhone;
    private static final int CONTACTS_PERMISSION_CODE = 100;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        contactsRecyclerView = findViewById(R.id.contactsRecyclerView);
        noUsersText = findViewById(R.id.noUsersText);
        dataChannelStatus = findViewById(R.id.dataconnstatus);

        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        currentUser = getIntent().getStringExtra("username");
        currentUserPhone = getIntent().getStringExtra("phoneNumber");
        firebaseClient = new FirebaseClient(currentUser, currentUserPhone);

        checkContactsPermission();

        initializePeerConnectionFactory();
        // ✅ Auto-listen for WebRTC signaling
        firebaseClient.listenForSignaling((type, data, sender) -> {
            peerUsername = sender; // ✅ Store sender's username

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

    // ✅ Check Contacts Permission
    private void checkContactsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, CONTACTS_PERMISSION_CODE);
        } else {
            fetchContactsAndCheckUsers();
        }
    }
    private void initializePeerConnectionFactory() {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions()
        );
    }

    // ✅ Request Permission Result Handling
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CONTACTS_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchContactsAndCheckUsers();
        }
    }

    // ✅ Fetch contacts and check for registered users
    private void fetchContactsAndCheckUsers() {
        List<String> phoneContacts = getDeviceContacts();
        firebaseClient.getRegisteredUsers(users -> {
            availableContacts.clear();

            for (UserModel user : users) {
                if (user.getPhone() == null) continue;

                String formattedPhone = formatPhoneNumber(user.getPhone());
                String formattedCurrentUserPhone = formatPhoneNumber(currentUserPhone);

                if (!formattedPhone.equals(formattedCurrentUserPhone) && phoneContacts.contains(formattedPhone)) {
                    availableContacts.add(user);
                }
            }
            runOnUiThread(this::updateUI);
        });
    }

    // ✅ Format Phone Number (remove country code, spaces, etc.)
    private String formatPhoneNumber(String phone) {
        phone = phone.replaceAll("[^0-9]", ""); // Remove non-numeric characters
        if (phone.length() > 10) {
            return phone.substring(phone.length() - 10); // Keep last 10 digits
        }
        return phone;
    }

    // ✅ Get Device Contacts
    private List<String> getDeviceContacts() {
        List<String> contactList = new ArrayList<>();
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);

        if (cursor != null) {
            int columnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            if (columnIndex != -1) {
                while (cursor.moveToNext()) {
                    contactList.add(formatPhoneNumber(cursor.getString(columnIndex)));
                }
            }
            cursor.close();
        }
        return contactList;
    }

    // ✅ Update UI
    private void updateUI() {
        runOnUiThread(() -> {
            if (availableContacts.isEmpty()) {
                noUsersText.setVisibility(View.VISIBLE);
                contactsRecyclerView.setVisibility(View.GONE);
            } else {
                noUsersText.setVisibility(View.GONE);
                contactsRecyclerView.setVisibility(View.VISIBLE);
                contactsAdapter = new UserAdapter(availableContacts, this::onContactClicked);
                contactsRecyclerView.setAdapter(contactsAdapter);
            }
        });
    }

    // ✅ Handle Contact Click - Auto Start WebRTC
    private void onContactClicked(UserModel contact) {
        peerUsername = contact.getUsername();

        if (peerUsername == null || peerUsername.isEmpty()) {
            Log.e("WebRTC", "❌ ERROR: peerUsername is NULL or EMPTY!");
            return;
        }

        Log.d("WebRTC", "👤 Contact clicked: " + peerUsername);
        setupPeerConnection();
        createOffer();
    }


    // ✅ Setup WebRTC Peer Connection
    private void setupPeerConnection() {
        PeerConnectionFactory peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();
        PeerConnection.IceServer stunServer = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer();

        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(stunServer);
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);

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
                DataChannelHandler.getInstance().setDataChannel(dc);

                dc.registerObserver(new DataChannel.Observer() {
                    @Override
                    public void onBufferedAmountChange(long l) {}

                    @Override
                    public void onStateChange() {
                        Log.d("WebRTC", "⚡ DataChannel state changed: " + dc.state());

                        runOnUiThread(() -> {
                            if (dataChannelStatus != null) {  // ✅ Prevent NullPointerException
                                dataChannelStatus.setText(dc.state().toString());
                            } else {
                                Log.e("WebRTC", "❌ ERROR: dataChannelStatusText is NULL!");
                            }
                        });
                    }

                    @Override
                    public void onMessage(DataChannel.Buffer buffer) {}
                });
            }


            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {

            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d("WebRTC", "🔄 ICE connection state changed: " + iceConnectionState);

                if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                    Log.d("WebRTC", "✅ WebRTC connection successfully established!");

                    runOnUiThread(() -> {
                        Log.d("WebRTC", "📌 Navigating to ChatActivity with peer: " + peerUsername);

                        if (peerUsername == null || peerUsername.isEmpty()) {
                            Log.e("WebRTC", "❌ Error: peerUsername is NULL or EMPTY! Cannot open ChatActivity.");
                            return;
                        }

                        Intent intent = new Intent(ConnectActivity.this, ChatActivity.class);
                        intent.putExtra("peerUsername", peerUsername);
                        startActivity(intent);
                        finish();
                    });
                } else if (iceConnectionState == PeerConnection.IceConnectionState.FAILED) {
                    Log.e("WebRTC", "❌ WebRTC connection failed! Retrying...");
                }
            }


            @Override
            public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState newState) {
                PeerConnection.Observer.super.onStandardizedIceConnectionChange(newState);
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
            public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {
                PeerConnection.Observer.super.onSelectedCandidatePairChanged(event);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {}

            @Override
            public void onRemoveStream(MediaStream mediaStream) {}

            @Override
            public void onRenegotiationNeeded() {}

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}

            @Override
            public void onTrack(RtpTransceiver transceiver) {
                PeerConnection.Observer.super.onTrack(transceiver);
            }
        });

        dataChannel = peerConnection.createDataChannel("chat", new DataChannel.Init());
        DataChannelHandler.getInstance().setDataChannel(dataChannel);
    }

    private boolean hasSentOffer = false; // ✅ Prevent multiple offers

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
                hasSentOffer = true; // ✅ Mark offer as sent
            }
        }, new MediaConstraints());
    }



    private void retryIceConnection() {
        if (peerConnection != null) {
            Log.d("WebRTC", "Retrying ICE connection...");
            peerConnection.close();
            setupPeerConnection();
            createOffer(); // Restart WebRTC connection
        }
    }
    private List<IceCandidate> queuedIceCandidates = new ArrayList<>();

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

        peerConnection.setRemoteDescription(new CustomSdpObserver() {
            @Override
            public void onSetSuccess() {
                Log.d("WebRTC", "✅ Remote answer set successfully! Connection should now be established.");
            }
        }, new SessionDescription(SessionDescription.Type.ANSWER, sdp));
    }


    private void receiveIceCandidate(String data) {
        if (peerConnection == null) return;

        if (peerConnection.getRemoteDescription() == null) {
            Log.e("WebRTC", "❌ Remote description is null! Waiting to add ICE candidate.");
            return; // 🚨 Avoid adding ICE candidates before remote description is set
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





}
