package com.example.smartworks.debug;

import android.os.AsyncTask;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Android Login Debug Tester
 * Add this to your app to test the exact login process
 */
public class AndroidLoginTester {
    private static final String TAG = "AndroidLoginTester";
    private static final String LOGIN_URL = "https://smartworkstech.com/server/login.php";
    
    public interface LoginTestListener {
        void onTestResult(String step, String result, boolean success);
        void onTestComplete(boolean overallSuccess);
    }
    
    private LoginTestListener listener;
    
    public AndroidLoginTester(LoginTestListener listener) {
        this.listener = listener;
    }
    
    public void runLoginTest(String username, String password) {
        new LoginTestTask().execute(username, password);
    }
    
    private class LoginTestTask extends AsyncTask<String, String, Boolean> {
        
        @Override
        protected Boolean doInBackground(String... params) {
            String username = params[0];
            String password = params[1];
            boolean overallSuccess = true;
            
            try {
                // Test 1: Basic connectivity
                publishProgress("1", "Testing basic connectivity to server...", "info");
                
                URL url = new URL(LOGIN_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                
                int responseCode = connection.getResponseCode();
                publishProgress("1", "Server responded with code: " + responseCode, 
                    responseCode == 200 ? "success" : "error");
                
                if (responseCode != 200) {
                    overallSuccess = false;
                }
                
                // Test 2: JSON POST request
                publishProgress("2", "Testing JSON POST request...", "info");
                
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                
                // Create JSON payload
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("username", username);
                jsonBody.put("password", password);
                
                String jsonString = jsonBody.toString();
                publishProgress("2", "Sending JSON: " + jsonString, "info");
                
                // Send request
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                // Get response
                responseCode = connection.getResponseCode();
                publishProgress("2", "POST response code: " + responseCode, 
                    responseCode == 200 ? "success" : "error");
                
                // Read response body
                BufferedReader reader;
                if (responseCode >= 200 && responseCode < 300) {
                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                } else {
                    reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    overallSuccess = false;
                }
                
                StringBuilder responseBody = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBody.append(line);
                }
                reader.close();
                
                String response = responseBody.toString();
                publishProgress("3", "Server response: " + response, "info");
                
                // Test 3: Parse JSON response
                publishProgress("4", "Testing JSON response parsing...", "info");
                
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    
                    if (jsonResponse.has("status")) {
                        String status = jsonResponse.getString("status");
                        publishProgress("4", "Status: " + status, 
                            "ok".equals(status) ? "success" : "error");
                        
                        if ("ok".equals(status)) {
                            if (jsonResponse.has("api_key")) {
                                String apiKey = jsonResponse.getString("api_key");
                                publishProgress("4", "API Key received: " + 
                                    apiKey.substring(0, Math.min(10, apiKey.length())) + "...", "success");
                            }
                            if (jsonResponse.has("user_id")) {
                                int userId = jsonResponse.getInt("user_id");
                                publishProgress("4", "User ID: " + userId, "success");
                            }
                        } else if (jsonResponse.has("message")) {
                            String message = jsonResponse.getString("message");
                            publishProgress("4", "Error message: " + message, "error");
                            overallSuccess = false;
                        }
                    } else {
                        publishProgress("4", "No 'status' field in response - might be HTML", "error");
                        overallSuccess = false;
                    }
                    
                } catch (Exception e) {
                    publishProgress("4", "JSON parsing failed: " + e.getMessage(), "error");
                    publishProgress("4", "Raw response (first 200 chars): " + 
                        response.substring(0, Math.min(200, response.length())), "error");
                    overallSuccess = false;
                }
                
                // Test 4: Headers analysis
                publishProgress("5", "Analyzing response headers...", "info");
                
                String contentType = connection.getHeaderField("Content-Type");
                publishProgress("5", "Content-Type: " + contentType, 
                    contentType != null && contentType.contains("json") ? "success" : "warning");
                
                String setCookie = connection.getHeaderField("Set-Cookie");
                if (setCookie != null) {
                    publishProgress("5", "Session cookie set: " + setCookie.substring(0, Math.min(50, setCookie.length())), "info");
                } else {
                    publishProgress("5", "No session cookie set", "warning");
                }
                
            } catch (Exception e) {
                publishProgress("ERROR", "Network error: " + e.getMessage(), "error");
                Log.e(TAG, "Login test failed", e);
                overallSuccess = false;
            }
            
            return overallSuccess;
        }
        
        @Override
        protected void onProgressUpdate(String... values) {
            String step = values[0];
            String message = values[1];
            String type = values[2];
            boolean success = "success".equals(type);
            
            Log.d(TAG, "[" + step + "] " + message);
            
            if (listener != null) {
                listener.onTestResult(step, message, success);
            }
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            if (listener != null) {
                listener.onTestComplete(success);
            }
        }
    }
    
    /**
     * Quick test method - call this from your activity
     */
    public static void quickTest(String username, String password) {
        AndroidLoginTester tester = new AndroidLoginTester(new LoginTestListener() {
            @Override
            public void onTestResult(String step, String result, boolean success) {
                Log.d(TAG, "[STEP " + step + "] " + result + " (" + (success ? "✓" : "✗") + ")");
            }
            
            @Override
            public void onTestComplete(boolean overallSuccess) {
                Log.d(TAG, "=== LOGIN TEST COMPLETE: " + (overallSuccess ? "SUCCESS" : "FAILED") + " ===");
            }
        });
        
        tester.runLoginTest(username, password);
    }
}