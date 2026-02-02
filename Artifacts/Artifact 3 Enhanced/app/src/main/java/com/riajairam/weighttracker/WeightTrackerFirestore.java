package com.riajairam.weighttracker;

import androidx.annotation.NonNull;
import com.google.firebase.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.*;

public class WeightTrackerFirestore {

    private final FirebaseFirestore fs = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    // Simple callback types
    public interface BoolCallback { void onComplete(boolean ok, String error); }
    public interface GoalCallback { void onComplete(Integer goalWeight, String goalType, String error); }
    public interface WeightsCallback { void onComplete(List<WeightRow> rows, String error); }

    // Model for returning weights to the UI
    public static class WeightRow {
        public String date;     // yyyy-MM-dd
        public long dateMillis; // optional, helps filter/sort
        public int weight;

        public WeightRow() {} // required for Firestore to use toObject()

        public WeightRow(String date, long dateMillis, int weight) {
            this.date = date;
            this.dateMillis = dateMillis;
            this.weight = weight;
        }
    }

    private String requireUidOrNull() {
        FirebaseUser user = auth.getCurrentUser();
        return (user == null) ? null : user.getUid();
    }

    private DocumentReference goalDoc(String uid) {
        return fs.collection("users").document(uid).collection("goal").document("main");
    }

    private DocumentReference weightDoc(String uid, String dateKey) {
        return fs.collection("users").document(uid).collection("weights").document(dateKey);
    }

    //Goal weight

    public void setGoalWeight(int goalWeight, String goalType, @NonNull BoolCallback cb) {
        String uid = requireUidOrNull();
        if (uid == null) { cb.onComplete(false, "Not logged in"); return; }

        Map<String, Object> data = new HashMap<>();
        data.put("goalWeight", goalWeight);
        data.put("goalType", goalType);

        goalDoc(uid).set(data)
                .addOnSuccessListener(unused -> cb.onComplete(true, null))
                .addOnFailureListener(e -> cb.onComplete(false, e.getMessage()));
    }

    public void getGoalWeight(@NonNull GoalCallback cb) {
        String uid = requireUidOrNull();
        if (uid == null) { cb.onComplete(null, null, "Not logged in"); return; }

        goalDoc(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        cb.onComplete(null, null, null); // not set
                        return;
                    }
                    Long gw = snapshot.getLong("goalWeight");
                    String gt = snapshot.getString("goalType");
                    cb.onComplete(gw == null ? null : gw.intValue(), gt, null);
                })
                .addOnFailureListener(e -> cb.onComplete(null, null, e.getMessage()));
    }

   // Weights

    public void addOrReplaceWeight(String dateKey, long dateMillis, int weight, @NonNull BoolCallback cb) {
        String uid = requireUidOrNull();
        if (uid == null) { cb.onComplete(false, "Not logged in"); return; }

        Map<String, Object> data = new HashMap<>();
        data.put("date", dateKey);
        data.put("dateMillis", dateMillis);
        data.put("weight", weight);

        // doc id = dateKey means "one weight per date"
        weightDoc(uid, dateKey).set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> cb.onComplete(true, null))
                .addOnFailureListener(e -> cb.onComplete(false, e.getMessage()));
    }

    public void deleteWeight(String dateKey, @NonNull BoolCallback cb) {
        String uid = requireUidOrNull();
        if (uid == null) { cb.onComplete(false, "Not logged in"); return; }

        weightDoc(uid, dateKey).delete()
                .addOnSuccessListener(unused -> cb.onComplete(true, null))
                .addOnFailureListener(e -> cb.onComplete(false, e.getMessage()));
    }

    public void updateWeight(String oldDateKey, String newDateKey, long newDateMillis, int newWeight, @NonNull BoolCallback cb) {
        String uid = requireUidOrNull();
        if (uid == null) { cb.onComplete(false, "Not logged in"); return; }

        // If date didn't change, just overwrite same doc
        if (oldDateKey.equals(newDateKey)) {
            addOrReplaceWeight(newDateKey, newDateMillis, newWeight, cb);
            return;
        }

        // If date changed, create new doc then delete old doc (transaction keeps it consistent)
        fs.runTransaction(transaction -> {
                    DocumentReference oldRef = weightDoc(uid, oldDateKey);
                    DocumentReference newRef = weightDoc(uid, newDateKey);

                    Map<String, Object> data = new HashMap<>();
                    data.put("date", newDateKey);
                    data.put("dateMillis", newDateMillis);
                    data.put("weight", newWeight);

                    transaction.set(newRef, data, SetOptions.merge());
                    transaction.delete(oldRef);
                    return null;
                }).addOnSuccessListener(unused -> cb.onComplete(true, null))
                .addOnFailureListener(e -> cb.onComplete(false, e.getMessage()));
    }

    public void getWeights(@NonNull WeightsCallback cb) {
        String uid = requireUidOrNull();
        if (uid == null) { cb.onComplete(Collections.emptyList(), "Not logged in"); return; }

        // Get everything (can also do orderBy("dateMillis") etc.)
        fs.collection("users").document(uid).collection("weights")
                .get()
                .addOnSuccessListener(query -> {
                    List<WeightRow> rows = new ArrayList<>();
                    for (DocumentSnapshot doc : query.getDocuments()) {
                        String date = doc.getString("date");
                        Long dm = doc.getLong("dateMillis");
                        Long w = doc.getLong("weight");
                        if (date == null || w == null) continue;
                        rows.add(new WeightRow(date, dm == null ? 0L : dm, w.intValue()));
                    }
                    cb.onComplete(rows, null);
                })
                .addOnFailureListener(e -> cb.onComplete(Collections.emptyList(), e.getMessage()));
    }
}
