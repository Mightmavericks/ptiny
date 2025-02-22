package com.example.protegotinyever.util;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.protegotinyever.inte.SignalingCallback;
import com.example.protegotinyever.inte.SuccessCallback;
import com.example.protegotinyever.mode.DataModel;
import com.example.protegotinyever.mode.UserListCallback;
import com.example.protegotinyever.tt.DataModelType;
import com.example.protegotinyever.tt.UserModel;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseClient {
    private final DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
    private final String currentUser;
    private final String currentUserPhone;
    private DatabaseReference connectedRef;
    private DatabaseReference userStatusRef;
    private int rea = 1;

    public FirebaseClient(String username, String currentUserPhone) {
        this.currentUser = username;
        this.currentUserPhone = currentUserPhone;
        setupOnlinePresence();
    }

    private void setupOnlinePresence() {
        // Get reference to Firebase's internal .info/connected node
        connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        userStatusRef = dbRef.child("users").child(currentUser).child("isOnline");

        connectedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean connected = Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                if (connected) {
                    Log.d("Firebase", "Connected to Firebase");
                    
                    // When this device disconnects, set isOnline to false
                    userStatusRef.onDisconnect().setValue(false);
                    
                    // Set isOnline to true for this device
                    userStatusRef.setValue(true);
                } else {
                    Log.d("Firebase", "Disconnected from Firebase");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Error getting connection state: " + error.getMessage());
            }
        });
    }

    // ✅ Save user data (username + phoneNumber + isOnline)
    public void saveUser(String username, String phoneNumber, boolean isOnline, SuccessCallback callback) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("username", username);
        userMap.put("phoneNumber", phoneNumber);
        userMap.put("isOnline", isOnline);

        dbRef.child("users").child(username)
            .setValue(userMap)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d("Firebase", "✅ User saved: " + username + ", Phone: " + phoneNumber);
                    setupOnlinePresence(); // Setup presence after saving user
                    callback.onSuccess();
                } else {
                    Log.e("Firebase", "❌ Failed to save user.");
                }
            });
    }

    // ✅ Fetch all registered users
    public void getRegisteredUsers(UserListCallback callback) {
        dbRef.child("users").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<UserModel> users = new ArrayList<>();
                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String username = userSnapshot.child("username").getValue(String.class);
                    String phone = userSnapshot.child("phoneNumber").getValue(String.class);
                    Boolean isOnline = userSnapshot.child("isOnline").getValue(Boolean.class);

                    if (username != null && phone != null) {
                        users.add(new UserModel(username, phone, isOnline != null ? isOnline : false));
                        Log.d("Firebase", "✅ Fetched User: " + username + ", Phone: " + phone + ", Online: " + isOnline);
                    }
                }
                callback.onUsersFetched(users);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "❌ Error fetching users: " + error.getMessage());
            }
        });
    }

    public String getCurrentUserPhone() {
        Log.d("FirebaseClient", "🔍 Returning Current User Phone: " + currentUserPhone);
        return this.currentUserPhone;
    }

    // ✅ Listen for signaling messages
    public void listenForSignaling(SignalingCallback callback) {
        dbRef.child("signaling").child(currentUser).child("data")
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                        if (snapshot.exists()) {
                            try {
                                DataModel message = snapshot.getValue(DataModel.class);
                                if (message != null) {
                                    Log.d("FirebaseClient", "✅ Received signaling message: " + message.getType() + " - " + message.getData());
                                    callback.onSignalingReceived(message.getType(), message.getData(), message.getSender()); // ✅ Include sender
                                    snapshot.getRef().removeValue();
                                } else {
                                    Log.e("FirebaseClient", "❌ DataModel is null!");
                                }
                            } catch (Exception e) {
                                Log.e("FirebaseClient", "❌ Error parsing DataModel: " + e.getMessage());
                            }
                        }
                    }

                    @Override
                    public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

                    @Override
                    public void onChildRemoved(@NonNull DataSnapshot snapshot) {}

                    @Override
                    public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("FirebaseClient", "❌ Error reading signaling data: " + error.getMessage());
                    }
                });
    }

    // ✅ Send signaling data
    public void sendSignalingData(String peerUsername, String type, String data) {
        if (peerUsername == null || peerUsername.isEmpty()) {
            Log.e("FirebaseClient", "❌ Peer username is null or empty! Cannot send signaling data.");
            return;
        }
        DataModel message = new DataModel(type, currentUser, peerUsername, data);

        dbRef.child("signaling").child(peerUsername).child("data")
                .push()  // ✅ Push instead of setValue to avoid overwriting
                .setValue(message)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("FirebaseClient", "✅ Signaling data sent: " + type);
                    } else {
                        Log.e("FirebaseClient", "❌ Failed to send signaling data.");
                    }
                });
    }

    public static class SignalingData {
        private String fromUsername;
        private DataModelType type;
        private String data;

        public SignalingData() {}

        public String getFromUsername() {
            return fromUsername;
        }

        public void setFromUsername(String fromUsername) {
            this.fromUsername = fromUsername;
        }

        public DataModelType getType() {
            return type;
        }

        public void setType(DataModelType type) {
            this.type = type;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }
}
