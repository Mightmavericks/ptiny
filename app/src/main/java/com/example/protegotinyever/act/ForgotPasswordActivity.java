package com.example.protegotinyever.act;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.protegotinyever.R;
import com.example.protegotinyever.util.AuthManager;
import com.google.android.material.textfield.TextInputLayout;

public class ForgotPasswordActivity extends AppCompatActivity {
    private TextInputLayout emailInputLayout;
    private EditText emailInput;
    private Button resetButton;
    private TextView backToLoginLink;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        authManager = AuthManager.getInstance(this);

        emailInputLayout = findViewById(R.id.emailInputLayout);
        emailInput = findViewById(R.id.emailInput);
        resetButton = findViewById(R.id.resetButton);
        backToLoginLink = findViewById(R.id.backToLoginLink);

        // Pre-fill email if provided
        String prefilledEmail = getIntent().getStringExtra("email");
        if (prefilledEmail != null && !prefilledEmail.isEmpty()) {
            emailInput.setText(prefilledEmail);
        }

        resetButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            if (validateEmail(email)) {
                resetButton.setEnabled(false);
                sendPasswordResetEmail(email);
            }
        });

        backToLoginLink.setOnClickListener(v -> {
            finish();
        });
    }

    private boolean validateEmail(String email) {
        if (email.isEmpty()) {
            emailInputLayout.setError("Email is required");
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.setError("Please enter a valid email");
            return false;
        }
        emailInputLayout.setError(null);
        return true;
    }

    private void sendPasswordResetEmail(String email) {
        authManager.sendPasswordResetEmail(email)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(this, R.string.reset_email_sent, Toast.LENGTH_LONG).show();
                // Return to login screen after successful send
                finish();
            })
            .addOnFailureListener(e -> {
                resetButton.setEnabled(true);
                Toast.makeText(this, R.string.reset_email_error, Toast.LENGTH_LONG).show();
            });
    }
} 