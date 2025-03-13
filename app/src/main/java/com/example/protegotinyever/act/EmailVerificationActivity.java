package com.example.protegotinyever.act;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.protegotinyever.R;
import com.example.protegotinyever.util.AuthManager;
import com.example.protegotinyever.util.SessionManager;
import com.google.firebase.auth.FirebaseUser;

public class EmailVerificationActivity extends AppCompatActivity {
    private TextView emailText;
    private Button resendButton;
    private Button verifyButton;
    private TextView changeEmailLink;
    private AuthManager authManager;
    private SessionManager sessionManager;
    private Handler handler = new Handler(Looper.getMainLooper());
    private static final int VERIFICATION_CHECK_INTERVAL = 3000; // 3 seconds
    private boolean isCheckingVerification = false;
    private Runnable verificationChecker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_verification);

        authManager = AuthManager.getInstance(this);
        sessionManager = SessionManager.getInstance(this);

        emailText = findViewById(R.id.emailText);
        resendButton = findViewById(R.id.resendButton);
        verifyButton = findViewById(R.id.verifyButton);
        changeEmailLink = findViewById(R.id.changeEmailLink);

        FirebaseUser user = authManager.getCurrentUser();
        if (user != null) {
            emailText.setText(user.getEmail());
            sendVerificationEmail();
        } else {
            // If no user is signed in, go back to login
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setupClickListeners();
        startVerificationCheck();
    }

    private void setupClickListeners() {
        resendButton.setOnClickListener(v -> {
            resendButton.setEnabled(false);
            sendVerificationEmail();
            handler.postDelayed(() -> resendButton.setEnabled(true), 30000); // Enable after 30 seconds
        });

        verifyButton.setOnClickListener(v -> {
            checkEmailVerification();
        });

        changeEmailLink.setOnClickListener(v -> {
            // Go back to signup
            authManager.signOut();
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
        });
    }

    private void sendVerificationEmail() {
        FirebaseUser user = authManager.getCurrentUser();
        if (user != null) {
            user.sendEmailVerification()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, R.string.email_sent, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        }
    }

    private void startVerificationCheck() {
        verificationChecker = new Runnable() {
            @Override
            public void run() {
                if (!isCheckingVerification) {
                    return;
                }
                checkEmailVerification();
                handler.postDelayed(this, VERIFICATION_CHECK_INTERVAL);
            }
        };
        isCheckingVerification = true;
        handler.post(verificationChecker);
    }

    private void checkEmailVerification() {
        FirebaseUser user = authManager.getCurrentUser();
        if (user != null) {
            user.reload().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    if (user.isEmailVerified()) {
                        isCheckingVerification = false;
                        Toast.makeText(this, R.string.email_verified, Toast.LENGTH_SHORT).show();
                        proceedToSecuritySetup();
                    }
                }
            });
        }
    }

    private void proceedToSecuritySetup() {
        Intent intent = new Intent(this, SecuritySetupActivity.class);
        intent.putExtra("username", getIntent().getStringExtra("username"));
        intent.putExtra("phoneNumber", getIntent().getStringExtra("phoneNumber"));
        startActivity(intent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isCheckingVerification = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isCheckingVerification) {
            startVerificationCheck();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isCheckingVerification = false;
        if (handler != null) {
            handler.removeCallbacks(verificationChecker);
        }
    }
} 