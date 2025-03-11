package com.example.protegotinyever.act;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.cardview.widget.CardView;

import com.example.protegotinyever.R;
import com.example.protegotinyever.util.AuthManager;
import com.example.protegotinyever.util.SessionManager;
import com.google.android.material.textfield.TextInputLayout;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    private TextView statusText;
    private ImageView securityIcon;
    private CardView securityCard;
    private Button authenticateButton;
    private TextInputLayout pinInputLayout;
    private SharedPreferences prefs;
    private SessionManager sessionManager;
    private AuthManager authManager;
    private static final String PREFS_NAME = "SecurityPrefs";
    private static final String KEY_SECURITY_ENABLED = "security_enabled";
    private static final String KEY_SECURITY_TYPE = "security_type";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String TYPE_BIOMETRIC = "biometric";
    private static final String TYPE_PIN = "pin";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        securityIcon = findViewById(R.id.securityIcon);
        securityCard = findViewById(R.id.securityCard);
        authenticateButton = findViewById(R.id.authenticateButton);
        pinInputLayout = findViewById(R.id.pinInputLayout);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sessionManager = SessionManager.getInstance(this);
        authManager = AuthManager.getInstance(this);

        if (!authManager.isLoggedIn() || !sessionManager.isLoggedIn()) {
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

        String securityType = prefs.getString(KEY_SECURITY_TYPE, TYPE_PIN);
        if (TYPE_BIOMETRIC.equals(securityType)) {
            setupBiometricAuth();
        } else {
            setupPinAuth();
        }
    }

    private void setupBiometricAuth() {
        BiometricManager biometricManager = BiometricManager.from(this);
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) 
            != BiometricManager.BIOMETRIC_SUCCESS) {
            // Fallback to PIN if biometric becomes unavailable
            setupPinAuth();
            return;
        }

        pinInputLayout.setVisibility(View.GONE);
        securityIcon.setImageResource(R.drawable.ic_fingerprint);
        statusText.setText(R.string.use_fingerprint);
        authenticateButton.setText(R.string.authenticate_biometric);

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, 
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    securityIcon.setImageResource(R.drawable.ic_check_circle);
                    statusText.setText(R.string.auth_successful);
                    proceedToConnectActivity();
                }

                @Override
                public void onAuthenticationError(int errorCode, 
                    @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    statusText.setText(getString(R.string.auth_error, errString));
                    if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS) {
                        setupPinAuth();
                    }
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    securityIcon.setImageResource(R.drawable.ic_error);
                    statusText.setText(R.string.auth_failed);
                }
            });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_app))
            .setSubtitle(getString(R.string.use_fingerprint_subtitle))
            .setNegativeButtonText(getString(R.string.use_pin))
            .build();

        authenticateButton.setOnClickListener(v -> 
            biometricPrompt.authenticate(promptInfo));
    }

    private void setupPinAuth() {
        pinInputLayout.setVisibility(View.VISIBLE);
        securityIcon.setImageResource(R.drawable.ic_pin);
        statusText.setText(R.string.enter_pin);
        authenticateButton.setText(R.string.authenticate_pin);

        authenticateButton.setOnClickListener(v -> {
            String inputPin = pinInputLayout.getEditText().getText().toString();
            String storedPinHash = prefs.getString(KEY_PIN_HASH, "");

            try {
                String inputPinHash = hashPin(inputPin);
                if (storedPinHash.equals(inputPinHash)) {
                    securityIcon.setImageResource(R.drawable.ic_check_circle);
                    statusText.setText(R.string.auth_successful);
                    proceedToConnectActivity();
                } else {
                    securityIcon.setImageResource(R.drawable.ic_error);
                    pinInputLayout.setError(getString(R.string.invalid_pin));
                    statusText.setText(R.string.auth_failed);
                }
            } catch (NoSuchAlgorithmException e) {
                Toast.makeText(this, R.string.auth_error_generic, 
                    Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String hashPin(String pin) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(pin.getBytes());
        return Base64.getEncoder().encodeToString(hash);
    }

    private void proceedToConnectActivity() {
        Toast.makeText(this, R.string.app_unlocked, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, ConnectActivity.class);
        intent.putExtra("username", sessionManager.getUsername());
        intent.putExtra("phoneNumber", sessionManager.getPhone());
        startActivity(intent);
        finish();
    }
}