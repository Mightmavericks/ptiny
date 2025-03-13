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
import com.example.protegotinyever.util.SessionManager;

public class SignUpActivity extends AppCompatActivity {
    private EditText emailInput, passwordInput, usernameInput, phoneInput;
    private Button signUpButton;
    private TextView loginLink;
    private AuthManager authManager;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        authManager = AuthManager.getInstance(this);
        sessionManager = SessionManager.getInstance(this);

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        usernameInput = findViewById(R.id.usernameInput);
        phoneInput = findViewById(R.id.phoneInput);
        signUpButton = findViewById(R.id.signUpButton);
        loginLink = findViewById(R.id.loginLink);

        signUpButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            String username = usernameInput.getText().toString().trim();
            String phone = phoneInput.getText().toString().trim();

            if (validateInputs(email, password, username, phone)) {
                signUpButton.setEnabled(false);
                authManager.signUp(email, password, username, phone)
                    .addOnSuccessListener(authResult -> {
                        sessionManager.saveLoginSession(username, phone);
                        Intent intent = new Intent(SignUpActivity.this, EmailVerificationActivity.class);
                        intent.putExtra("username", username);
                        intent.putExtra("phoneNumber", phone);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        signUpButton.setEnabled(true);
                        Toast.makeText(SignUpActivity.this, 
                            "Registration failed: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    });
            }
        });

        loginLink.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private boolean validateInputs(String email, String password, String username, String phone) {
        if (email.isEmpty()) {
            emailInput.setError("Email is required");
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Please enter a valid email");
            return false;
        }
        if (password.isEmpty()) {
            passwordInput.setError("Password is required");
            return false;
        }
        if (password.length() < 6) {
            passwordInput.setError("Password must be at least 6 characters");
            return false;
        }
        if (username.isEmpty()) {
            usernameInput.setError("Username is required");
            return false;
        }
        if (phone.isEmpty()) {
            phoneInput.setError("Phone number is required");
            return false;
        }
        return true;
    }
} 