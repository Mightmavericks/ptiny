package com.example.protegotinyever;

import android.util.Log;
import androidx.annotation.NonNull;
import com.example.protegotinyever.tt.DataModel;
import com.example.protegotinyever.tt.DataModelType;
import com.example.protegotinyever.util.SignalingCallback;
import com.example.protegotinyever.util.SuccessCallback;
import com.google.firebase.database.*;

public class FirebaseClient {
    private final DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
    private String currentUser;

    public FirebaseClient(String username) {
        this.currentUser = username;
    }

    // ✅ Save username to database
    public void saveUsername(SuccessCallback callback) {
        dbRef.child("users").child(currentUser).setValue(true)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess();
                    }
                });
    }

    // ✅ Send signaling data (Appends instead of overwriting)
    private String lastSentIceCandidate = ""; // Stores the last sent ICE candidate

    public void sendSignalingData(String peerUsername, String type, String data) {
        DataModel message = new DataModel(type, currentUser, peerUsername, data);

        // ✅ Store in "signaling/{peerUsername}/data" instead of overwriting entire "signaling/{peerUsername}"
        dbRef.child("signaling").child(peerUsername).child("data").setValue(message)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("FirebaseClient", "✅ Signaling data sent: " + type);
                    } else {
                        Log.e("FirebaseClient", "❌ Failed to send signaling data.");
                    }
                });
    }



    // ✅ Listen for signaling messages and remove them after reading
    public void listenForSignaling(SignalingCallback callback) {
        dbRef.child("signaling").child(currentUser).child("data")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            try {
                                DataModel message = snapshot.getValue(DataModel.class);
                                if (message != null) {
                                    Log.d("FirebaseClient", "✅ Received signaling message: " + message.getType() + " - " + message.getData());
                                    callback.onSignalingReceived(message.getType(), message.getData());
                                } else {
                                    Log.e("FirebaseClient", "❌ DataModel is null!");
                                }
                            } catch (Exception e) {
                                Log.e("FirebaseClient", "❌ Error parsing DataModel: " + e.getMessage());
                            }
                        } else {
                            Log.e("FirebaseClient", "⚠️ Snapshot is empty! No signaling data.");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("FirebaseClient", "❌ Error reading signaling data: " + error.getMessage());
                    }
                });
    }

}
