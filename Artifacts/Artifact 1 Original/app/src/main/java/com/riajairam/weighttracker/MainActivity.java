package com.riajairam.weighttracker;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.content.Intent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.activity.EdgeToEdge;


//This is the login activity screen
public class MainActivity extends AppCompatActivity {

    private EditText editUsername, editPassword;
    private Button btnLogin, btnRegister;
    private WeightTrackerDB db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    SessionManager session;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // New instance of DB class and session
        db = new WeightTrackerDB(this);
        session = new SessionManager(this);

        //Link UI elements
        editUsername = findViewById(R.id.editTextUsername);
        editPassword = findViewById(R.id.editTextPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnCreateAccount);

        //Check for register button and try to add to DB
        btnRegister.setOnClickListener(v -> {
            final String username = editUsername.getText().toString().trim();
            final String password = editPassword.getText().toString();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter both username and password.", Toast.LENGTH_SHORT).show();
                return;
            }

            //Simple check for short password
            if (password.length() < 8) {
                Toast.makeText(this, "Password must be at least 8 characters.", Toast.LENGTH_SHORT).show();
                return;
            }

            //Store username/pass in DB, Toast if exists already
            //User will be logged in automatically
            executor.execute(() -> {
                boolean created = db.registerUser(username, password);
                runOnUiThread(() -> {
                    if (created) {
                        Toast.makeText(this, "Account created! Logging in...", Toast.LENGTH_SHORT).show();

                        session.createLoginSession(username);

                        Intent intent = new Intent(MainActivity.this, GoalWeightActivity.class);
                        startActivity(intent);
                        finish();
                    }
                    else
                        Toast.makeText(this, "Username already exists.", Toast.LENGTH_SHORT).show();
                });
            });
        });

        //Regular login without account creation
        btnLogin.setOnClickListener(v -> {
            final String username = editUsername.getText().toString().trim();
            final String password = editPassword.getText().toString();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter your username and password.", Toast.LENGTH_SHORT).show();
                return;
            }

            //Check database and then login user
            executor.execute(() -> {
                boolean success = db.checkLogin(username, password);
                runOnUiThread(() -> {
                    if (success) {
                        SessionManager session = new SessionManager(this);
                        session.createLoginSession(username);

                        Toast.makeText(this, username + " logged in", Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(MainActivity.this, WeightTableActivity.class);
                        startActivity(intent);

                    } else {
                        Toast.makeText(this, "Invalid username or password.", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}