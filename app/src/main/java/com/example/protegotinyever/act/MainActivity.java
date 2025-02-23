package com.example.protegotinyever.act;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.protegotinyever.R;
import com.example.protegotinyever.util.SessionManager;

import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private TextView statusText;
    private ImageView fingerprintIcon;
    private Button authenticateButton;
    private SharedPreferences prefs;
    private SessionManager sessionManager;
    private static final String PREFS_NAME = "SecurityPrefs";
    private static final String KEY_SECURITY_ENABLED = "security_enabled";
    private static final String KEY_PIN = "pin";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        fingerprintIcon = findViewById(R.id.fingerprintIcon);
        authenticateButton = findViewById(R.id.authenticateButton);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sessionManager = SessionManager.getInstance(this);

        if (!sessionManager.isLoggedIn()) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        boolean isSecurityEnabled = prefs.getBoolean(KEY_SECURITY_ENABLED, false);
        if (!isSecurityEnabled) {
            Intent intent = new Intent(this, SecuritySetupActivity.class);
            intent.putExtra("username", sessionManager.getUsername());
            intent.putExtra("phoneNumber", sessionManager.getPhone());
            startActivity(intent);
            finish();
            return;
        }

        authenticateUser();
    }

    private void authenticateUser() {
        BiometricManager biometricManager = BiometricManager.from(this);
        switch (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                statusText.setText("Use fingerprint to unlock Protegotinyever");
                promptBiometric();
                break;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                promptPin();
                break;
        }
    }

    private void promptBiometric() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                statusText.setText("Authentication Successful!");
                fingerprintIcon.setImageResource(android.R.drawable.checkbox_on_background);
                proceedToConnectActivity();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                statusText.setText("Error: " + errString);
                promptPin();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                statusText.setText("Authentication Failed. Try Again.");
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Protegotinyever")
                .setSubtitle("Use your fingerprint to authenticate")
                .setNegativeButtonText("Use PIN")
                .build();

        authenticateButton.setText("Authenticate Biometric");
        authenticateButton.setOnClickListener(v -> biometricPrompt.authenticate(promptInfo));
    }

    private void promptPin() {
        statusText.setText("Enter PIN to unlock Protegotinyever");
        authenticateButton.setText("Authenticate PIN");
        authenticateButton.setOnClickListener(v -> {
            String storedPin = prefs.getString(KEY_PIN, "");
            String inputPin = "1234"; // Placeholder: Replace with actual input in production
            if (storedPin.equals(inputPin)) {
                statusText.setText("PIN Authentication Successful!");
                proceedToConnectActivity();
            } else {
                Toast.makeText(this, "Invalid PIN", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void proceedToConnectActivity() {
        Toast.makeText(this, "Protegotinyever Unlocked!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, ConnectActivity.class);
        intent.putExtra("username", sessionManager.getUsername());
        intent.putExtra("phoneNumber", sessionManager.getPhone());
        startActivity(intent);
        finish();
    }
}