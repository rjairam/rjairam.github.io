package com.riajairam.weighttracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class WeightTrackerDB extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "weighttracker.db";
    private static final int DATABASE_VERSION = 4; // bump version for uniqueness constraint

    // Table names
    private static final String TABLE_AUTH = "auth";
    private static final String TABLE_GOAL = "goal_weight";
    private static final String TABLE_WEIGHTS = "weights";

    // Common columns
    private static final String COL_USERNAME = "username";

    // Auth columns
    private static final String COL_PASSWORD_HASH = "password_hash";
    private static final String COL_SALT = "salt";

    // Goal columns
    private static final String COL_GOAL_WEIGHT = "goal_weight";
    private static final String COL_GOAL_TYPE = "goal_type";

    // Weights columns
    private static final String COL_DATE = "date";
    private static final String COL_WEIGHT = "weight";

    public WeightTrackerDB(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    //Here we use SQLite - three tables:
    // 1. Table for users and (hashed) passwords
    // 2. Table for goal weight, type of goal (gain/loss) keyed by username
    // 3. Table for weights and dates, keyed by username

    // We have a fully functional CRUD class for the weights

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_AUTH + " (" +
                COL_USERNAME + " TEXT PRIMARY KEY, " +
                COL_PASSWORD_HASH + " TEXT, " +
                COL_SALT + " TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_GOAL + " (" +
                COL_USERNAME + " TEXT, " +
                COL_GOAL_WEIGHT + " INTEGER, " +
                COL_GOAL_TYPE + " TEXT, " +
                "FOREIGN KEY(" + COL_USERNAME + ") REFERENCES " + TABLE_AUTH + "(" + COL_USERNAME + "))");

        // Ensure uniqueness: one weight entry per username per date
        db.execSQL("CREATE TABLE " + TABLE_WEIGHTS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_USERNAME + " TEXT, " +
                COL_DATE + " TEXT, " +
                COL_WEIGHT + " INTEGER, " +
                "UNIQUE(" + COL_USERNAME + ", " + COL_DATE + ") ON CONFLICT REPLACE, " +
                "FOREIGN KEY(" + COL_USERNAME + ") REFERENCES " + TABLE_AUTH + "(" + COL_USERNAME + "))");
    }

    //OnUpgrade is if we need to u
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 4) {
            // Add uniqueness constraint safely
            db.execSQL("CREATE TABLE weights_new (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_USERNAME + " TEXT, " +
                    COL_DATE + " TEXT, " +
                    COL_WEIGHT + " INTEGER, " +
                    "UNIQUE(" + COL_USERNAME + ", " + COL_DATE + ") ON CONFLICT REPLACE, " +
                    "FOREIGN KEY(" + COL_USERNAME + ") REFERENCES " + TABLE_AUTH + "(" + COL_USERNAME + "))");
            db.execSQL("INSERT INTO weights_new (" + COL_USERNAME + ", " + COL_DATE + ", " + COL_WEIGHT + ") " +
                    "SELECT " + COL_USERNAME + ", " + COL_DATE + ", " + COL_WEIGHT + " FROM " + TABLE_WEIGHTS + ";");
            db.execSQL("DROP TABLE " + TABLE_WEIGHTS + ";");
            db.execSQL("ALTER TABLE weights_new RENAME TO " + TABLE_WEIGHTS + ";");
        }
    }

    // PASSWORD HASHING
    // Because storing plaintext passwords is unsafe
    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    //Hash the password using SHA-256 if possible
    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashed = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // AUTHENTICATION
    public boolean registerUser(String username, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.query(TABLE_AUTH, null, COL_USERNAME + "=?", new String[]{username}, null, null, null);
        if (cursor.moveToFirst()) {
            cursor.close();
            db.close();
            return false;
        }
        cursor.close();

        String salt = generateSalt();
        String hash = hashPassword(password, salt);

        ContentValues values = new ContentValues();
        values.put(COL_USERNAME, username);
        values.put(COL_PASSWORD_HASH, hash);
        values.put(COL_SALT, salt);

        long result = db.insert(TABLE_AUTH, null, values);
        db.close();
        return result != -1;
    }

    //To check we retrieve by username and compare hashes
    public boolean checkLogin(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_AUTH,
                new String[]{COL_PASSWORD_HASH, COL_SALT},
                COL_USERNAME + "=?",
                new String[]{username},
                null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            String storedHash = cursor.getString(0);
            String salt = cursor.getString(1);
            cursor.close();
            db.close();
            return storedHash.equals(hashPassword(password, salt));
        }

        if (cursor != null) cursor.close();
        db.close();
        return false;
    }

    // GOAL WEIGHT

    //Method to set the goal weight and type
    public boolean setGoalWeight(String username, int goalWeight, String goalType) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_GOAL, COL_USERNAME + "=?", new String[]{username});

        ContentValues values = new ContentValues();
        values.put(COL_USERNAME, username);
        values.put(COL_GOAL_WEIGHT, goalWeight);
        values.put(COL_GOAL_TYPE, goalType);

        long result = db.insert(TABLE_GOAL, null, values);
        db.close();
        return result != -1;
    }

    //Get the goal weight
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
                do {
                    int goalWeight = cursor.getInt(cursor.getColumnIndexOrThrow(COL_GOAL_WEIGHT));
                    String goalType = cursor.getString(cursor.getColumnIndexOrThrow(COL_GOAL_TYPE));
                    goalList.add(new String[]{String.valueOf(goalWeight), goalType});
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        db.close();
        return goalList;
    }


    // WEIGHT ENTRIES
    // Add weight
    public boolean addWeight(String username, String date, int weight) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_USERNAME, username);
        values.put(COL_DATE, date);
        values.put(COL_WEIGHT, weight);

        // thanks to UNIQUE(username, date), insert will replace existing entry
        long result = db.insertWithOnConflict(TABLE_WEIGHTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
        return result != -1;
    }

    //Get weights
    public Cursor getWeights(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_WEIGHTS,
                new String[]{COL_DATE, COL_WEIGHT},
                COL_USERNAME + "=?",
                new String[]{username},
                null, null,
                COL_DATE + " DESC");
    }

    //Update weight
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

    //Delete weight
    public boolean deleteWeight(String username, String date) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_WEIGHTS,
                COL_USERNAME + "=? AND " + COL_DATE + "=?",
                new String[]{username, date});
        db.close();
        return result > 0;
    }
}
