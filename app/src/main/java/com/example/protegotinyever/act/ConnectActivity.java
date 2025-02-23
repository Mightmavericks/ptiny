package com.example.protegotinyever.act;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;
import com.example.protegotinyever.R;
import com.example.protegotinyever.service.WebRTCService;
import com.example.protegotinyever.tt.UserAdapter;
import com.example.protegotinyever.tt.UserModel;
import com.example.protegotinyever.util.FirebaseClient;
import com.example.protegotinyever.webrtc.WebRTCClient;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import androidx.activity.OnBackPressedCallback;
import org.webrtc.DataChannel;
import java.util.ArrayList;
import java.util.List;
import com.example.protegotinyever.service.ConnectionManager;
import com.example.protegotinyever.util.SessionManager;

public class ConnectActivity extends AppCompatActivity {
    private WebRTCClient webRTCClient;
    private FirebaseClient firebaseClient;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private ConnectPagerAdapter pagerAdapter;
    private ConnectionManager connectionManager;
    private static final int CONTACTS_PERMISSION_CODE = 100;
    private static final int NOTIFICATION_PERMISSION_CODE = 101;
    private boolean hasCheckedPermissions = false;
    private int rea = 1;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);

        String username = getIntent().getStringExtra("username");
        String phoneNumber = getIntent().getStringExtra("phoneNumber");
        firebaseClient = new FirebaseClient(username, phoneNumber);
        webRTCClient = WebRTCClient.getInstance(this, firebaseClient);
        connectionManager = ConnectionManager.getInstance(this);

        setupViewPager();
        setupWebRTC();

        checkAndRequestPermissions();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });

        // Set user online and attempt reconnection on login with retry
        firebaseClient.saveUser(username, phoneNumber, true, () -> {
            Log.d("ConnectActivity", "User set online: " + username);
            reconnectToPreviousUsersWithRetry(3, 1000); // Retry 3 times, 1s delay
        });
    }

    private void setupViewPager() {
        pagerAdapter = new ConnectPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(position == 0 ? "REQUESTS" : "CHATS");
        }).attach();

        pagerAdapter.getRequestsFragment().setWebRTCClient(webRTCClient);
        pagerAdapter.getChatsFragment().setWebRTCClient(webRTCClient);

        UserAdapter.OnUserClickListener listener = new UserAdapter.OnUserClickListener() {
            @Override
            public void onConnectionButtonClick(UserModel user) {
                handleConnectionClick(user);
            }

            @Override
            public void onChatButtonClick(UserModel user) {
                navigateToChat(user.getUsername());
            }
        };

        pagerAdapter.getRequestsFragment().setUserClickListener(listener);
        pagerAdapter.getChatsFragment().setUserClickListener(listener);
    }

    private void setupWebRTC() {
        webRTCClient.setWebRTCListener(new WebRTCClient.WebRTCListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    fetchContactsAndCheckUsers();
                });
            }

            @Override
            public void onConnectionFailed() {
                runOnUiThread(() -> {
                    fetchContactsAndCheckUsers();
                });
            }

            @Override
            public void onMessageReceived(String message, String peerUsername) {}
        });
    }

    private void checkAndRequestPermissions() {
        if (hasCheckedPermissions) {
            return;
        }
        hasCheckedPermissions = true;

        boolean needsContactsPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED;

        boolean needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;

        if (needsContactsPermission && needsNotificationPermission) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS},
                    CONTACTS_PERMISSION_CODE);
        } else if (needsContactsPermission) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    CONTACTS_PERMISSION_CODE);
        } else if (needsNotificationPermission) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_CODE);
        } else {
            startWebRTCService();
            fetchContactsAndCheckUsers();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CONTACTS_PERMISSION_CODE) {
            boolean contactsGranted = false;
            boolean notificationsGranted = false;

            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.READ_CONTACTS)) {
                    contactsGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                } else if (permissions[i].equals(Manifest.permission.POST_NOTIFICATIONS)) {
                    notificationsGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                }
            }

            if (contactsGranted) {
                fetchContactsAndCheckUsers();
            } else {
                Toast.makeText(this, "Contacts permission is required to find your contacts", Toast.LENGTH_LONG).show();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (notificationsGranted) {
                    startWebRTCService();
                } else {
                    Toast.makeText(this, "Notifications permission is required for chat messages", Toast.LENGTH_LONG).show();
                }
            } else {
                startWebRTCService();
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startWebRTCService();
            } else {
                Toast.makeText(this, "Notifications permission is required for chat messages", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startWebRTCService() {
        try {
            Intent serviceIntent = new Intent(this, WebRTCService.class);
            serviceIntent.putExtra("username", getIntent().getStringExtra("username"));
            serviceIntent.putExtra("phoneNumber", getIntent().getStringExtra("phoneNumber"));
            startForegroundService(serviceIntent);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                fetchContactsAndCheckUsers();
            }, 1000);
        } catch (Exception e) {
            Log.e("ConnectActivity", "Failed to start WebRTC service", e);
            Toast.makeText(this, "Failed to start chat service", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchContactsAndCheckUsers() {
        if (firebaseClient == null) {
            return;
        }

        List<String> phoneContacts = getDeviceContacts();
        firebaseClient.getRegisteredUsers(users -> {
            runOnUiThread(() -> updateFragments(users));
        });
    }

    private void updateFragments(List<UserModel> users) {
        for (UserModel user : users) {
            if (user.isOnline() && connectionManager.isUserConnected(user.getUsername())) {
                DataChannel dataChannel = webRTCClient.getDataChannels().get(user.getUsername());
                if (dataChannel == null || dataChannel.state() != DataChannel.State.OPEN) {
                    Log.d("ConnectActivity", "Reconnecting to " + user.getUsername());
                    webRTCClient.startConnection(user.getUsername());
                }
            }
        }
        pagerAdapter.getRequestsFragment().updateUsers(users);
        pagerAdapter.getChatsFragment().updateUsers(users);
    }

    private List<String> getDeviceContacts() {
        List<String> contactList = new ArrayList<>();
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);

        if (cursor != null) {
            int columnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            while (cursor.moveToNext()) {
                String phone = cursor.getString(columnIndex);
                contactList.add(formatPhoneNumber(phone));
            }
            cursor.close();
        }
        return contactList;
    }

    private String formatPhoneNumber(String phone) {
        phone = phone.replaceAll("[^0-9]", "");
        return phone.length() > 10 ? phone.substring(phone.length() - 10) : phone;
    }

    private void handleConnectionClick(UserModel user) {
        DataChannel dataChannel = webRTCClient.getDataChannels().get(user.getUsername());

        if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
            webRTCClient.disconnectPeer(user.getUsername());
            Toast.makeText(this, "Disconnected from " + user.getUsername(), Toast.LENGTH_SHORT).show();
            connectionManager.removeConnectedUser(user.getUsername());
        } else if (!webRTCClient.isAttemptingConnection(user.getUsername())) {
            webRTCClient.startConnection(user.getUsername());
            Toast.makeText(this, "Connecting to " + user.getUsername(), Toast.LENGTH_SHORT).show();
            connectionManager.addConnectedUser(user.getUsername());
            // Ensure requester is marked online
            firebaseClient.saveUser(getIntent().getStringExtra("username"),
                    getIntent().getStringExtra("phoneNumber"), true, () -> {});
        }

        fetchContactsAndCheckUsers();
    }

    private void navigateToChat(String peerUsername) {
        DataChannel dataChannel = webRTCClient.getDataChannels().get(peerUsername);
        if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("peerUsername", peerUsername);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Please connect with peer first", Toast.LENGTH_SHORT).show();
            firebaseClient.getRegisteredUsers(users -> {
                for (UserModel user : users) {
                    if (user.getUsername().equals(peerUsername) && user.isOnline()) {
                        webRTCClient.startConnection(peerUsername);
                        Toast.makeText(this, "Attempting to reconnect to " + peerUsername, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webRTCClient != null) {
            webRTCClient.onForeground();
            fetchContactsAndCheckUsers();
            reconnectToPreviousUsersWithRetry(3, 1000); // Retry on resume
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webRTCClient != null) {
            webRTCClient.onBackground();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_connect, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            if (firebaseClient != null) {
                firebaseClient.saveUser(getIntent().getStringExtra("username"),
                        getIntent().getStringExtra("phoneNumber"),
                        false,
                        () -> {
                            stopService(new Intent(this, WebRTCService.class));
                            if (webRTCClient != null) {
                                webRTCClient.disconnect();
                            }
                            SessionManager.getInstance(this).clearSession();
                            Intent intent = new Intent(this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        });
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reconnectToPreviousUsersWithRetry(int retries, long delayMillis) {
        firebaseClient.getRegisteredUsers(users -> {
            runOnUiThread(() -> {
                boolean reconnected = false;
                for (UserModel user : users) {
                    if (user.isOnline() && connectionManager.isUserConnected(user.getUsername())) {
                        DataChannel dataChannel = webRTCClient.getDataChannels().get(user.getUsername());
                        if (dataChannel == null || dataChannel.state() != DataChannel.State.OPEN) {
                            Log.d("ConnectActivity", "Attempting to reconnect to " + user.getUsername());
                            webRTCClient.startConnection(user.getUsername());
                            reconnected = true;
                        }
                    }
                }
                updateFragments(users);
                if (!reconnected && retries > 0) {
                    Log.d("ConnectActivity", "No reconnections made, retrying in " + delayMillis + "ms, retries left: " + retries);
                    handler.postDelayed(() -> reconnectToPreviousUsersWithRetry(retries - 1, delayMillis), delayMillis);
                }
            });
        });
    }
}