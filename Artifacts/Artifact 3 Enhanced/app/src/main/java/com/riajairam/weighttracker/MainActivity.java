package com.riajairam.weighttracker;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.EdgeToEdge;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// Firestore
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    private EditText editEmail, editPassword;
    private Button btnLogin, btnCreateAccount;
    private TextView textForgotPassword;
    private TextView textResendVerification;

    private FirebaseAuth auth;
    private FirebaseFirestore fs;

    private FirebaseUser lastUnverifiedUser = null; // store for resend

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance();
        fs = FirebaseFirestore.getInstance();

        editEmail = findViewById(R.id.editTextEmail);
        editPassword = findViewById(R.id.editTextPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        textForgotPassword = findViewById(R.id.textForgotPassword);
        textResendVerification = findViewById(R.id.textResendVerification);

        btnCreateAccount.setOnClickListener(v -> createAccount());
        btnLogin.setOnClickListener(v -> login());
        textForgotPassword.setOnClickListener(v -> sendResetEmail());

        textResendVerification.setOnClickListener(v -> {
            FirebaseUser user = auth.getCurrentUser(); // always get fresh user
            if (user != null) {
                resendVerificationEmail(user);
            } else if (lastUnverifiedUser != null) {
                resendVerificationEmail(lastUnverifiedUser);
            } else {
                Toast.makeText(this, "Please log in again to resend verification.", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseUser user = auth.getCurrentUser();

        if (user != null) {
            if (!user.isEmailVerified()) {
                // No toast here. Just show resend link.
                showResendLink(user);
                return;
            }
            textResendVerification.setVisibility(View.GONE);
            routeAfterAuth(user);
        } else {
            textResendVerification.setVisibility(View.GONE);
            lastUnverifiedUser = null;
        }
    }

    // CREATE ACCOUNT
    private void createAccount() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString();

        if (!validateInputs(email, password)) return;

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (!task.isSuccessful()) {
                        Toast.makeText(this,
                                task.getException() != null ? task.getException().getMessage() : "Signup failed",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        // Send verification and show one toast (inside resendVerificationEmail).
                        // Do not call showResendLink() here; resendVerificationEmail does.
                        resendVerificationEmail(user);
                    }
                });
    }

    // LOGIN
    private void login() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString();

        if (!validateInputs(email, password)) return;

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (!task.isSuccessful()) {
                        Toast.makeText(this,
                                task.getException() != null ? task.getException().getMessage() : "Login failed.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) return;

                    if (!user.isEmailVerified()) {
                        showResendLink(user);
                        return;
                    }

                    textResendVerification.setVisibility(View.GONE);
                    lastUnverifiedUser = null;

                    Toast.makeText(this, "Logged in!", Toast.LENGTH_SHORT).show();
                    routeAfterAuth(user);
                });
    }

    // RESEND LINK UI ONLY
    private void showResendLink(FirebaseUser user) {
        lastUnverifiedUser = user;
        textResendVerification.setVisibility(View.VISIBLE);
    }

    // Sends verification email and shows a toast.
    // Also signs out to force verify-before-use.
    private void resendVerificationEmail(FirebaseUser user) {
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                "Verification email sent! Check your inbox (and spam).",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this,
                                task.getException() != null ? task.getException().getMessage() : "Failed to send email.",
                                Toast.LENGTH_LONG).show();
                    }

                    // Show resend link (UI only)
                    showResendLink(user);

                    // Sign out to force verify-before-use
                    auth.signOut();
                    editPassword.setText("");
                });
    }

    // ROUTING (Firestore)
    private void routeAfterAuth(FirebaseUser user) {
        String uid = user.getUid();

        fs.collection("users")
                .document(uid)
                .collection("goal")
                .document("main")
                .get()
                .addOnSuccessListener(snapshot -> {
                    boolean hasGoal = snapshot.exists()
                            && snapshot.getLong("goalWeight") != null
                            && snapshot.getString("goalType") != null;

                    Intent intent = new Intent(
                            this,
                            hasGoal ? WeightTableActivity.class : GoalWeightActivity.class
                    );

                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Could not check goal (Firestore): " + e.getMessage(),
                            Toast.LENGTH_LONG).show();

                    // Safe fallback: send to Goal screen
                    Intent intent = new Intent(this, GoalWeightActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }

    // RESET PASSWORD
    private void sendResetEmail() {
        String email = editEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Enter your email first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Invalid email.", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task ->
                        Toast.makeText(this,
                                task.isSuccessful() ? "Reset email sent!" : "Failed to send reset email.",
                                Toast.LENGTH_LONG).show());
    }

    // VALIDATION
    private boolean validateInputs(String email, String password) {
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Enter both email and password.", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Invalid email.", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (password.length() < 8) {
            Toast.makeText(this, "Password must be at least 8 characters.", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }
}
