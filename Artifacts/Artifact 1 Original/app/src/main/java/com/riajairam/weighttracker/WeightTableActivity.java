package com.riajairam.weighttracker;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class WeightTableActivity extends AppCompatActivity {

    private WeightTrackerDB db;
    private SessionManager session;
    private TableLayout tableWeight;
    private EditText editTextEnterDate, setNewWeight;
    private Button btnWTLogout, btnSettings, btnAddWeight;
    private ImageButton btnGoalWeightEdit;
    private static final String PREFS_NAME = "WeightTrackerPrefs";
    private static final String KEY_SMS_ENABLED = "sms_enabled";



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.weighttable);

        //new DB and session instances
        db = new WeightTrackerDB(this);
        session = new SessionManager(this);

        //UI elements
        tableWeight = findViewById(R.id.tableWeight);
        editTextEnterDate = findViewById(R.id.editTextEnterDate);
        setNewWeight = findViewById(R.id.setNewWeight);
        btnAddWeight = findViewById(R.id.btnAddWeight);
        btnWTLogout = findViewById(R.id.btnWTLogout);
        btnGoalWeightEdit = findViewById(R.id.btnGoalWeightEdit);
        btnSettings = findViewById(R.id.btnSettings);

        // Set today's date by default to the add date field
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());
        editTextEnterDate.setText(today);



        //Get the logged-in username and load the weights
        String username = session.getUsername();
        loadWeights(username);

        //Check if goal was achieved first
        checkGoalAchievement(username);

        //Load the goal weight from the database
        loadGoalWeight(username);


        // Date picker
        editTextEnterDate.setFocusable(false);
        editTextEnterDate.setOnClickListener(v -> showDatePicker(editTextEnterDate));

        btnSettings.setOnClickListener( v -> {
            Intent intent = new Intent(WeightTableActivity.this, NotificationActivity.class);
            startActivity(intent);
        });

        //Logout button - log out and display Toast message
        btnWTLogout.setOnClickListener( v -> {

            session.logout();
            Toast.makeText(this, username + " logged out", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(WeightTableActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            finish();


        });

        //Edit goal weight (takes you to different screen)
        btnGoalWeightEdit.setOnClickListener( v -> {
            Intent intent = new Intent(WeightTableActivity.this, GoalWeightActivity.class);
            startActivity(intent);
        });

        // Add weight
        btnAddWeight.setOnClickListener(v -> {
            String dateStr = editTextEnterDate.getText().toString().trim();
            String weightStr = setNewWeight.getText().toString().trim();

            if (dateStr.isEmpty() || weightStr.isEmpty()) {
                Toast.makeText(this, "Please enter both date and weight.", Toast.LENGTH_SHORT).show();
                return;
            }

            int weight;
            try {
                weight = Integer.parseInt(weightStr);
            } catch (NumberFormatException e) { //Error checking
                Toast.makeText(this, "Invalid weight format.", Toast.LENGTH_SHORT).show();
                return;
            }

            //Toast messages and reset fields when weight added
            boolean added = db.addWeight(username, dateStr, weight);
            if (added) {
                Toast.makeText(this, "Weight added.", Toast.LENGTH_SHORT).show();
                setNewWeight.setText("");
                editTextEnterDate.setText("");
                loadWeights(username);
                checkGoalAchievement(username); //Check goal

            } else {
                Toast.makeText(this, "Error adding weight.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        //Load goal weight if we resume the screen
        super.onResume();
        String username = session.getUsername();
        loadGoalWeight(username); // Refresh goal weight each time you return
    }


    private void loadGoalWeight(String username) {
        TextView textGoalWeight = findViewById(R.id.textViewGoalWeight);

        List<String[]> goalData = db.getGoalWeight(username);
        if (!goalData.isEmpty()) {
            String[] goal = goalData.get(0);
            int goalWeight = Integer.parseInt(goal[0]);
            String goalType = goal[1];
            textGoalWeight.setText("Goal Weight: " + goalWeight + " lbs (" + goalType + ")");
        } else {
            textGoalWeight.setText("Goal Weight: Not Set");
        }
    }

    // Show date picker dialog
    private void showDatePicker(EditText target) {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String formattedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            target.setText(formattedDate);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }


    // Load weights from DB
    private void loadWeights(String username) {
        tableWeight.removeViews(1, Math.max(0, tableWeight.getChildCount() - 1)); // clear old rows

        Cursor cursor = db.getWeights(username);
        if (cursor.moveToFirst()) {
            do {
                String date = cursor.getString(0);
                int weight = cursor.getInt(1);

                TableRow row = new TableRow(this);
                row.setPadding(10, 10, 10, 10);

                TextView textDate = new TextView(this);
                textDate.setText(date);
                textDate.setPadding(10, 10, 10, 10);

                TextView textWeight = new TextView(this);
                textWeight.setText(String.valueOf(weight));
                textWeight.setPadding(10, 10, 10, 10);

                Button btnEdit = new Button(this);
                btnEdit.setText("Edit");
                btnEdit.setTextColor(Color.WHITE);
                btnEdit.setBackgroundTintList(getColorStateList(android.R.color.holo_blue_light));
                btnEdit.setOnClickListener(v -> showEditDialog(username, date, weight));

                Button btnDelete = new Button(this);
                btnDelete.setText("Delete");
                btnDelete.setTextColor(Color.WHITE);
                btnDelete.setBackgroundTintList(getColorStateList(android.R.color.holo_red_dark));
                btnDelete.setOnClickListener(v -> {
                    boolean deleted = db.deleteWeight(username, date);
                    if (deleted) {
                        Toast.makeText(this, "Deleted " + date, Toast.LENGTH_SHORT).show();
                        loadWeights(username);
                    } else {
                        Toast.makeText(this, "Failed to delete.", Toast.LENGTH_SHORT).show();
                    }
                });

                row.addView(textDate);
                row.addView(textWeight);
                row.addView(btnEdit);
                row.addView(btnDelete);

                tableWeight.addView(row);
            } while (cursor.moveToNext());
        }
        cursor.close();
    }


    //Helper to send SMS if goal reached.
    private void sendGoalReachedSMS(String username, String sms_message) {

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String phoneNumber = prefs.getString("smsNumber", "");

        String message = "Hi " + username + ", "+ sms_message ;

        try {
            SmsManager smsManager = getSystemService(SmsManager.class);
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Toast.makeText(this, "SMS sent: " + phoneNumber, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "SMS failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

// Check if goal was met or exceeded within the last week
// Only shows once per achievement
    private void checkGoalAchievement(String username) {
        List<String[]> goalData = db.getGoalWeight(username);
        if (goalData.isEmpty()) return;

        String[] goal = goalData.get(0);
        int goalWeight = Integer.parseInt(goal[0]);
        String goalType = goal[1];

        Cursor cursor = db.getWeights(username);
        if (cursor == null || cursor.getCount() == 0) return;

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -7);
        long oneWeekAgoMillis = calendar.getTimeInMillis();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Integer recentWeight = null;
        Long recentDate = null;

        while (cursor.moveToNext()) {
            String dateStr = cursor.getString(0);
            int weight = cursor.getInt(1);
            try {
                long dateMillis = sdf.parse(dateStr).getTime();
                if (dateMillis >= oneWeekAgoMillis) {
                    if (recentDate == null || dateMillis > recentDate) {
                        recentDate = dateMillis;
                        recentWeight = weight;
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace(); //For debugging
            }
        }
        cursor.close();

        if (recentWeight == null) return;

        boolean goalMet = false;
        String message = "";
        String message_for_sms = ""; //Clean version for SMS.

        //Here we set messages for the goal weight achievement.
        //Separate message for SMS. We use emojis in the in-app message to make it fun for the user

        if (goalType.equalsIgnoreCase("loss") && recentWeight <= goalWeight) {
            goalMet = true;
            message = "🎉 Congratulations! You've reached your goal weight of " + goalWeight + " lbs.\nKeep up the great work!";
            message_for_sms = "Congratulations! You've reached your goal weight of " + goalWeight + " lbs. Keep up the great work!";
        } else if (goalType.equalsIgnoreCase("gain") && recentWeight >= goalWeight) {
            goalMet = true;
            message = "💪 Fantastic! You've achieved your weight gain goal of " + goalWeight + " lbs.\nKeep going strong!";
            message_for_sms = "Fantastic! You've achieved your weight gain goal of " + goalWeight + " lbs. Keep going strong!";

        }

        //IF the goal is achieved, send in-app message and SMS (if enabled)
        if (goalMet) {

            new AlertDialog.Builder(this)
                    .setTitle("Goal Achieved! 🏆")
                    .setMessage(message)
                    .setPositiveButton("Awesome!", (dialog, which) -> dialog.dismiss())
                    .show();


            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean SMSEnabled = prefs.getBoolean(KEY_SMS_ENABLED, false);

            if (SMSEnabled){
                sendGoalReachedSMS(username, message_for_sms);
            }
        }
    }

    // Edit dialog for weight and date
    private void showEditDialog(String username, String oldDate, int oldWeight) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Weight Entry");

        // Create layout for dialog
        TableLayout layout = new TableLayout(this);
        layout.setPadding(30, 30, 30, 10);

        EditText inputDate = new EditText(this);
        inputDate.setHint("Date (YYYY-MM-DD)");
        inputDate.setText(oldDate);
        inputDate.setFocusable(false);
        inputDate.setOnClickListener(v -> showDatePicker(inputDate));

        EditText inputWeight = new EditText(this);
        inputWeight.setHint("Weight");
        inputWeight.setText(String.valueOf(oldWeight));
        inputWeight.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        layout.addView(inputDate);
        layout.addView(inputWeight);
        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newDate = inputDate.getText().toString().trim();
            String newWeightStr = inputWeight.getText().toString().trim();

            if (newDate.isEmpty() || newWeightStr.isEmpty()) {
                Toast.makeText(this, "Please enter valid values.", Toast.LENGTH_SHORT).show();
                return;
            }

            int newWeight;
            try {
                newWeight = Integer.parseInt(newWeightStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid weight format.", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean updated = db.updateWeight(username, oldDate, newDate, newWeight);
            if (updated) {
                Toast.makeText(this, "Updated successfully!", Toast.LENGTH_SHORT).show();
                loadWeights(username);
                checkGoalAchievement(username);
            } else {
                Toast.makeText(this, "Update failed!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
