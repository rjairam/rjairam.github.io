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

public class GoalWeightActivity extends AppCompatActivity {

    // Database instance
    private WeightTrackerDB db;

    // Firebase session
    private FirebaseAuth auth;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.goalweight);

        // Init Firebase + DB
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        db = new WeightTrackerDB(this);

        // Firebase session check
        if (currentUser == null || currentUser.getEmail() == null) {
            Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        // Use email as the username key going forward
        String username = currentUser.getEmail();

        // Link UI elements
        EditText editGoalWeight = findViewById(R.id.editTextGoalWeight);
        TextView textViewGoalUsername = findViewById(R.id.textViewGoalUsername);
        RadioButton radioLoss = findViewById(R.id.radioLoss);
        Button btnSaveGoal = findViewById(R.id.btnSaveGoal);
        Button btnCancel = findViewById(R.id.btnCancel);

        // Show the username/email
        textViewGoalUsername.setText(getString(R.string.user_label, username));

        // Cancel/go back
        btnCancel.setOnClickListener(v -> {
            Intent intent = new Intent(GoalWeightActivity.this, WeightTableActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        // Save goal
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

            boolean success = db.setGoalWeight(username, goalWeight, goalType);
            if (success) {
                Toast.makeText(this,
                        "Goal (" + goalType + ") saved for " + username + "!",
                        Toast.LENGTH_SHORT).show();

                // Reset goal-achievement flags for this email-keyed user
                String keyLoss = username + "_goal_shown_loss";
                String keyGain = username + "_goal_shown_gain";
                getSharedPreferences("GoalPrefs", MODE_PRIVATE)
                        .edit()
                        .remove(keyLoss)
                        .remove(keyGain)
                        .apply();

                Intent intent = new Intent(GoalWeightActivity.this, WeightTableActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Error saving goal weight.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
