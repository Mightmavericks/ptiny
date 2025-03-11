package com.example.protegotinyever.util;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

public class AuthManager {
    private static AuthManager instance;
    private final FirebaseAuth auth;
    private final Context context;

    private AuthManager(Context context) {
        this.context = context.getApplicationContext();
        this.auth = FirebaseAuth.getInstance();
    }

    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthManager(context.getApplicationContext());
        }
        return instance;
    }

    public Task<AuthResult> signUp(String email, String password, String username, String phone) {
        return auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                .setDisplayName(username)
                                .build();
                        user.updateProfile(profileUpdates);
                        
                        // Save additional user info to Realtime Database
                        FirebaseClient firebaseClient = new FirebaseClient(username, phone);
                        firebaseClient.saveUser(username, phone, true, () -> {});
                    }
                });
    }

    public Task<AuthResult> signIn(String email, String password) {
        return auth.signInWithEmailAndPassword(email, password);
    }

    public void signOut() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            String username = user.getDisplayName();
            // Set user offline in Realtime Database
            FirebaseClient firebaseClient = new FirebaseClient(username, "");
            firebaseClient.saveUser(username, "", false, () -> {
                auth.signOut();
                SessionManager.getInstance(context).clearSession();
            });
        } else {
            auth.signOut();
            SessionManager.getInstance(context).clearSession();
        }
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }
} 