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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.protegotinyever.R;
import com.example.protegotinyever.tt.UserAdapter;
import com.example.protegotinyever.tt.UserModel;
import com.example.protegotinyever.util.FirebaseClient;
import com.example.protegotinyever.webrtc.WebRTCClient;

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

        firebaseClient = new FirebaseClient(currentUser, currentUserPhone);
        webRTCClient = WebRTCClient.getInstance(this, firebaseClient);

        checkContactsPermission();
        setupWebRTC();
    }

    private void setupWebRTC() {
        webRTCClient.setWebRTCListener(new WebRTCClient.WebRTCListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> updateUI());
            }

            @Override
            public void onConnectionFailed() {
                Log.e("WebRTC", "❌ Connection Failed!");
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
        if (!webRTCClient.isConnected()) {
            webRTCClient.startConnection(user.getUsername());
        } else {
            navigateToChat(user.getUsername());
        }
    }

    private void navigateToChat(String peerUsername) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("peerUsername", peerUsername);
        startActivity(intent);
    }
}
