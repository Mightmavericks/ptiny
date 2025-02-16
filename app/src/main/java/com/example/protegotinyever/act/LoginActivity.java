package com.example.protegotinyever.act;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.protegotinyever.act.ConnectActivity;
import com.example.protegotinyever.util.FirebaseClient;
import com.example.protegotinyever.R;

public class LoginActivity extends AppCompatActivity {
    private EditText usernameInput, phoneNumberInput;
    private Button loginButton;
    private FirebaseClient firebaseClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        usernameInput = findViewById(R.id.usernameInput);
        phoneNumberInput = findViewById(R.id.phoneInput);
        loginButton = findViewById(R.id.loginButton);

        loginButton.setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();
            String phoneNumber = phoneNumberInput.getText().toString().trim();

            if (!username.isEmpty() && !phoneNumber.isEmpty()) {
                firebaseClient = new FirebaseClient(username,phoneNumber); // ✅ No need to pass username & phone here
                firebaseClient.saveUser(username, phoneNumber, true, () -> {
                    Toast.makeText(LoginActivity.this, "Logged in as " + username, Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(LoginActivity.this, ConnectActivity.class);
                    intent.putExtra("username", username);
                    intent.putExtra("phoneNumber", phoneNumber);
                    startActivity(intent);
                    finish();
                });
            } else {
                Toast.makeText(LoginActivity.this, "Enter both username and phone number!", Toast.LENGTH_SHORT).show();
            }

        });
    }
}
