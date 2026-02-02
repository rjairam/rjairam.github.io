package com.riajairam.weighttracker;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

//For firebase
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class WeightTableActivity extends AppCompatActivity {

    //Local SQLite DB. To be converted to Firebase
    private WeightTrackerDB db;

    //Firebase auth
    private FirebaseAuth auth;
    private FirebaseUser currentUser;

    private TableLayout tableWeight;
    private EditText editTextEnterDate, setNewWeight;
    private Button btnWTLogout, btnSettings, btnAddWeight;
    private ImageButton btnGoalWeightEdit;

    // Search/Filter UI
    private EditText editTextStartDate, editTextEndDate, editTextMinWeight, editTextMaxWeight;
    private Button btnApplyFilter, btnClearFilter;
    private TextView textViewFilterSummary;

    private static final String PREFS_NAME = "WeightTrackerPrefs";
    private static final String KEY_SMS_ENABLED = "sms_enabled";

    private String username; // Firebase email

    //This is for the sorting using the tap of the header. Sort is local not via db
    private TextView headerDate;
    private TextView headerWeight;

    private enum SortField { DATE, WEIGHT }
    private SortField currentSortField = SortField.DATE;
    private boolean sortAscending = false; // false = descending (newest/heaviest first)

    // In-memory row model
    private static class WeightEntry {
        final String dateStr;   // "yyyy-MM-dd"
        final long dateMillis;  // parsed once
        final int weight;

        WeightEntry(String dateStr, long dateMillis, int weight) {
            this.dateStr = dateStr;
            this.dateMillis = dateMillis;
            this.weight = weight;
        }
    }

    // Filter state (null means "no filter")
    private Long filterStartMillis = null;
    private Long filterEndMillis = null;
    private Integer filterMinWeight = null;
    private Integer filterMaxWeight = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.weighttable);

        db = new WeightTrackerDB(this);
        auth = FirebaseAuth.getInstance();

        // UI elements
        tableWeight = findViewById(R.id.tableWeight);
        editTextEnterDate = findViewById(R.id.editTextEnterDate);
        setNewWeight = findViewById(R.id.setNewWeight);
        btnAddWeight = findViewById(R.id.btnAddWeight);
        btnWTLogout = findViewById(R.id.btnWTLogout);
        btnGoalWeightEdit = findViewById(R.id.btnGoalWeightEdit);
        btnSettings = findViewById(R.id.btnSettings);

        // Header views
        headerDate = findViewById(R.id.headerDate);
        headerWeight = findViewById(R.id.headerWeight);
        wireUpHeaderTaps();

        // Filter UI
        editTextStartDate = findViewById(R.id.editTextStartDate);
        editTextEndDate = findViewById(R.id.editTextEndDate);
        editTextMinWeight = findViewById(R.id.editTextMinWeight);
        editTextMaxWeight = findViewById(R.id.editTextMaxWeight);
        btnApplyFilter = findViewById(R.id.btnApplyFilter);
        btnClearFilter = findViewById(R.id.btnClearFilter);
        textViewFilterSummary = findViewById(R.id.textViewFilterSummary);

        // Filter date pickers
        if (editTextStartDate != null) {
            editTextStartDate.setFocusable(false);
            editTextStartDate.setOnClickListener(v -> showDatePicker(editTextStartDate));
        }
        if (editTextEndDate != null) {
            editTextEndDate.setFocusable(false);
            editTextEndDate.setOnClickListener(v -> showDatePicker(editTextEndDate));
        }

        if (btnApplyFilter != null) {
            btnApplyFilter.setOnClickListener(v -> {
                if (!ensureLoggedInAndSetUsername()) return;
                if (!readAndValidateFilters()) return;
                updateFilterSummary();
                loadWeights(username);
            });
        }

        if (btnClearFilter != null) {
            btnClearFilter.setOnClickListener(v -> {
                clearFiltersUIAndState();
                updateFilterSummary();
                if (!ensureLoggedInAndSetUsername()) return;
                loadWeights(username);
            });
        }

        updateFilterSummary();

        // Set today's date by default
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new java.util.Date());
        editTextEnterDate.setText(today);

        // Date picker for "add weight"
        editTextEnterDate.setFocusable(false);
        editTextEnterDate.setOnClickListener(v -> showDatePicker(editTextEnterDate));

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(WeightTableActivity.this, NotificationActivity.class);
            startActivity(intent);
        });

        // Firebase logout
        btnWTLogout.setOnClickListener(v -> {
            FirebaseUser user = auth.getCurrentUser();
            String who = (user != null && user.getEmail() != null) ? user.getEmail() : "User";

            auth.signOut();

            Toast.makeText(this, who + " logged out", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(WeightTableActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // Edit goal weight
        btnGoalWeightEdit.setOnClickListener(v -> {
            Intent intent = new Intent(WeightTableActivity.this, GoalWeightActivity.class);
            startActivity(intent);
        });

        // Add weight
        btnAddWeight.setOnClickListener(v -> {
            if (!ensureLoggedInAndSetUsername()) return;

            // If goal not set, force user to set it before adding weights
            if (!ensureGoalExistsOrRoute(username)) return;

            String dateStr = editTextEnterDate.getText().toString().trim();
            String weightStr = setNewWeight.getText().toString().trim();

            if (dateStr.isEmpty() || weightStr.isEmpty()) {
                Toast.makeText(this, "Please enter both date and weight.", Toast.LENGTH_SHORT).show();
                return;
            }

            int weight;
            try {
                weight = Integer.parseInt(weightStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid weight format.", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean added = db.addWeight(username, dateStr, weight);
            if (added) {
                Toast.makeText(this, "Weight added.", Toast.LENGTH_SHORT).show();
                setNewWeight.setText("");

                String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(new java.util.Date());
                editTextEnterDate.setText(todayDate);

                loadWeights(username);
                checkGoalAchievement(username);
                loadGoalWeight(username);
            } else {
                Toast.makeText(this, "Error adding weight.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Refresh Firebase user every time we come to foreground
        if (!ensureLoggedInAndSetUsername()) return;

        // If goal is missing, send them to GoalWeightActivity
        if (!ensureGoalExistsOrRoute(username)) return;

        updateHeaderIndicators(); // keep arrows correct on return
        updateFilterSummary();
        loadWeights(username);
        loadGoalWeight(username);
        checkGoalAchievement(username);
    }

    // Ensures user is logged in and email is available; otherwise routes to login
    private boolean ensureLoggedInAndSetUsername() {
        currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return false;
        }

        username = currentUser.getEmail();
        if (username == null || username.isEmpty()) {
            Toast.makeText(this, "Error: User email is not available.", Toast.LENGTH_LONG).show();
            auth.signOut();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return false;
        }

        return true;
    }

    // Ensures a goal exists; if not, routes to GoalWeightActivity
    private boolean ensureGoalExistsOrRoute(String username) {
        List<String[]> goalData = db.getGoalWeight(username);

        if (goalData == null || goalData.isEmpty()) {
            Toast.makeText(this, "Please set a goal weight first.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, GoalWeightActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return false;
        }

        return true;
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
            String formattedDate = String.format(Locale.getDefault(),
                    "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            target.setText(formattedDate);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    // Header taps to sort wiring
    private void wireUpHeaderTaps() {
        if (headerDate == null || headerWeight == null) return;

        headerDate.setOnClickListener(v -> {
            if (!ensureLoggedInAndSetUsername()) return;
            toggleSort(SortField.DATE);
        });

        headerWeight.setOnClickListener(v -> {
            if (!ensureLoggedInAndSetUsername()) return;
            toggleSort(SortField.WEIGHT);
        });

        updateHeaderIndicators();
    }

    private void toggleSort(SortField newField) {
        if (currentSortField == newField) {
            // same column -> flip direction
            sortAscending = !sortAscending;
        } else {
            // new column -> switch field and default to descending
            currentSortField = newField;
            sortAscending = false; // newest/heaviest first
        }

        updateHeaderIndicators();
        loadWeights(username);
    }

    private void updateHeaderIndicators() {
        if (headerDate == null || headerWeight == null) return;

        String dateLabel = "Date";
        String weightLabel = "Weight";
        String arrow = sortAscending ? " ▲" : " ▼";

        if (currentSortField == SortField.DATE) {
            dateLabel += arrow;
        } else {
            weightLabel += arrow;
        }

        headerDate.setText(dateLabel);
        headerWeight.setText(weightLabel);

        // bold active header
        headerDate.setTypeface(null, currentSortField == SortField.DATE ? Typeface.BOLD : Typeface.NORMAL);
        headerWeight.setTypeface(null, currentSortField == SortField.WEIGHT ? Typeface.BOLD : Typeface.NORMAL);
    }
    // Filter - read/clear/summary
    private boolean readAndValidateFilters() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        sdf.setLenient(false);

        String startStr = editTextStartDate != null ? editTextStartDate.getText().toString().trim() : "";
        String endStr = editTextEndDate != null ? editTextEndDate.getText().toString().trim() : "";
        String minWStr = editTextMinWeight != null ? editTextMinWeight.getText().toString().trim() : "";
        String maxWStr = editTextMaxWeight != null ? editTextMaxWeight.getText().toString().trim() : "";

        // Dates
        filterStartMillis = null;
        filterEndMillis = null;

        try {
            if (!startStr.isEmpty()) {
                java.util.Date d = sdf.parse(startStr);
                if (d != null) filterStartMillis = d.getTime();
            }
            if (!endStr.isEmpty()) {
                // include the whole end day: 23:59:59.999
                java.util.Date d = sdf.parse(endStr);
                if (d != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(d);
                    cal.set(Calendar.HOUR_OF_DAY, 23);
                    cal.set(Calendar.MINUTE, 59);
                    cal.set(Calendar.SECOND, 59);
                    cal.set(Calendar.MILLISECOND, 999);
                    filterEndMillis = cal.getTimeInMillis();
                }
            }
        } catch (ParseException e) {
            Toast.makeText(this, "Invalid date format. Use YYYY-MM-DD.", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (filterStartMillis != null && filterEndMillis != null && filterStartMillis > filterEndMillis) {
            Toast.makeText(this, "Start date must be before end date.", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Weights
        filterMinWeight = null;
        filterMaxWeight = null;

        try {
            if (!minWStr.isEmpty()) filterMinWeight = Integer.parseInt(minWStr);
            if (!maxWStr.isEmpty()) filterMaxWeight = Integer.parseInt(maxWStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Weight filter must be numbers.", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (filterMinWeight != null && filterMaxWeight != null && filterMinWeight > filterMaxWeight) {
            Toast.makeText(this, "Min weight must be <= max weight.", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private void clearFiltersUIAndState() {
        if (editTextStartDate != null) editTextStartDate.setText("");
        if (editTextEndDate != null) editTextEndDate.setText("");
        if (editTextMinWeight != null) editTextMinWeight.setText("");
        if (editTextMaxWeight != null) editTextMaxWeight.setText("");

        filterStartMillis = null;
        filterEndMillis = null;
        filterMinWeight = null;
        filterMaxWeight = null;
    }

    private void updateFilterSummary() {
        if (textViewFilterSummary == null) return;

        boolean any = (filterStartMillis != null || filterEndMillis != null || filterMinWeight != null || filterMaxWeight != null);
        if (!any) {
            textViewFilterSummary.setText("");
            return;
        }

        String startStr = editTextStartDate != null ? editTextStartDate.getText().toString().trim() : "";
        String endStr = editTextEndDate != null ? editTextEndDate.getText().toString().trim() : "";
        String minWStr = editTextMinWeight != null ? editTextMinWeight.getText().toString().trim() : "";
        String maxWStr = editTextMaxWeight != null ? editTextMaxWeight.getText().toString().trim() : "";

        StringBuilder sb = new StringBuilder("Filter: ");

        if (!startStr.isEmpty() || !endStr.isEmpty()) {
            sb.append("Dates ");
            sb.append(startStr.isEmpty() ? "Any" : startStr);
            sb.append(" → ");
            sb.append(endStr.isEmpty() ? "Any" : endStr);
            sb.append("   ");
        }

        if (!minWStr.isEmpty() || !maxWStr.isEmpty()) {
            sb.append("Weight ");
            sb.append(minWStr.isEmpty() ? "Any" : minWStr);
            sb.append(" → ");
            sb.append(maxWStr.isEmpty() ? "Any" : maxWStr);
        }

        textViewFilterSummary.setText(sb.toString().trim());
    }

    private List<WeightEntry> applyFilters(List<WeightEntry> entries) {
        if (entries == null) return new ArrayList<>();

        boolean any = (filterStartMillis != null || filterEndMillis != null || filterMinWeight != null || filterMaxWeight != null);
        if (!any) return entries;

        List<WeightEntry> filtered = new ArrayList<>();

        for (WeightEntry e : entries) {
            if (filterStartMillis != null && e.dateMillis < filterStartMillis) continue;
            if (filterEndMillis != null && e.dateMillis > filterEndMillis) continue;

            if (filterMinWeight != null && e.weight < filterMinWeight) continue;
            if (filterMaxWeight != null && e.weight > filterMaxWeight) continue;

            filtered.add(e);
        }

        return filtered;
    }

    // Load weights from DB (filter+sort in-memory using merge sort)
    private void loadWeights(String username) {
        // clear old rows (keep header row at index 0)
        tableWeight.removeViews(1, Math.max(0, tableWeight.getChildCount() - 1));

        Cursor cursor = db.getWeights(username);
        if (cursor == null) return;

        List<WeightEntry> entries = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        sdf.setLenient(false);

        if (cursor.moveToFirst()) {
            do {
                String date = cursor.getString(0);
                int weight = cursor.getInt(1);

                long millis = 0L;
                try {
                    java.util.Date d = sdf.parse(date);
                    if (d != null) millis = d.getTime();
                } catch (ParseException ignored) { }

                entries.add(new WeightEntry(date, millis, weight));
            } while (cursor.moveToNext());
        }
        cursor.close();

        // Filter first (search), then sort (algorithm)
        entries = applyFilters(entries);
        entries = mergeSort(entries, currentSortField, sortAscending);

        // Render rows
        for (WeightEntry e : entries) {
            TableRow row = new TableRow(this);
            row.setPadding(10, 10, 10, 10);

            TextView textDate = new TextView(this);
            textDate.setText(e.dateStr);
            textDate.setPadding(10, 10, 10, 10);

            TextView textWeight = new TextView(this);
            textWeight.setText(String.valueOf(e.weight));
            textWeight.setPadding(10, 10, 10, 10);

            Button btnEdit = new Button(this);
            btnEdit.setText("Edit");
            btnEdit.setTextColor(Color.WHITE);
            btnEdit.setBackgroundTintList(getColorStateList(android.R.color.holo_blue_light));
            btnEdit.setOnClickListener(v -> showEditDialog(username, e.dateStr, e.weight));

            Button btnDelete = new Button(this);
            btnDelete.setText("Delete");
            btnDelete.setTextColor(Color.WHITE);
            btnDelete.setBackgroundTintList(getColorStateList(android.R.color.holo_red_dark));
            btnDelete.setOnClickListener(v -> {
                boolean deleted = db.deleteWeight(username, e.dateStr);
                if (deleted) {
                    Toast.makeText(this, "Deleted " + e.dateStr, Toast.LENGTH_SHORT).show();
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
        }
    }

    // Sort algorithm - merge sort
    private List<WeightEntry> mergeSort(List<WeightEntry> list, SortField field, boolean ascending) {
        if (list == null || list.size() <= 1) return list;

        int mid = list.size() / 2;
        List<WeightEntry> left = mergeSort(new ArrayList<>(list.subList(0, mid)), field, ascending);
        List<WeightEntry> right = mergeSort(new ArrayList<>(list.subList(mid, list.size())), field, ascending);

        return merge(left, right, field, ascending);
    }

    private List<WeightEntry> merge(List<WeightEntry> left, List<WeightEntry> right, SortField field, boolean ascending) {
        List<WeightEntry> result = new ArrayList<>(left.size() + right.size());
        int i = 0, j = 0;

        while (i < left.size() && j < right.size()) {
            WeightEntry a = left.get(i);
            WeightEntry b = right.get(j);

            int cmp = compare(a, b, field);
            boolean takeA = ascending ? (cmp <= 0) : (cmp >= 0);

            if (takeA) {
                result.add(a);
                i++;
            } else {
                result.add(b);
                j++;
            }
        }

        while (i < left.size()) result.add(left.get(i++));
        while (j < right.size()) result.add(right.get(j++));

        return result;
    }

    private int compare(WeightEntry a, WeightEntry b, SortField field) {
        switch (field) {
            case DATE:
                return Long.compare(a.dateMillis, b.dateMillis);
            case WEIGHT:
                return Integer.compare(a.weight, b.weight);
            default:
                return 0;
        }
    }

    // Helper to send SMS if goal reached.
    private void sendGoalReachedSMS(String username, String sms_message) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String phoneNumber = prefs.getString("smsNumber", "");

        String message = "Hi " + username + ", " + sms_message;

        try {
            SmsManager smsManager = getSystemService(SmsManager.class);
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Toast.makeText(this, "SMS sent: " + phoneNumber, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "SMS failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Check if goal was met or exceeded within the last week
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
        sdf.setLenient(false);

        Integer recentWeight = null;
        Long recentDate = null;

        // Don't skip the first row
        if (cursor.moveToFirst()) {
            do {
                String dateStr = cursor.getString(0);
                int weight = cursor.getInt(1);
                try {
                    java.util.Date d = sdf.parse(dateStr);
                    if (d == null) continue;
                    long dateMillis = d.getTime();

                    if (dateMillis >= oneWeekAgoMillis) {
                        if (recentDate == null || dateMillis > recentDate) {
                            recentDate = dateMillis;
                            recentWeight = weight;
                        }
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            } while (cursor.moveToNext());
        }
        cursor.close();

        if (recentWeight == null) return;

        boolean goalMet = false;
        String message = "";
        String message_for_sms = "";

        if (goalType.equalsIgnoreCase("loss") && recentWeight <= goalWeight) {
            goalMet = true;
            message = "🎉 Congratulations! You've reached your goal weight of " + goalWeight +
                    " lbs.\nKeep up the great work!";
            message_for_sms = "Congratulations! You've reached your goal weight of " + goalWeight +
                    " lbs. Keep up the great work!";
        } else if (goalType.equalsIgnoreCase("gain") && recentWeight >= goalWeight) {
            goalMet = true;
            message = "💪 Fantastic! You've achieved your weight gain goal of " + goalWeight +
                    " lbs.\nKeep going strong!";
            message_for_sms = "Fantastic! You've achieved your weight gain goal of " + goalWeight +
                    " lbs. Keep going strong!";
        }

        if (goalMet) {
            new AlertDialog.Builder(this)
                    .setTitle("Goal Achieved! 🏆")
                    .setMessage(message)
                    .setPositiveButton("Awesome!", (dialog, which) -> dialog.dismiss())
                    .show();

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean smsEnabled = prefs.getBoolean(KEY_SMS_ENABLED, false);

            if (smsEnabled) {
                sendGoalReachedSMS(username, message_for_sms);
            }
        }
    }

    // Edit dialog for weight and date
    private void showEditDialog(String username, String oldDate, int oldWeight) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Weight Entry");

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
