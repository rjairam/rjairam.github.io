package com.riajairam.weighttracker;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// Firestore
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class GoalWeightActivity extends AppCompatActivity {

    // Firebase session
    private FirebaseAuth auth;
    private FirebaseUser currentUser;

    // Firestore
    private FirebaseFirestore fs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.goalweight);

        // Init Firebase + Firestore
        auth = FirebaseAuth.getInstance();
        fs = FirebaseFirestore.getInstance();
        currentUser = auth.getCurrentUser();

        // Firebase session check
        if (currentUser == null || currentUser.getEmail() == null) {
            Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        // Use email for display only (Firestore keying should be UID)
        String username = currentUser.getEmail();
        String uid = currentUser.getUid();

        // Link UI elements
        EditText editGoalWeight = findViewById(R.id.editTextGoalWeight);
        TextView textViewGoalUsername = findViewById(R.id.textViewGoalUsername);
        RadioButton radioLoss = findViewById(R.id.radioLoss);
        RadioButton radioGain = findViewById(R.id.radioGain);
        Button btnSaveGoal = findViewById(R.id.btnSaveGoal);
        Button btnCancel = findViewById(R.id.btnCancel);

        // Show the username/email
        textViewGoalUsername.setText(getString(R.string.user_label, username));

        // (Optional) preload existing goal from Firestore
        fs.collection("users").document(uid).collection("goal").document("main")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) return;

                    Long gw = snapshot.getLong("goalWeight");
                    String gt = snapshot.getString("goalType");

                    if (gw != null) editGoalWeight.setText(String.valueOf(gw.intValue()));
                    if (gt != null) {
                        if ("loss".equalsIgnoreCase(gt)) {
                            radioLoss.setChecked(true);
                        } else {
                            radioGain.setChecked(true);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    // Non-fatal; user can still set goal
                });

        // Cancel/go back
        btnCancel.setOnClickListener(v -> {
            Intent intent = new Intent(GoalWeightActivity.this, WeightTableActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        // Save goal (Firestore)
        btnSaveGoal.setOnClickListener(v -> {
            String goalStr = editGoalWeight.getText().toString().trim();

            if (goalStr.isEmpty()) {
                Toast.makeText(this, "Please enter a goal weight.", Toast.LENGTH_SHORT).show();
                return;
            }

            int goalWeight;
            try {
                goalWeight = Integer.parseInt(goalStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid number.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Determine selected goal type
            String goalType = radioLoss.isChecked() ? "loss" : "gain";

            Map<String, Object> data = new HashMap<>();
            data.put("goalWeight", goalWeight);
            data.put("goalType", goalType);

            fs.collection("users").document(uid).collection("goal").document("main")
                    .set(data)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this,
                                "Goal (" + goalType + ") saved for " + username + "!",
                                Toast.LENGTH_SHORT).show();

                        // Reset goal-achievement flags for this UID (recommended)
                        String keyLoss = uid + "_goal_shown_loss";
                        String keyGain = uid + "_goal_shown_gain";
                        getSharedPreferences("GoalPrefs", MODE_PRIVATE)
                                .edit()
                                .remove(keyLoss)
                                .remove(keyGain)
                                .apply();

                        Intent intent = new Intent(GoalWeightActivity.this, WeightTableActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error saving goal weight: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });
    }
}
