package com.example.protegotinyever.act;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.cardview.widget.CardView;

import com.example.protegotinyever.R;
import com.google.android.material.textfield.TextInputLayout;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class SecuritySetupActivity extends AppCompatActivity {
    private TextView setupStatusText;
    private CardView biometricCard, pinCard;
    private Button enableBiometricButton;
    private EditText pinInput, confirmPinInput;
    private Button enablePinButton;
    private TextInputLayout pinInputLayout, confirmPinInputLayout;
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "SecurityPrefs";
    private static final String KEY_SECURITY_ENABLED = "security_enabled";
    private static final String KEY_SECURITY_TYPE = "security_type";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String TYPE_BIOMETRIC = "biometric";
    private static final String TYPE_PIN = "pin";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_setup);

        setupStatusText = findViewById(R.id.setupStatusText);
        biometricCard = findViewById(R.id.biometricCard);
        pinCard = findViewById(R.id.pinCard);
        enableBiometricButton = findViewById(R.id.enableBiometricButton);
        pinInput = findViewById(R.id.pinInput);
        confirmPinInput = findViewById(R.id.confirmPinInput);
        enablePinButton = findViewById(R.id.enablePinButton);
        pinInputLayout = findViewById(R.id.pinInputLayout);
        confirmPinInputLayout = findViewById(R.id.confirmPinInputLayout);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        setupBiometricOption();
        setupPinOption();
    }

    private void setupBiometricOption() {
        BiometricManager biometricManager = BiometricManager.from(this);
        boolean biometricAvailable = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS;

        if (!biometricAvailable) {
            biometricCard.setVisibility(View.GONE);
            pinCard.setVisibility(View.VISIBLE);
            setupStatusText.setText(R.string.pin_only_available);
            return;
        }

        enableBiometricButton.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_SECURITY_ENABLED, true);
            editor.putString(KEY_SECURITY_TYPE, TYPE_BIOMETRIC);
            editor.apply();
            
            Toast.makeText(this, R.string.biometric_enabled, Toast.LENGTH_SHORT).show();
            proceedToConnectActivity();
        });
    }

    private void setupPinOption() {
        enablePinButton.setOnClickListener(v -> {
            String pin = pinInput.getText().toString();
            String confirmPin = confirmPinInput.getText().toString();

            if (!validatePin(pin, confirmPin)) {
                return;
            }

            try {
                String hashedPin = hashPin(pin);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(KEY_SECURITY_ENABLED, true);
                editor.putString(KEY_SECURITY_TYPE, TYPE_PIN);
                editor.putString(KEY_PIN_HASH, hashedPin);
                editor.apply();

                Toast.makeText(this, R.string.pin_enabled, Toast.LENGTH_SHORT).show();
                proceedToConnectActivity();
            } catch (NoSuchAlgorithmException e) {
                Toast.makeText(this, R.string.pin_setup_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean validatePin(String pin, String confirmPin) {
        if (pin.length() != 4) {
            pinInputLayout.setError(getString(R.string.pin_length_error));
            return false;
        }

        if (!pin.matches("\\d{4}")) {
            pinInputLayout.setError(getString(R.string.pin_digits_only));
            return false;
        }

        if (!pin.equals(confirmPin)) {
            confirmPinInputLayout.setError(getString(R.string.pin_mismatch));
            return false;
        }

        pinInputLayout.setError(null);
        confirmPinInputLayout.setError(null);
        return true;
    }

    private String hashPin(String pin) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(pin.getBytes());
        return Base64.getEncoder().encodeToString(hash);
    }

    private void proceedToConnectActivity() {
        Intent intent = new Intent(this, ConnectActivity.class);
        intent.putExtra("username", getIntent().getStringExtra("username"));
        intent.putExtra("phoneNumber", getIntent().getStringExtra("phoneNumber"));
        startActivity(intent);
        finish();
    }
}