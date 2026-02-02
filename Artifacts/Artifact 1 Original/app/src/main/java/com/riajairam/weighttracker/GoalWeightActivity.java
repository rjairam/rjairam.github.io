package com.riajairam.weighttracker;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

public class GoalWeightActivity extends AppCompatActivity {

    //Database instance to store weights/goals/users
    private WeightTrackerDB db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.goalweight);


        //Session Manager to check logged in user
        SessionManager session;

        // Initialize database and session
        db = new WeightTrackerDB(this);
        session = new SessionManager(this);

        // Check if user is logged in
        if (!session.isLoggedIn()) {
            Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Get logged-in username
        String username = session.getUsername();

        // Link UI elements
        //EditText for the goal weight
       EditText editGoalWeight = findViewById(R.id.editTextGoalWeight);

       // Textview for username
       TextView textViewGoalUsername = findViewById(R.id.textViewGoalUsername);

       // Radio button for weight loss
       RadioButton radioLoss = findViewById(R.id.radioLoss);

       //Buttons for Saving the goal and canceling
       Button btnSaveGoal = findViewById(R.id.btnSaveGoal);
       Button btnCancel = findViewById(R.id.btnCancel);

        // Show the username
        textViewGoalUsername.setText(getString(R.string.user_label, username));



        //Go back button
        //Will go back and not have any screens below
        btnCancel.setOnClickListener(v -> {
            Intent intent = new Intent(GoalWeightActivity.this, WeightTableActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        // Save goal button click
        btnSaveGoal.setOnClickListener(v -> {
            String goalStr = editGoalWeight.getText().toString().trim();

            //Check if the goal is empty and then prompt user if it is empty
            if (goalStr.isEmpty()) {
                Toast.makeText(this, "Please enter a goal weight.", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int goalWeight = Integer.parseInt(goalStr);


                // Determine selected goal type
                String goalType = radioLoss.isChecked() ? "loss" : "gain";

                //If goal is successfully set, print a toast message
                boolean success = db.setGoalWeight(username, goalWeight, goalType);
                if (success) {
                    Toast.makeText(this, "Goal (" + goalType + ") saved for " + username + "!", Toast.LENGTH_SHORT).show();

                    // Reset the goal-achievement flag for this user
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
                    //Error handling
                    Toast.makeText(this, "Error saving goal weight.", Toast.LENGTH_SHORT).show();
                }

            }
           catch (NumberFormatException e) {
                //If user doesn't enter a valid number, toast message
                Toast.makeText(this, "Please enter a valid number.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
