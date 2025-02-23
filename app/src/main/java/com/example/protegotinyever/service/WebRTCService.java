package com.example.protegotinyever.service;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.protegotinyever.R;
import com.example.protegotinyever.act.ChatActivity;
import com.example.protegotinyever.util.DataChannelHandler;
import com.example.protegotinyever.util.FirebaseClient;
import com.example.protegotinyever.webrtc.WebRTCClient;

import java.util.HashMap;
import java.util.Map;

public class WebRTCService extends Service {
    private static final String SERVICE_CHANNEL_ID = "WebRTCServiceChannel";
    private static final String MESSAGE_CHANNEL_ID = "WebRTCMessageChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "WebRTCService";
    private WebRTCClient webRTCClient;
    private FirebaseClient firebaseClient;
    private NotificationManager notificationManager;
    private int messageNotificationId = 100;
    private DataChannelHandler dataChannelHandler;
    private static String currentActivityName = null;
    private static String currentChatPeer = null;
    private static int activeActivities = 0;
    private static final Object activityLock = new Object();
    private static boolean isTransitioningActivities = false;
    private static final long TRANSITION_TIMEOUT = 1000; // 1 second timeout for transitions
    private static String lastStartedActivity = null;
    private static String lastPausedActivity = null;
    private Map<String, Integer> notificationIds;
    private int nextNotificationId = 2; // Start from 2 since 1 is used for foreground service
    private int rea = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "WebRTCService onCreate");
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationIds = new HashMap<>();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createForegroundNotification());
        dataChannelHandler = DataChannelHandler.getInstance(getApplicationContext());
        
        // Register activity lifecycle callbacks
        getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity activity) {
                synchronized (activityLock) {
                    String activityName = activity.getClass().getName();
                    lastStartedActivity = activityName;
                    
                    // Always increment counter on start
                    activeActivities++;
                    isTransitioningActivities = true;
                    
                    Log.d("WebRTC", "➡️ Activity started: " + activityName + 
                          " (Active activities: " + activeActivities + ")");
                    
                    // Schedule transition timeout
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        synchronized (activityLock) {
                            isTransitioningActivities = false;
                        }
                    }, TRANSITION_TIMEOUT);
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
                synchronized (activityLock) {
                    String activityName = activity.getClass().getName();
                    
                    // Only decrement if we're not transitioning between activities
                    if (!isTransitioningActivities) {
                        activeActivities = Math.max(0, activeActivities - 1);
                        
                        // Clear chat state only when truly going to background
                        if (activeActivities == 0) {
                            Log.d("WebRTC", "📱 App going to background");
                            currentActivityName = null;
                            currentChatPeer = null;
                        }
                        
                        // Clear chat peer if leaving ChatActivity
                        if (activity instanceof ChatActivity) {
                            Log.d("WebRTC", "💬 Leaving chat with: " + currentChatPeer);
                            currentChatPeer = null;
                        }
                    }
                    
                    Log.d("WebRTC", "⬅️ Activity stopped: " + activityName + 
                          " (Active activities: " + activeActivities + 
                          ", Transitioning: " + isTransitioningActivities + ")");
                }
            }

            @Override
            public void onActivityResumed(Activity activity) {
                synchronized (activityLock) {
                    String activityName = activity.getClass().getName();
                    Log.d("WebRTC", "🔄 Activity resumed: " + activityName);
                    currentActivityName = activityName;
                    isTransitioningActivities = false;
                    
                    if (activity instanceof ChatActivity) {
                        String peer = ((ChatActivity) activity).getPeerUsername();
                        if (peer != null) {
                            currentChatPeer = peer;
                            dataChannelHandler.setCurrentPeer(peer);
                            Log.d("WebRTC", "📱 Chat opened with peer: " + peer);
                        }
                    } else {
                        Log.d("WebRTC", "📱 Not in chat, clearing current peer: " + currentChatPeer);
                        currentChatPeer = null;
                        dataChannelHandler.setCurrentPeer(null);
                    }
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                synchronized (activityLock) {
                    String activityName = activity.getClass().getName();
                    lastPausedActivity = activityName;
                    
                    // Don't clear chat peer here - we'll handle it in onActivityStopped
                    Log.d("WebRTC", "⏸️ Activity paused: " + activityName);
                }
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle bundle) {}

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {}

            @Override
            public void onActivityDestroyed(Activity activity) {
                synchronized (activityLock) {
                    Log.d("WebRTC", "❌ Activity destroyed: " + activity.getClass().getName());
                    if (activity instanceof ChatActivity) {
                        Log.d("WebRTC", "💬 Chat activity destroyed, clearing chat peer");
                        currentChatPeer = null;
                    }
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "WebRTCService onStartCommand");
        
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case "ACCEPT_CONNECTION":
                        String peerUsername = intent.getStringExtra("peerUsername");
                        String offerSdp = intent.getStringExtra("offerSdp");
                        if (peerUsername != null && offerSdp != null) {
                            webRTCClient.acceptConnection(peerUsername, offerSdp);
                            // Cancel the notification
                            notificationManager.cancel(peerUsername.hashCode());
                        }
                        break;
                    case "REJECT_CONNECTION":
                        String rejectPeer = intent.getStringExtra("peerUsername");
                        if (rejectPeer != null) {
                            webRTCClient.rejectConnection(rejectPeer);
                            // Cancel the notification
                            notificationManager.cancel(rejectPeer.hashCode());
                        }
                        break;
                }
            }

            // Initialize clients if needed
            String username = intent.getStringExtra("username");
            String phoneNumber = intent.getStringExtra("phoneNumber");
            
            if (firebaseClient == null) {
                firebaseClient = new FirebaseClient(username, phoneNumber);
            }
            
            if (webRTCClient == null) {
                webRTCClient = WebRTCClient.getInstance(getApplicationContext(), firebaseClient);
                webRTCClient.setWebRTCService(this);
                setupMessageHandler();
            }
        }
        
        return START_STICKY;
    }

    private void setupMessageHandler() {
        // Set WebRTCClient in DataChannelHandler
        dataChannelHandler.setWebRTCClient(webRTCClient);
        
        dataChannelHandler.setOnMessageReceivedListener(message -> {
            String peerUsername = webRTCClient.getPeerUsername();
            
            // Log the current state
            Log.d("WebRTC", "📨 Message received:" +
                  "\n- From: " + peerUsername +
                  "\n- Message: " + message +
                  "\n- Current Activity: " + currentActivityName +
                  "\n- Current Chat Peer: " + currentChatPeer +
                  "\n- Active Activities: " + activeActivities +
                  "\n- Is Transitioning: " + isTransitioningActivities);
            
            // Delay notification check slightly to allow activity transitions to complete
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                synchronized (activityLock) {
                    // Check if we should show notification
                    boolean isBackground = activeActivities == 0;
                    boolean isInChatActivity = ChatActivity.class.getName().equals(currentActivityName);
                    boolean isChattingWithSender = peerUsername != null && peerUsername.equals(currentChatPeer);
                    
                    // Show notification if:
                    // 1. App is in background, OR
                    // 2. Not in chat activity, OR
                    // 3. In different chat than sender
                    boolean shouldShowNotification = isBackground || !isInChatActivity || !isChattingWithSender;
                    
                    Log.d("WebRTC", "📱 Notification check:" +
                          "\n- Is Background: " + isBackground +
                          "\n- In Chat Activity: " + isInChatActivity +
                          "\n- Chatting with sender: " + isChattingWithSender +
                          "\n- Should Show: " + shouldShowNotification +
                          "\n- Active Activities: " + activeActivities +
                          "\n- Is Transitioning: " + isTransitioningActivities +
                          "\n- Current Activity: " + currentActivityName +
                          "\n- Current Chat Peer: " + currentChatPeer);
                    
                    if (shouldShowNotification) {
                        Log.d("WebRTC", "🔔 Will show notification - Not actively chatting with: " + peerUsername);
                        handleMessageNotification(message, peerUsername != null ? peerUsername : "Unknown");
                    } else {
                        Log.d("WebRTC", "💬 Skipping notification - Currently chatting with: " + peerUsername);
                    }
                }
            }, 500); // Add a small delay to allow activity transitions
        });

        dataChannelHandler.setStateChangeListener(state -> {
            Log.d("WebRTC", "⚡ DataChannel state changed to: " + state);
            switch (state) {
                case OPEN:
                    updateServiceNotification("Secure Connection Active");
                    break;
                case CLOSED:
                case CLOSING:
                    updateServiceNotification("Connection Closed");
                    break;
                default:
                    break;
            }
        });
    }

    private boolean isChatActivityActive() {
        boolean isActive = currentActivityName != null && ChatActivity.class.getName().equals(currentActivityName);
        Log.d("WebRTC", "🔍 Chat activity active: " + isActive + " (currentActivityName: " + currentActivityName + ")");
        return isActive;
    }

    private boolean isCurrentChatWith(String peerUsername) {
        boolean isChatting = peerUsername != null && peerUsername.equals(currentChatPeer);
        Log.d("WebRTC", "🔍 Currently chatting with " + peerUsername + ": " + isChatting + " (currentChatPeer: " + currentChatPeer + ")");
        return isChatting;
    }

    private void showMessageNotification(String message, String fromPeer) {
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Message notifications");
            notificationManager.createNotificationChannel(channel);
        }
        
        // Create intent for opening chat
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("peerUsername", fromPeer);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New message from " + fromPeer)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent);
        
        // Show the notification
        notificationManager.notify(fromPeer.hashCode(), builder.build());
        Log.d("WebRTC", "🔔 Showed notification for message from: " + fromPeer);
    }

    private boolean isBackground() {
        synchronized (activityLock) {
            return activeActivities == 0 && !isTransitioningActivities;
        }
    }

    private void updateServiceNotification(String text) {
        startForeground(NOTIFICATION_ID, createNotification(text));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create service channel (low priority)
            NotificationChannel serviceChannel = new NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    "WebRTC Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Maintains secure connection");
            serviceChannel.setShowBadge(false);
            notificationManager.createNotificationChannel(serviceChannel);
            Log.d("WebRTC", "📢 Service notification channel created");

            // Create message channel (high priority)
            NotificationChannel messageChannel = new NotificationChannel(
                    MESSAGE_CHANNEL_ID,
                    "WebRTC Message Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            messageChannel.setDescription("Shows incoming messages");
            messageChannel.enableLights(true);
            messageChannel.enableVibration(true);
            messageChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            messageChannel.setShowBadge(true);
            notificationManager.createNotificationChannel(messageChannel);
            Log.d("WebRTC", "📢 Message notification channel created");
        }
    }

    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, ChatActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
                .setContentTitle("Secure Chat")
                .setContentText(text)
                .setSmallIcon(R.drawable.avatar)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "WebRTCService onDestroy");
        if (webRTCClient != null) {
            // Don't disconnect, just cleanup resources
            webRTCClient.onBackground();
        }
    }

    // Helper method to check if we're in chat with specific peer
    private boolean isInChatWith(String peerUsername) {
        boolean isInChatActivity = ChatActivity.class.getName().equals(currentActivityName);
        boolean isWithPeer = peerUsername != null && peerUsername.equals(currentChatPeer);
        return isInChatActivity && isWithPeer;
    }

    public void handleMessageNotification(String message, String fromPeer) {
        synchronized (activityLock) {
            // At this point, we know we should show a notification because:
            // 1. The message is from a peer we're not actively chatting with
            // 2. The DataChannelHandler has already filtered out messages from the active chat
            
            // Create chat intent
            Intent chatIntent = new Intent(this, ChatActivity.class);
            chatIntent.putExtra("peerUsername", fromPeer);
            chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this, fromPeer.hashCode(), chatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Build notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Message from " + fromPeer)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

            // Show notification
            notificationManager.notify(fromPeer.hashCode(), builder.build());
            Log.d(TAG, "Showing notification for message from " + fromPeer);
        }
    }

    private Notification createForegroundNotification() {
        Intent notificationIntent = new Intent(this, ChatActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("Chat Service Running")
            .setContentText("Connected to chat network")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build();
    }

    public void showConnectionRequestNotification(String fromPeer, String offerSdp) {
        // Create accept intent
        Intent acceptIntent = new Intent(this, WebRTCService.class);
        acceptIntent.setAction("ACCEPT_CONNECTION");
        acceptIntent.putExtra("peerUsername", fromPeer);
        acceptIntent.putExtra("offerSdp", offerSdp);
        
        PendingIntent acceptPendingIntent = PendingIntent.getService(
            this, 0, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Create reject intent
        Intent rejectIntent = new Intent(this, WebRTCService.class);
        rejectIntent.setAction("REJECT_CONNECTION");
        rejectIntent.putExtra("peerUsername", fromPeer);
        
        PendingIntent rejectPendingIntent = PendingIntent.getService(
            this, 1, rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Connection Request")
            .setContentText(fromPeer + " wants to connect")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_accept, "Accept", acceptPendingIntent)
            .addAction(R.drawable.ic_reject, "Reject", rejectPendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w("WebRTCService", "Notification permission not granted");
                return;
            }
        }

        notificationManager.notify(fromPeer.hashCode(), builder.build());
    }
} 