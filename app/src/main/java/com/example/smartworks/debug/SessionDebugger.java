package com.example.smartworks.debug;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Simple debug utility to check what's stored in SharedPreferences
 */
public class SessionDebugger {
    private static final String TAG = "SessionDebugger";
    private static final String PREFS_NAME = "SmartWorksAuth";
    
    public static void logSessionData(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            Log.d(TAG, "=== SESSION DEBUG DATA ===");
            Log.d(TAG, "is_logged_in: " + prefs.getBoolean("is_logged_in", false));
            Log.d(TAG, "user_id: " + prefs.getInt("user_id", 0));
            Log.d(TAG, "username: " + prefs.getString("username", "null"));
            Log.d(TAG, "email: " + prefs.getString("email", "null"));
            Log.d(TAG, "api_key: " + prefs.getString("api_key", "null"));
            Log.d(TAG, "role: " + prefs.getString("role", "null"));
            Log.d(TAG, "=== END SESSION DEBUG ===");
            
        } catch (Exception e) {
            Log.e(TAG, "Error reading session data", e);
        }
    }
    
    public static void clearSessionData(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();
            Log.d(TAG, "Session data cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing session data", e);
        }
    }
}