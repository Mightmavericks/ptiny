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
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {
    private EditText emailInput, passwordInput;
    private Button loginButton;
    private TextView signUpLink;
    private AuthManager authManager;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authManager = AuthManager.getInstance(this);
        sessionManager = SessionManager.getInstance(this);

        // Check if user is already logged in
        if (authManager.isLoggedIn()) {
            FirebaseUser user = authManager.getCurrentUser();
            if (user != null && sessionManager.isLoggedIn()) {
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        }

        setContentView(R.layout.activity_login);

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        signUpLink = findViewById(R.id.signUpLink);
        TextView forgotPasswordLink = findViewById(R.id.forgotPasswordLink);

        loginButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (validateInputs(email, password)) {
                loginButton.setEnabled(false);
                authManager.signIn(email, password)
                    .addOnSuccessListener(authResult -> {
                        FirebaseUser user = authResult.getUser();
                        if (user != null) {
                            String username = user.getDisplayName();
                            // For simplicity, we'll use email as phone number if not available
                            String phone = user.getPhoneNumber() != null ? 
                                         user.getPhoneNumber() : user.getEmail();
                            
                            sessionManager.saveLoginSession(username, phone);
                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                            startActivity(intent);
                            finish();
                        }
                    })
                    .addOnFailureListener(e -> {
                        loginButton.setEnabled(true);
                        Toast.makeText(LoginActivity.this, 
                            "Login failed: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    });
            }
        });

        signUpLink.setOnClickListener(v -> {
            startActivity(new Intent(this, SignUpActivity.class));
        });

        forgotPasswordLink.setOnClickListener(v -> {
            Intent intent = new Intent(this, ForgotPasswordActivity.class);
            intent.putExtra("email", emailInput.getText().toString().trim());
            startActivity(intent);
        });
    }

    private boolean validateInputs(String email, String password) {
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
        return true;
    }
}