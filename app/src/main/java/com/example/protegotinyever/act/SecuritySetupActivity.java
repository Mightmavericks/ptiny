package com.example.protegotinyever.act;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;

import com.example.protegotinyever.R;

public class SecuritySetupActivity extends AppCompatActivity {

    private TextView setupStatusText;
    private Button enableBiometricButton;
    private EditText pinInput;
    private Button enablePinButton;
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "SecurityPrefs";
    private static final String KEY_SECURITY_ENABLED = "security_enabled";
    private static final String KEY_PIN = "pin";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_setup);

        setupStatusText = findViewById(R.id.setupStatusText);
        enableBiometricButton = findViewById(R.id.enableBiometricButton);
        pinInput = findViewById(R.id.pinInput);
        enablePinButton = findViewById(R.id.enablePinButton);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        BiometricManager biometricManager = BiometricManager.from(this);
        boolean biometricAvailable = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS;
        enableBiometricButton.setEnabled(biometricAvailable);

        enableBiometricButton.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_SECURITY_ENABLED, true);
            editor.apply();
            Toast.makeText(this, "Biometric security enabled", Toast.LENGTH_SHORT).show();
            proceedToConnectActivity();
        });

        enablePinButton.setOnClickListener(v -> {
            String pin = pinInput.getText().toString();
            if (pin.length() == 4) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(KEY_SECURITY_ENABLED, true);
                editor.putString(KEY_PIN, pin); // In production, hash this PIN
                editor.apply();
                Toast.makeText(this, "PIN security enabled", Toast.LENGTH_SHORT).show();
                proceedToConnectActivity();
            } else {
                Toast.makeText(this, "Please enter a 4-digit PIN", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void proceedToConnectActivity() {
        Intent intent = new Intent(this, ConnectActivity.class);
        intent.putExtra("username", getIntent().getStringExtra("username")); // From Login
        intent.putExtra("phoneNumber", getIntent().getStringExtra("phoneNumber"));
        startActivity(intent);
        finish();
    }
}