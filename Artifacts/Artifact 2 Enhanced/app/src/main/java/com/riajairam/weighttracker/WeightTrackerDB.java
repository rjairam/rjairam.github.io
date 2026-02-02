package com.riajairam.weighttracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class WeightTrackerDB extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "weighttracker.db";
    // Bump version because we're removing the auth table and changing schema
    private static final int DATABASE_VERSION = 5;

    // Table names
    private static final String TABLE_GOAL = "goal_weight";
    private static final String TABLE_WEIGHTS = "weights";

    // Common columns
    private static final String COL_USERNAME = "username"; // this will now be Firebase email

    // Goal columns
    private static final String COL_GOAL_WEIGHT = "goal_weight";
    private static final String COL_GOAL_TYPE = "goal_type";

    // Weights columns
    private static final String COL_DATE = "date";
    private static final String COL_WEIGHT = "weight";

    public WeightTrackerDB(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /*
      Firebase Auth now handles users/passwords.
      This SQLite DB only stores per-user data (goal weight + weight entries),
      keyed by username = Firebase user email.

      This will be updated to use firebase in a future enhancement (enhancement 3).
    */

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_GOAL + " (" +
                COL_USERNAME + " TEXT PRIMARY KEY, " +
                COL_GOAL_WEIGHT + " INTEGER, " +
                COL_GOAL_TYPE + " TEXT" +
                ")");

        // Ensure uniqueness: one weight entry per username per date
        db.execSQL("CREATE TABLE " + TABLE_WEIGHTS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_USERNAME + " TEXT NOT NULL, " +
                COL_DATE + " TEXT NOT NULL, " +
                COL_WEIGHT + " INTEGER NOT NULL, " +
                "UNIQUE(" + COL_USERNAME + ", " + COL_DATE + ") ON CONFLICT REPLACE" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        // If upgrading from older versions that had auth table, remove it safely
        if (oldVersion < 5) {
            // Drop old tables if they exist, then recreate fresh schema
            db.execSQL("DROP TABLE IF EXISTS auth");
            db.execSQL("DROP TABLE IF EXISTS goal_weight");
            db.execSQL("DROP TABLE IF EXISTS weights");
            onCreate(db);
        }
    }


    // GOAL WEIGHT

    // Set goal weight and type (gain/loss)
    public boolean setGoalWeight(String username, int goalWeight, String goalType) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COL_USERNAME, username);
        values.put(COL_GOAL_WEIGHT, goalWeight);
        values.put(COL_GOAL_TYPE, goalType);

        // Replace so each user has only one goal row
        long result = db.insertWithOnConflict(TABLE_GOAL, null, values, SQLiteDatabase.CONFLICT_REPLACE);

        db.close();
        return result != -1;
    }

    public List<String[]> getGoalWeight(String username) {
        List<String[]> goalList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_GOAL,
                new String[]{COL_GOAL_WEIGHT, COL_GOAL_TYPE},
                COL_USERNAME + "=?",
                new String[]{username},
                null, null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int goalWeight = cursor.getInt(0);
                String goalType = cursor.getString(1);
                goalList.add(new String[]{String.valueOf(goalWeight), goalType});
            }
            cursor.close();
        }

        db.close();
        return goalList;
    }


    // WEIGHT ENTRIES

    public boolean addWeight(String username, String date, int weight) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COL_USERNAME, username);
        values.put(COL_DATE, date);
        values.put(COL_WEIGHT, weight);

        long result = db.insertWithOnConflict(TABLE_WEIGHTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);

        db.close();
        return result != -1;
    }

    public Cursor getWeights(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_WEIGHTS,
                new String[]{COL_DATE, COL_WEIGHT},
                COL_USERNAME + "=?",
                new String[]{username},
                null, null,
                COL_DATE + " DESC");
    }

    public boolean updateWeight(String username, String oldDate, String newDate, int newWeight) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COL_DATE, newDate);
        values.put(COL_WEIGHT, newWeight);

        int result = db.update(TABLE_WEIGHTS, values,
                COL_USERNAME + "=? AND " + COL_DATE + "=?",
                new String[]{username, oldDate});

        db.close();
        return result > 0;
    }

    public boolean deleteWeight(String username, String date) {
        SQLiteDatabase db = this.getWritableDatabase();

        int result = db.delete(TABLE_WEIGHTS,
                COL_USERNAME + "=? AND " + COL_DATE + "=?",
                new String[]{username, date});

        db.close();
        return result > 0;
    }
}
