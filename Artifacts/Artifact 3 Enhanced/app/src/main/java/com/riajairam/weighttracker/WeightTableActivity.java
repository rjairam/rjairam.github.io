package com.riajairam.weighttracker;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// Firestore includes
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WeightTableActivity extends AppCompatActivity {

    // Firebase Auth
    private FirebaseAuth auth;
    private FirebaseUser currentUser;

    // Firestore
    private FirebaseFirestore fs;

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

    private String username; // Firebase email (for display / SMS greeting)

    // Sort via header tap (local algorithm sort)
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

    // Cache the goal (so you don't fetch from the cloud repeatedly)
    private Integer cachedGoalWeight = null;
    private String cachedGoalType = null;

    // Cached weights list (used for checking goal without extra cloud access)
    private List<WeightEntry> cachedAllWeights = new ArrayList<>();

    // Date parser
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.weighttable);

        auth = FirebaseAuth.getInstance();
        fs = FirebaseFirestore.getInstance();
        sdf.setLenient(false);

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
                refreshAllData(); // reload from Firestore, then apply filter and sort
            });
        }

        if (btnClearFilter != null) {
            btnClearFilter.setOnClickListener(v -> {
                clearFiltersUIAndState();
                updateFilterSummary();
                if (!ensureLoggedInAndSetUsername()) return;
                refreshAllData();
            });
        }

        updateFilterSummary();

        // Set today's date by default
        String today = sdf.format(new java.util.Date());
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

        // Add weight (Firestore write)
        btnAddWeight.setOnClickListener(v -> {
            if (!ensureLoggedInAndSetUsername()) return;

            // Ensure goal exists first (asynchronous)
            ensureGoalExistsOrRouteAsync(ok -> {
                if (!ok) return;

                String dateStr = editTextEnterDate.getText().toString().trim();
                String weightStr = setNewWeight.getText().toString().trim();

                if (dateStr.isEmpty() || weightStr.isEmpty()) {
                    Toast.makeText(this, "Please enter both date and weight.", Toast.LENGTH_SHORT).show();
                    return;
                }

                int weight;

                //Input validation - check if date is integer
                try {
                    weight = Integer.parseInt(weightStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid weight format.", Toast.LENGTH_SHORT).show();
                    return;
                }

                long millis;
                try {
                    java.util.Date d = sdf.parse(dateStr);
                    millis = (d == null) ? 0L : d.getTime();
                } catch (ParseException e) {
                    Toast.makeText(this, "Invalid date. Use YYYY-MM-DD.", Toast.LENGTH_SHORT).show();
                    return;
                }

                addOrReplaceWeightFirestore(dateStr, millis, weight);
            });
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!ensureLoggedInAndSetUsername()) return;

        updateHeaderIndicators();
        updateFilterSummary();

        // Must be asynchronous because Firestore
        ensureGoalExistsOrRouteAsync(ok -> {
            if (!ok) return;
            refreshAllData();
        });
    }

    // Firestore paths

    private String uidOrNull() {
        FirebaseUser u = auth.getCurrentUser();
        return (u == null) ? null : u.getUid();
    }

    private DocumentReference goalDoc() {
        String uid = uidOrNull();
        return fs.collection("users").document(uid).collection("goal").document("main");
    }

    private CollectionReference weightsCol() {
        String uid = uidOrNull();
        return fs.collection("users").document(uid).collection("weights");
    }

    private DocumentReference weightDoc(String dateKey) {
        String uid = uidOrNull();
        return fs.collection("users").document(uid).collection("weights").document(dateKey);
    }

    //Login helpers

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

    //Goal: check or load

    private interface BoolCallback { void onDone(boolean ok); }

    private void ensureGoalExistsOrRouteAsync(BoolCallback cb) {
        // If cached and valid, use it
        if (cachedGoalWeight != null && cachedGoalType != null && !cachedGoalType.trim().isEmpty()) {
            cb.onDone(true);
            return;
        }

        goalDoc().get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        Toast.makeText(this, "Please set a goal weight first.", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(this, GoalWeightActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                        cb.onDone(false);
                        return;
                    }

                    Long gw = snapshot.getLong("goalWeight");
                    String gt = snapshot.getString("goalType");

                    if (gw == null || gt == null || gt.trim().isEmpty()) {
                        Toast.makeText(this, "Please set a goal weight first.", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(this, GoalWeightActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                        cb.onDone(false);
                        return;
                    }

                    cachedGoalWeight = gw.intValue();
                    cachedGoalType = gt;
                    loadGoalWeightToUI();
                    cb.onDone(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Goal load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    cb.onDone(false);
                });
    }

    private void loadGoalWeightToUI() {
        TextView textGoalWeight = findViewById(R.id.textViewGoalWeight);
        if (cachedGoalWeight != null && cachedGoalType != null) {
            textGoalWeight.setText("Goal Weight: " + cachedGoalWeight + " lbs (" + cachedGoalType + ")");
        } else {
            textGoalWeight.setText("Goal Weight: Not Set");
        }
    }

    // Refresh all data

    private void refreshAllData() {
        // Reload goal in case it changed then load weights.
        goalDoc().get()
                .addOnSuccessListener(goalSnap -> {
                    if (goalSnap.exists()) {
                        Long gw = goalSnap.getLong("goalWeight");
                        String gt = goalSnap.getString("goalType");
                        cachedGoalWeight = (gw == null) ? null : gw.intValue();
                        cachedGoalType = gt;
                    } else {
                        cachedGoalWeight = null;
                        cachedGoalType = null;
                    }
                    loadGoalWeightToUI();

                    // Now load weights
                    loadWeightsFromFirestore();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Goal load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // Still try weights
                    loadWeightsFromFirestore();
                });
    }

    //Weights: Firestore CRUD

    private void addOrReplaceWeightFirestore(String dateKey, long dateMillis, int weight) {
        Map<String, Object> data = new HashMap<>();
        data.put("date", dateKey);
        data.put("dateMillis", dateMillis);
        data.put("weight", weight);

        weightDoc(dateKey).set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Weight added.", Toast.LENGTH_SHORT).show();
                    setNewWeight.setText("");
                    editTextEnterDate.setText(sdf.format(new java.util.Date()));
                    refreshAllData();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error adding weight: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void deleteWeightFirestore(String dateKey) {
        weightDoc(dateKey).delete()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Deleted " + dateKey, Toast.LENGTH_SHORT).show();
                    refreshAllData();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void updateWeightFirestore(String oldDateKey, String newDateKey, long newDateMillis, int newWeight) {
        // If date unchanged, overwrite same doc
        if (oldDateKey.equals(newDateKey)) {
            addOrReplaceWeightFirestore(newDateKey, newDateMillis, newWeight);
            return;
        }

        // If date changed, create new doc then delete old doc (best-effort sequence)
        Map<String, Object> data = new HashMap<>();
        data.put("date", newDateKey);
        data.put("dateMillis", newDateMillis);
        data.put("weight", newWeight);

        weightDoc(newDateKey).set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> weightDoc(oldDateKey).delete()
                        .addOnSuccessListener(u2 -> {
                            Toast.makeText(this, "Updated successfully!", Toast.LENGTH_SHORT).show();
                            refreshAllData();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Updated (but old row not deleted): " + e.getMessage(), Toast.LENGTH_LONG).show();
                            refreshAllData();
                        }))
                .addOnFailureListener(e -> Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    //Load weights - Firestore -> filter -> sort -> render

    private void loadWeightsFromFirestore() {
        // Clear old rows, keep header row at index 0
        tableWeight.removeViews(1, Math.max(0, tableWeight.getChildCount() - 1));

        weightsCol().get()
                .addOnSuccessListener(this::onWeightsLoaded)
                .addOnFailureListener(e -> Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void onWeightsLoaded(QuerySnapshot query) {
        List<WeightEntry> all = new ArrayList<>();

        for (DocumentSnapshot doc : query.getDocuments()) {
            String date = doc.getString("date");
            Long dm = doc.getLong("dateMillis");
            Long w = doc.getLong("weight");
            if (date == null || w == null) continue;

            long dateMillis = (dm != null) ? dm : parseMillisOrZero(date);
            all.add(new WeightEntry(date, dateMillis, w.intValue()));
        }

        cachedAllWeights = all;

        // Apply search filters & algorithm sort (merge sort)
        List<WeightEntry> viewList = applyFilters(all);
        viewList = mergeSort(viewList, currentSortField, sortAscending);

        renderTable(viewList);

        // Goal check based on ALL weights (not filtered) to avoid confusion
        checkGoalAchievementFromCached();
    }

    private long parseMillisOrZero(String dateStr) {
        try {
            java.util.Date d = sdf.parse(dateStr);
            return (d == null) ? 0L : d.getTime();
        } catch (ParseException e) {
            return 0L;
        }
    }

    private void renderTable(List<WeightEntry> entries) {
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
            btnEdit.setOnClickListener(v -> showEditDialog(e.dateStr, e.weight));

            Button btnDelete = new Button(this);
            btnDelete.setText("Delete");
            btnDelete.setTextColor(Color.WHITE);
            btnDelete.setBackgroundTintList(getColorStateList(android.R.color.holo_red_dark));
            btnDelete.setOnClickListener(v -> deleteWeightFirestore(e.dateStr));

            row.addView(textDate);
            row.addView(textWeight);
            row.addView(btnEdit);
            row.addView(btnDelete);

            tableWeight.addView(row);
        }
    }

    //Date picker

    private void showDatePicker(EditText target) {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String formattedDate = String.format(Locale.getDefault(),
                    "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            target.setText(formattedDate);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    //Sorting (header taps)

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
            sortAscending = !sortAscending;
        } else {
            currentSortField = newField;
            sortAscending = false;
        }

        updateHeaderIndicators();
        // No DB sorting; just re-render from cached list
        List<WeightEntry> viewList = applyFilters(cachedAllWeights);
        viewList = mergeSort(viewList, currentSortField, sortAscending);

        // Clear & render
        tableWeight.removeViews(1, Math.max(0, tableWeight.getChildCount() - 1));
        renderTable(viewList);
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

        headerDate.setTypeface(null, currentSortField == SortField.DATE ? Typeface.BOLD : Typeface.NORMAL);
        headerWeight.setTypeface(null, currentSortField == SortField.WEIGHT ? Typeface.BOLD : Typeface.NORMAL);
    }

    //  Filtering

    private boolean readAndValidateFilters() {
        sdf.setLenient(false);

        String startStr = editTextStartDate != null ? editTextStartDate.getText().toString().trim() : "";
        String endStr = editTextEndDate != null ? editTextEndDate.getText().toString().trim() : "";
        String minWStr = editTextMinWeight != null ? editTextMinWeight.getText().toString().trim() : "";
        String maxWStr = editTextMaxWeight != null ? editTextMaxWeight.getText().toString().trim() : "";

        filterStartMillis = null;
        filterEndMillis = null;

        try {
            if (!startStr.isEmpty()) {
                java.util.Date d = sdf.parse(startStr);
                if (d != null) filterStartMillis = d.getTime();
            }
            if (!endStr.isEmpty()) {
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

    // Sorting algorithm - merge sort
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

    // Goal achievement with no extra DB calls

    private void checkGoalAchievementFromCached() {
        if (cachedGoalWeight == null || cachedGoalType == null) return;
        if (cachedAllWeights == null || cachedAllWeights.isEmpty()) return;

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -7);
        long oneWeekAgoMillis = calendar.getTimeInMillis();

        Integer recentWeight = null;
        Long recentDate = null;

        for (WeightEntry e : cachedAllWeights) {
            if (e.dateMillis >= oneWeekAgoMillis) {
                if (recentDate == null || e.dateMillis > recentDate) {
                    recentDate = e.dateMillis;
                    recentWeight = e.weight;
                }
            }
        }

        if (recentWeight == null) return;

        boolean goalMet = false;
        String message = "";
        String messageForSms = "";

        if ("loss".equalsIgnoreCase(cachedGoalType) && recentWeight <= cachedGoalWeight) {
            goalMet = true;
            message = "🎉 Congratulations! You've reached your goal weight of " + cachedGoalWeight + " lbs.\nKeep up the great work!";
            messageForSms = "Congratulations! You've reached your goal weight of " + cachedGoalWeight + " lbs. Keep up the great work!";
        } else if ("gain".equalsIgnoreCase(cachedGoalType) && recentWeight >= cachedGoalWeight) {
            goalMet = true;
            message = "💪 Fantastic! You've achieved your weight gain goal of " + cachedGoalWeight + " lbs.\nKeep going strong!";
            messageForSms = "Fantastic! You've achieved your weight gain goal of " + cachedGoalWeight + " lbs. Keep going strong!";
        }

        if (!goalMet) return;

        new AlertDialog.Builder(this)
                .setTitle("Goal Achieved! 🏆")
                .setMessage(message)
                .setPositiveButton("Awesome!", (dialog, which) -> dialog.dismiss())
                .show();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean smsEnabled = prefs.getBoolean(KEY_SMS_ENABLED, false);

        if (smsEnabled) {
            sendGoalReachedSMS(username, messageForSms);
        }
    }

    // Helper to send SMS if goal reached.
    private void sendGoalReachedSMS(String username, String smsMessage) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String phoneNumber = prefs.getString("smsNumber", "");

        String message = "Hi " + username + ", " + smsMessage;

        try {
            SmsManager smsManager = getSystemService(SmsManager.class);
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Toast.makeText(this, "SMS sent: " + phoneNumber, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "SMS failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    //Edit dialog (Firestore update)

    private void showEditDialog(String oldDate, int oldWeight) {
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

            long newMillis;
            try {
                java.util.Date d = sdf.parse(newDate);
                newMillis = (d == null) ? 0L : d.getTime();
            } catch (ParseException e) {
                Toast.makeText(this, "Invalid date. Use YYYY-MM-DD.", Toast.LENGTH_SHORT).show();
                return;
            }

            updateWeightFirestore(oldDate, newDate, newMillis, newWeight);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
