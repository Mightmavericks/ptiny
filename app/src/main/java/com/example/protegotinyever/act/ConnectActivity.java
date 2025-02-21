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
import com.example.protegotinyever.R;
import com.example.protegotinyever.service.WebRTCService;
import com.example.protegotinyever.tt.UserAdapter;
import com.example.protegotinyever.tt.UserModel;
import com.example.protegotinyever.util.FirebaseClient;
import com.example.protegotinyever.webrtc.WebRTCClient;
import androidx.activity.OnBackPressedCallback;

import java.util.ArrayList;
import java.util.List;

public class ConnectActivity extends AppCompatActivity {
    private FirebaseClient firebaseClient;
    private WebRTCClient webRTCClient;
    private RecyclerView contactsRecyclerView;
    private TextView noUsersText;
    private List<UserModel> availableContacts = new ArrayList<>();
    private UserAdapter contactsAdapter;
    private static final int CONTACTS_PERMISSION_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        contactsRecyclerView = findViewById(R.id.contactsRecyclerView);
        noUsersText = findViewById(R.id.noUsersText);
        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        String currentUser = getIntent().getStringExtra("username");
        String currentUserPhone = getIntent().getStringExtra("phoneNumber");

        // Start WebRTC service to maintain connection
        Intent serviceIntent = new Intent(this, WebRTCService.class);
        serviceIntent.putExtra("username", currentUser);
        serviceIntent.putExtra("phoneNumber", currentUserPhone);
        startForegroundService(serviceIntent);

        // Initialize Firebase first
        firebaseClient = new FirebaseClient(currentUser, currentUserPhone);
        
        // Set initial scanning state before WebRTC setup
        TextView scanningText = findViewById(R.id.scanningText);
        
        // Get WebRTC instance but don't clean up existing connections
        webRTCClient = WebRTCClient.getInstance(this, firebaseClient);
        
        // Update UI based on existing connection state
        if (webRTCClient.isConnected()) {
            scanningText.setText("SECURE CONNECTION ACTIVE");
            scanningText.setTextColor(getColor(R.color.success_green));
        } else {
            scanningText.setText("SCANNING FOR SECURE PEERS");
            scanningText.setTextColor(getColor(R.color.accent));
        }

        // Setup back press handling
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // If connected, just minimize the app instead of disconnecting
                moveTaskToBack(true);
            }
        });

        checkContactsPermission();
        setupWebRTC();
    }

    private void setupWebRTC() {
        webRTCClient.setWebRTCListener(new WebRTCClient.WebRTCListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    TextView scanningText = findViewById(R.id.scanningText);
                    scanningText.setText("SECURE CONNECTION ACTIVE");
                    scanningText.setTextColor(getColor(R.color.success_green));
                    
                    // Update adapter to refresh status indicators
                    if (contactsAdapter != null) {
                        contactsAdapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onConnectionFailed() {
                runOnUiThread(() -> {
                    TextView scanningText = findViewById(R.id.scanningText);
                    // Only show CONNECTION FAILED if we were actually trying to connect
                    if (webRTCClient.getPeerUsername() != null && webRTCClient.isAttemptingConnection()) {
                        scanningText.setText("CONNECTION FAILED");
                        scanningText.setTextColor(getColor(R.color.error_red));
                        // Clean up the failed connection attempt
                        webRTCClient.disconnect();  // Use disconnect instead of cleanup
                    } else {
                        scanningText.setText("SCANNING FOR SECURE PEERS");
                        scanningText.setTextColor(getColor(R.color.accent));
                    }
                });
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CONTACTS_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchContactsAndCheckUsers();
        }
    }

    private void fetchContactsAndCheckUsers() {
        List<String> phoneContacts = getDeviceContacts();
        firebaseClient.getRegisteredUsers(users -> {
            availableContacts.clear();

            for (UserModel user : users) {
                String formattedPhone = formatPhoneNumber(user.getPhone());
                if (phoneContacts.contains(formattedPhone)) {
                    availableContacts.add(user);
                }
            }

            Log.d("Firebase", "✅ Matched Users: " + availableContacts.size());
            runOnUiThread(this::updateUI);
        });
    }

    private String formatPhoneNumber(String phone) {
        phone = phone.replaceAll("[^0-9]", "");
        return phone.length() > 10 ? phone.substring(phone.length() - 10) : phone;
    }

    private List<String> getDeviceContacts() {
        List<String> contactList = new ArrayList<>();
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);

        if (cursor != null) {
            int columnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            while (cursor.moveToNext()) {
                contactList.add(formatPhoneNumber(cursor.getString(columnIndex)));
            }
            cursor.close();
        }
        return contactList;
    }

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

    private void onContactClicked(UserModel user) {
        TextView scanningText = findViewById(R.id.scanningText);
        
        // If already connected to this user, navigate to chat
        if (webRTCClient.isConnected() && user.getUsername().equals(webRTCClient.getPeerUsername())) {
            navigateToChat(user.getUsername());
            return;
        }
        
        // If connected to different user, show toast
        if (webRTCClient.isConnected()) {
            Toast.makeText(this, "Already connected to " + webRTCClient.getPeerUsername(), Toast.LENGTH_SHORT).show();
            return;
        }

        // Only try to establish connection if not already connected
        if (!webRTCClient.isConnected() && !webRTCClient.isAttemptingConnection()) {
            // Show connecting state in UI
            scanningText.setText("ESTABLISHING SECURE CONNECTION...");
            scanningText.setTextColor(getColor(R.color.warning_yellow));
            webRTCClient.startConnection(user.getUsername());
        }
    }

    private void navigateToChat(String peerUsername) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("peerUsername", peerUsername);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Notify WebRTCClient that we're in foreground
        if (webRTCClient != null) {
            webRTCClient.onForeground();
        }
        
        // Update UI based on connection state
        TextView scanningText = findViewById(R.id.scanningText);
        if (webRTCClient != null && webRTCClient.isConnected()) {
            scanningText.setText("SECURE CONNECTION ACTIVE");
            scanningText.setTextColor(getColor(R.color.success_green));
            
            // Also update the contact list to show connected state
            if (contactsAdapter != null) {
                contactsAdapter.notifyDataSetChanged();
            }
        } else {
            // Only refresh contacts if not connected
            fetchContactsAndCheckUsers();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Notify WebRTCClient that we're going to background
        if (webRTCClient != null) {
            webRTCClient.onBackground();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't disconnect or cleanup here - let the service handle it
    }
}
