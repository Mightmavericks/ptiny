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

    public FirebaseClient(String username, String currentUserPhone) {
        this.currentUser = username;
        this.currentUserPhone = currentUserPhone;
    }

    // ✅ Save user data (username + phoneNumber + isOnline)
    public void saveUser(String username, String phoneNumber, boolean isOnline, SuccessCallback callback) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("username", username);
        userMap.put("phoneNumber", phoneNumber);  // ✅ Ensure correct field name
        userMap.put("isOnline", isOnline);

        dbRef.child("users").child(username).setValue(userMap)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("Firebase", "✅ User saved: " + username + ", Phone: " + phoneNumber);
                        callback.onSuccess();
                    } else {
                        Log.e("Firebase", "❌ Failed to save user.");
                    }
                });
    }


    // ✅ Fetch all registered users
    public void getRegisteredUsers(UserListCallback callback) {
        dbRef.child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<UserModel> users = new ArrayList<>();
                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String username = userSnapshot.child("username").getValue(String.class);
                    String phone = userSnapshot.child("phoneNumber").getValue(String.class); // ✅ Correct key

                    if (username != null && phone != null) {
                        users.add(new UserModel(username, phone));
                        Log.d("Firebase", "✅ Fetched User: " + username + ", Phone: " + phone); // ✅ Log to verify
                    } else {
                        Log.e("Firebase", "⚠️ User data missing phone or username!");
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










    // ✅ Send signaling data
    public void sendSignalingData(String peerUsername, String type, String data) {
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

}
