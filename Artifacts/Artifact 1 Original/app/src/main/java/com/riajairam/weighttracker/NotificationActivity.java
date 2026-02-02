package com.riajairam.weighttracker;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


//Settings for SMS notifications
public class NotificationActivity extends Activity {

    private TextView textViewPermissionsEnabled;
    private EditText editTextPhone;
    private Button btnSaveNumber, btnNotificationBack;
    private Switch switchEnableSMS;
    private static final String PREFS_NAME = "WeightTrackerPrefs";
    private static final String KEY_SMS_ENABLED = "sms_enabled";
    private static final int SMS_PERMISSION_CODE = 100;

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notification);

        //Link UI elements
        editTextPhone = findViewById(R.id.editTextPhone);
        btnSaveNumber = findViewById(R.id.btnSaveNumber);
        btnNotificationBack = findViewById(R.id.btnNotificationBack);
        switchEnableSMS = findViewById(R.id.switchEnableSMS);

        //Prefs for storing phone number
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);


        boolean isEnabled = prefs.getBoolean(KEY_SMS_ENABLED, false);
        switchEnableSMS.setChecked(isEnabled);

        //Initial state
        setSMSControlsEnabled(isEnabled);

        //Switch to enable SMS
        switchEnableSMS.setOnCheckedChangeListener((buttonView, isChecked) -> {

            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_SMS_ENABLED, isChecked);
            editor.apply();

            if (isChecked){
                checkSMSPermission();
            }
            // Enable or disable the fields
            setSMSControlsEnabled(isChecked);

        });

        // Load saved number from prefs (if it exists)
        String savedNumber = prefs.getString("smsNumber", "");
        if (!savedNumber.isEmpty()) {
            editTextPhone.setText(savedNumber);
        }

        // Save button
        btnSaveNumber.setOnClickListener(v -> {
            String enteredNumber = editTextPhone.getText().toString().trim();
            if (enteredNumber.isEmpty()) {
                Toast.makeText(this, "Please enter a valid phone number", Toast.LENGTH_SHORT).show();
            }
            else{
                prefs.edit().putString("smsNumber", enteredNumber).apply();

                if (enteredNumber.equals(prefs.getString("smsNumber", ""))) {
                    Toast.makeText(this, "Phone number " +prefs.getString("smsNumber", "") + " saved for SMS notifications", Toast.LENGTH_SHORT).show();
                    finish();
                }
                else{
                    Toast.makeText(this, "ERROR: Failed to save phone number.", Toast.LENGTH_SHORT).show();
                }
            }
        });

//Notification back button to go to Weight table
        btnNotificationBack.setOnClickListener(v -> {
            finish();
        });
    }

    //This will dim the UI elements if SMS is disabled
    private void setSMSControlsEnabled(boolean enabled) {
        editTextPhone.setEnabled(enabled);
        btnSaveNumber.setEnabled(enabled);

        // visually dim them when disabled
        float alpha = enabled ? 1.0f : 0.5f;
        editTextPhone.setAlpha(alpha);
        btnSaveNumber.setAlpha(alpha);
    }

    //Check for permission and ask user
    private void checkSMSPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            // If Permission not granted, request it
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.SEND_SMS},
                    SMS_PERMISSION_CODE
            );
        }
    }
}


