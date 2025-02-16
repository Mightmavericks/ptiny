package com.example.protegotinyever.act;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.protegotinyever.act.ChatActivity;
import com.example.protegotinyever.tt.UserAdapter;
import com.example.protegotinyever.tt.UserModel;
import com.example.protegotinyever.util.DataChannelHandler;
import com.example.protegotinyever.util.FirebaseClient;
import com.example.protegotinyever.tt.DataModelType;
import com.example.protegotinyever.util.CustomSdpObserver;
import com.example.protegotinyever.R;
import com.example.protegotinyever.util.UserListCallback;

import org.webrtc.*;

import java.util.ArrayList;
import java.util.List;

public class ConnectActivity extends AppCompatActivity {
    private FirebaseClient firebaseClient;
    private PeerConnection peerConnection;
    private DataChannel dataChannel;
    private RecyclerView contactsRecyclerView;
    private TextView noUsersText;
    private List<UserModel> availableContacts = new ArrayList<>();
    private UserAdapter contactsAdapter;
    private String peerUsername;
    private static final int CONTACTS_PERMISSION_CODE = 100;
    private String currentUser;
    private String phno="";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        contactsRecyclerView = findViewById(R.id.contactsRecyclerView);
        noUsersText = findViewById(R.id.noUsersText);
        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        currentUser = getIntent().getStringExtra("username");
        firebaseClient = new FirebaseClient(currentUser,getIntent().getStringExtra("phoneNumber"));
        checkContactsPermission();
        fetchContactsAndCheckUsers();
        Log.d("Match", "Total Matched Contacts: " + availableContacts.size());



        firebaseClient.listenForSignaling((type, data) -> {
            switch (type) {
                case DataModelType.OFFER:
                    Log.d("WebRTC", "📩 Received Offer!");
                    setupPeerConnection();
                    receiveOffer(data);
                    break;
                case DataModelType.ANSWER:
                    Log.d("WebRTC", "📩 Received Answer!");
                    receiveAnswer(data);
                    break;
                case DataModelType.ICE:
                    Log.d("WebRTC", "📩 Received ICE Candidate!");
                    receiveIceCandidate(data);
                    break;
            }
        });
    }

    private void checkContactsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, CONTACTS_PERMISSION_CODE);
        } else {
            fetchContactsAndCheckUsers();
        }
    }

    private void fetchContactsAndCheckUsers() {
        List<String> phoneContacts = getDeviceContacts();
        Log.d("Match", "📌 Contacts List: " + phoneContacts);

        firebaseClient.getRegisteredUsers(users -> {
            availableContacts.clear();
            String currentUserPhone = firebaseClient.getCurrentUserPhone(); // ✅ Get current user's phone

            for (UserModel user : users) {
                String phone = user.getPhone();

                if (phone == null) {
                    Log.e("Match", "⚠️ Skipping user " + user.getUsername() + " due to null phone number!");
                    continue;
                }

                // ✅ Normalize Phone Numbers (Remove +91 or any country code)
                String formattedPhone = phone.replaceAll("[^0-9]", "");
                if (formattedPhone.length() > 10) {
                    formattedPhone = formattedPhone.substring(formattedPhone.length() - 10);
                }

                String formattedCurrentUserPhone = currentUserPhone.replaceAll("[^0-9]", "");
                if (formattedCurrentUserPhone.length() > 10) {
                    formattedCurrentUserPhone = formattedCurrentUserPhone.substring(formattedCurrentUserPhone.length() - 10);
                }

                Log.d("Match", "🔍 Checking Firebase User: " + user.getUsername() + ", Phone: " + formattedPhone);
                Log.d("Match", "🔍 Comparing With Current User Phone: " + formattedCurrentUserPhone);

                // ✅ Skip if the user is **the current user**
                if (formattedPhone.equals(formattedCurrentUserPhone)) {
                    Log.d("Match", "🚫 Skipping self: " + user.getUsername());
                    continue;
                }

                if (phoneContacts.contains(formattedPhone)) {
                    availableContacts.add(user);
                    Log.d("Match", "✅ Match Found: " + user.getUsername());
                }
            }

            Log.d("Match", "✅ Total Matched Contacts (excluding self): " + availableContacts.size());
            runOnUiThread(this::updateUI);
        });
    }










    private List<String> getDeviceContacts() {
        List<String> contactList = new ArrayList<>();
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);

        if (cursor != null) {
            int columnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            if (columnIndex != -1) {
                while (cursor.moveToNext()) {
                    String phoneNumber = cursor.getString(columnIndex);
                    phoneNumber = phoneNumber.replaceAll("[^0-9]", ""); // ✅ Remove spaces, dashes, and parentheses
                    if (phoneNumber.length() > 10) {
                        phoneNumber = phoneNumber.substring(phoneNumber.length() - 10); // ✅ Keep only last 10 digits
                    }
                    contactList.add(phoneNumber);
                }
            }
            cursor.close();
        }
        return contactList;
    }





    private void updateUI() {
        runOnUiThread(() -> {
            if (availableContacts.isEmpty()) {
                Log.e("Match", "❌ No contacts matched!");
                noUsersText.setVisibility(View.VISIBLE);
                contactsRecyclerView.setVisibility(View.GONE);
            } else {
                Log.d("Match", "✅ Updating UI with matched contacts!");
                noUsersText.setVisibility(View.GONE);
                contactsRecyclerView.setVisibility(View.VISIBLE);
                contactsAdapter = new UserAdapter(availableContacts, this::onContactClicked);
                contactsRecyclerView.setAdapter(contactsAdapter);
            }
        });
    }


    private void onContactClicked(UserModel contact) {
        peerUsername = contact.getUsername();
        setupPeerConnection();
        createOffer();
    }

    private void setupPeerConnection() {
        PeerConnectionFactory peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();
        PeerConnection.IceServer stunServer = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer();
        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(stunServer);
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                if (peerConnection == null) return;
                Log.d("WebRTC", "📤 Sending ICE Candidate!");
                firebaseClient.sendSignalingData(peerUsername, DataModelType.ICE,
                        iceCandidate.sdp + "," + iceCandidate.sdpMid + "," + iceCandidate.sdpMLineIndex);
            }

            @Override
            public void onDataChannel(DataChannel dc) {
                Log.d("WebRTC", "✅ DataChannel established!");
                dataChannel = dc;
                DataChannelHandler.getInstance().setDataChannel(dc);
            }

            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {}

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                    Log.d("WebRTC", "✅ WebRTC Connection Established!");
                    runOnUiThread(() -> {
                        Intent intent = new Intent(ConnectActivity.this, ChatActivity.class);
                        intent.putExtra("peerUsername", peerUsername);
                        startActivity(intent);
                        finish();
                    });
                }
            }

            @Override
            public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState newState) {
                PeerConnection.Observer.super.onStandardizedIceConnectionChange(newState);
            }

            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState newState) {}

            @Override
            public void onIceConnectionReceivingChange(boolean b) {

            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}

            @Override
            public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {}

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
        DataChannelHandler.getInstance().setDataChannel(dataChannel);
    }

    private void createOffer() {
        peerConnection.createOffer(new CustomSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new CustomSdpObserver(), sessionDescription);
                firebaseClient.sendSignalingData(peerUsername, DataModelType.OFFER, sessionDescription.description);
            }
        }, new MediaConstraints());
    }

    private void receiveOffer(String sdp) {
        peerConnection.setRemoteDescription(new CustomSdpObserver(), new SessionDescription(SessionDescription.Type.OFFER, sdp));
    }

    private void receiveAnswer(String sdp) {
        peerConnection.setRemoteDescription(new CustomSdpObserver(), new SessionDescription(SessionDescription.Type.ANSWER, sdp));
    }

    private void receiveIceCandidate(String data) {
        String[] parts = data.split(",");
        peerConnection.addIceCandidate(new IceCandidate(parts[1], Integer.parseInt(parts[2]), parts[0]));
    }
}
