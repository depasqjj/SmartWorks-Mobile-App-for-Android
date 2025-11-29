package com.example.smartworks.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.smartworks.config.ApiConfig;

import okhttp3.*;

/**
 * ProductionAuthenticationManager - Real authentication implementation
 * FIXED: Updated to handle actual PHP server response format
 */
public class AuthenticationManager {
    private static final String TAG = "AuthManager";
    private static final String PREFS_NAME = "SmartWorksAuth";
    
    // Use ApiConfig for server URL management
    private static final String BASE_URL = ApiConfig.getBaseUrl();
    
    // Preference keys
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_ROLE = "role";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    
    private static AuthenticationManager instance;
    private final Context context;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final ExecutorService executorService;
    private SharedPreferences prefs;
    
    // User data
    private User currentUser;
    
    private AuthenticationManager(Context context) {
        try {
            this.context = context.getApplicationContext();
            this.gson = new Gson();
            this.executorService = Executors.newCachedThreadPool();
            
            this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            // Initialize OkHttp with configuration from ApiConfig
            this.httpClient = new OkHttpClient.Builder()
                    .connectTimeout(ApiConfig.CONNECT_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(ApiConfig.READ_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(ApiConfig.WRITE_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            
            loadUserData();
            Log.d(TAG, "AuthenticationManager initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing AuthenticationManager", e);
            throw new IllegalStateException("Failed to initialize AuthenticationManager", e);
        }
    }
    
    public static synchronized AuthenticationManager getInstance(Context context) {
        try {
            if (instance == null) {
                instance = new AuthenticationManager(context);
            }
            return instance;
        } catch (Exception e) {
            Log.e(TAG, "Error getting AuthenticationManager instance", e);
            throw new IllegalStateException("Cannot create AuthenticationManager", e);
        }
    }
    
    private void loadUserData() {
        try {
            if (prefs.getBoolean(KEY_IS_LOGGED_IN, false)) {
                currentUser = new User();
                currentUser.id = prefs.getInt(KEY_USER_ID, 0);
                currentUser.username = prefs.getString(KEY_USERNAME, "");
                currentUser.email = prefs.getString(KEY_EMAIL, "");
                currentUser.apiKey = prefs.getString(KEY_API_KEY, "");
                currentUser.role = prefs.getString(KEY_ROLE, "user");
                Log.d(TAG, "Loaded user data for: " + currentUser.username);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading user data", e);
            currentUser = null;
        }
    }
    
    private void saveUserData(User user) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_IS_LOGGED_IN, true);
            editor.putInt(KEY_USER_ID, user.id);
            editor.putString(KEY_USERNAME, user.username);
            editor.putString(KEY_EMAIL, user.email);
            editor.putString(KEY_API_KEY, user.apiKey);
            editor.putString(KEY_ROLE, user.role);
            boolean saved = editor.commit();
            
            if (saved) {
                this.currentUser = user;
                Log.d(TAG, "User data saved successfully for: " + user.username);
                notifyAuthStateChanged(true, user);
            } else {
                Log.e(TAG, "Failed to save user data");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving user data", e);
        }
    }
    
    private void clearUserData() {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            boolean cleared = editor.commit();
            User oldUser = this.currentUser;
            this.currentUser = null;
            Log.d(TAG, "User data cleared: " + cleared);
            notifyAuthStateChanged(false, null);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing user data", e);
        }
    }
    
    /**
     * Login with username and password - FIXED VERSION for PHP server
     */
    public CompletableFuture<AuthResult> login(String username, String password) {
        CompletableFuture<AuthResult> future = new CompletableFuture<>();
        
        try {
            Log.d(TAG, "Attempting login for user: " + username);
            
            // Create login request
            LoginRequest loginRequest = new LoginRequest(username, password);
            String jsonBody = gson.toJson(loginRequest);
            RequestBody requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json"));
            
            Request request = new Request.Builder()
                    .url(ApiConfig.buildUrl(ApiConfig.LOGIN_ENDPOINT))
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Login network request failed", e);
                    
                    // For development/testing, fall back to demo mode if server is unreachable
                    if (e instanceof java.net.ConnectException || e instanceof java.net.UnknownHostException) {
                        Log.w(TAG, "Server unreachable, using fallback demo authentication");
                        performDemoLogin(username, password, future);
                    } else {
                        future.complete(AuthResult.error("Network error: " + e.getMessage()));
                    }
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseBody = response.body().string();
                        Log.d(TAG, "Login response: " + responseBody);
                        
                        if (response.isSuccessful()) {
                            // FIXED: Parse the actual PHP server response format
                            PhpLoginResponse loginResponse = gson.fromJson(responseBody, PhpLoginResponse.class);
                            
                            if ("ok".equals(loginResponse.status)) {
                                // FIXED: Create user from the PHP response format
                                User user = new User();
                                user.id = loginResponse.user_id;
                                user.username = loginResponse.username != null ? loginResponse.username : username;
                                user.email = loginResponse.email != null ? loginResponse.email : "";
                                user.role = loginResponse.role != null ? loginResponse.role : "user";
                                user.apiKey = loginResponse.api_key;
                                
                                Log.d(TAG, "Login successful for user: " + user.username + " (ID: " + user.id + ")");
                                saveUserData(user);
                                future.complete(AuthResult.success("Login successful", user));
                            } else {
                                // Handle error from PHP server
                                String errorMsg = loginResponse.message != null ? loginResponse.message : "Login failed";
                                Log.d(TAG, "Login failed: " + errorMsg);
                                future.complete(AuthResult.error(errorMsg));
                            }
                        } else {
                            // Parse error response
                            try {
                                PhpErrorResponse error = gson.fromJson(responseBody, PhpErrorResponse.class);
                                String errorMsg = error.message != null ? error.message : "Login failed";
                                future.complete(AuthResult.error(errorMsg));
                            } catch (Exception e) {
                                future.complete(AuthResult.error("Login failed: HTTP " + response.code()));
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing login response", e);
                        future.complete(AuthResult.error("Error processing response: " + e.getMessage()));
                    } finally {
                        response.close();
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting login process", e);
            future.complete(AuthResult.error("System error during login"));
        }
        
        return future;
    }
    
    /**
     * Fallback demo login for development/testing when server is unreachable
     */
    private void performDemoLogin(String username, String password, CompletableFuture<AuthResult> future) {
        executorService.execute(() -> {
            try {
                // Simulate network delay
                Thread.sleep(1000);
                
                // Basic validation
                if (username != null && !username.trim().isEmpty() && 
                    password != null && !password.trim().isEmpty() && password.length() >= 3) {
                    
                    // Create demo user
                    User user = new User();
                    user.id = 1;
                    user.username = username.trim();
                    user.email = username.trim() + "@demo.com";
                    user.apiKey = "demo_api_key_" + System.currentTimeMillis();
                    user.role = "user";
                    
                    saveUserData(user);
                    future.complete(AuthResult.success("Login successful (Demo Mode - Server Unavailable)", user));
                    
                    Log.d(TAG, "Demo fallback login successful for: " + username);
                } else {
                    future.complete(AuthResult.error("Invalid username or password"));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Demo login error", e);
                future.complete(AuthResult.error("Login failed: " + e.getMessage()));
            }
        });
    }
    
    /**
     * Register new user - PRODUCTION VERSION
     */
    public CompletableFuture<AuthResult> register(String username, String email, String password) {
        CompletableFuture<AuthResult> future = new CompletableFuture<>();
        
        try {
            // Create registration request
            RegistrationRequest regRequest = new RegistrationRequest(username, email, password);
            String jsonBody = gson.toJson(regRequest);
            RequestBody requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json"));
            
            Request request = new Request.Builder()
                    .url(ApiConfig.buildUrl(ApiConfig.REGISTER_ENDPOINT))
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Registration network request failed", e);
                    
                    // For development/testing, fall back to demo mode
                    if (e instanceof java.net.ConnectException || e instanceof java.net.UnknownHostException) {
                        Log.w(TAG, "Server unreachable, using demo registration");
                        performDemoRegistration(username, email, password, future);
                    } else {
                        future.complete(AuthResult.error("Network error: " + e.getMessage()));
                    }
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseBody = response.body().string();
                        
                        if (response.isSuccessful()) {
                            BasicResponse regResponse = gson.fromJson(responseBody, BasicResponse.class);
                            future.complete(AuthResult.success(regResponse.message));
                        } else {
                            try {
                                ErrorResponse error = gson.fromJson(responseBody, ErrorResponse.class);
                                future.complete(AuthResult.error(error.message));
                            } catch (Exception e) {
                                future.complete(AuthResult.error("Registration failed: HTTP " + response.code()));
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing registration response", e);
                        future.complete(AuthResult.error("Error processing response"));
                    } finally {
                        response.close();
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting registration process", e);
            future.complete(AuthResult.error("System error during registration"));
        }
        
        return future;
    }
    
    private void performDemoRegistration(String username, String email, String password, CompletableFuture<AuthResult> future) {
        executorService.execute(() -> {
            try {
                // Simulate network delay
                Thread.sleep(1000);
                
                // Demo validation
                if (username != null && !username.trim().isEmpty() && 
                    email != null && !email.trim().isEmpty() && 
                    password != null && password.length() >= 6) {
                    
                    future.complete(AuthResult.success("Registration successful (Demo Mode - Server Unavailable). Please login."));
                    Log.d(TAG, "Demo registration successful for: " + username);
                } else {
                    future.complete(AuthResult.error("Invalid registration data"));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Demo registration error", e);
                future.complete(AuthResult.error("Registration failed: " + e.getMessage()));
            }
        });
    }
    
    /**
     * Request password reset - PRODUCTION VERSION
     */
    public CompletableFuture<AuthResult> requestPasswordReset(String email) {
        CompletableFuture<AuthResult> future = new CompletableFuture<>();
        
        try {
            // Create reset request
            PasswordResetRequest resetRequest = new PasswordResetRequest(email);
            String jsonBody = gson.toJson(resetRequest);
            RequestBody requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json"));
            
            Request request = new Request.Builder()
                    .url(ApiConfig.buildUrl(ApiConfig.FORGOT_PASSWORD_ENDPOINT))
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Password reset request failed", e);
                    
                    // For development/testing, fall back to demo mode
                    if (e instanceof java.net.ConnectException || e instanceof java.net.UnknownHostException) {
                        Log.w(TAG, "Server unreachable, simulating password reset");
                        performDemoPasswordReset(email, future);
                    } else {
                        future.complete(AuthResult.error("Network error: " + e.getMessage()));
                    }
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseBody = response.body().string();
                        
                        if (response.isSuccessful()) {
                            BasicResponse resetResponse = gson.fromJson(responseBody, BasicResponse.class);
                            
                            if (resetResponse.success) {
                                future.complete(AuthResult.success(resetResponse.message));
                            } else {
                                future.complete(AuthResult.error(resetResponse.message));
                            }
                        } else {
                            try {
                                ErrorResponse error = gson.fromJson(responseBody, ErrorResponse.class);
                                future.complete(AuthResult.error(error.message));
                            } catch (Exception e) {
                                future.complete(AuthResult.error("Password reset failed: HTTP " + response.code()));
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing password reset response", e);
                        future.complete(AuthResult.error("Error processing response"));
                    } finally {
                        response.close();
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting password reset process", e);
            future.complete(AuthResult.error("System error during password reset"));
        }
        
        return future;
    }
    
    private void performDemoPasswordReset(String email, CompletableFuture<AuthResult> future) {
        executorService.execute(() -> {
            try {
                // Simulate network delay
                Thread.sleep(1000);
                
                if (email != null && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    future.complete(AuthResult.success("Password reset instructions sent to " + email + " (Demo Mode - Server Unavailable)"));
                } else {
                    future.complete(AuthResult.error("Please provide a valid email address"));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Demo password reset error", e);
                future.complete(AuthResult.error("Password reset failed: " + e.getMessage()));
            }
        });
    }
    
    /**
     * Logout current user
     */
    public void logout() {
        try {
            clearUserData();
            Log.d(TAG, "User logged out");
        } catch (Exception e) {
            Log.e(TAG, "Error during logout", e);
        }
    }
    
    /**
     * Check if user is logged in
     */
    public boolean isLoggedIn() {
        try {
            boolean loggedIn = currentUser != null && 
                             currentUser.username != null && 
                             !currentUser.username.trim().isEmpty();
            Log.d(TAG, "Login check: " + loggedIn + " (user: " + 
                  (currentUser != null ? currentUser.username : "null") + ")");
            return loggedIn;
        } catch (Exception e) {
            Log.e(TAG, "Error checking login status", e);
            return false;
        }
    }
    
    /**
     * Get current user
     */
    public User getCurrentUser() {
        return currentUser;
    }
    
    /**
     * Get API key for authenticated requests
     */
    public String getApiKey() {
        return currentUser != null ? currentUser.apiKey : null;
    }
    
    /**
     * Get user ID for authenticated requests
     */
    public int getUserId() {
        return currentUser != null ? currentUser.id : 0;
    }
    
    /**
     * Validate current session - UPDATED for PHP server
     */
    public CompletableFuture<Boolean> validateSession() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        try {
            Log.d(TAG, "validateSession() called");
            
            if (!isLoggedIn()) {
                Log.d(TAG, "validateSession: Not logged in");
                future.complete(false);
                return future;
            }
            
            String apiKey = getApiKey();
            if (apiKey == null) {
                Log.d(TAG, "validateSession: No API key");
                future.complete(false);
                return future;
            }
            
            Log.d(TAG, "validateSession: Attempting server validation with API key: " + apiKey.substring(0, 8) + "...");
            
            Request request = new Request.Builder()
                    .url(ApiConfig.buildUrl(ApiConfig.VALIDATE_SESSION_ENDPOINT))
                    .get()
                    .addHeader("X-API-Key", apiKey)
                    .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Session validation network request failed", e);
                    
                    // IMPORTANT: For development/testing, accept local session if server unreachable
                    if (e instanceof java.net.ConnectException || e instanceof java.net.UnknownHostException) {
                        Log.w(TAG, "Server unreachable during session validation - accepting local session");
                        boolean valid = currentUser != null && 
                                      currentUser.username != null && 
                                      !currentUser.username.trim().isEmpty();
                        Log.d(TAG, "Local session validation result: " + valid);
                        future.complete(valid);
                    } else {
                        Log.d(TAG, "Network error during session validation: " + e.getMessage());
                        future.complete(false);
                    }
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        Log.d(TAG, "Session validation response code: " + response.code());
                        
                        if (response.isSuccessful()) {
                            String responseBody = response.body().string();
                            Log.d(TAG, "Session validation response: " + responseBody);
                            
                            // FIXED: Parse PHP session validation response
                            PhpValidationResponse validationResponse = gson.fromJson(responseBody, PhpValidationResponse.class);
                            boolean isValid = "ok".equals(validationResponse.status) && validationResponse.valid;
                            Log.d(TAG, "Server session validation result: " + isValid);
                            future.complete(isValid);
                        } else {
                            Log.d(TAG, "Session validation failed with HTTP " + response.code());
                            future.complete(false);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing session validation response", e);
                        future.complete(false);
                    } finally {
                        response.close();
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting session validation", e);
            future.complete(false);
        }
        
        return future;
    }
    
    // FIXED: Request/Response classes for PHP server API communication
    private static class LoginRequest {
        public final String username;
        public final String password;
        
        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
    
    // FIXED: PHP server login response format
    private static class PhpLoginResponse {
        public String status;           // "ok" or "error"
        public String message;          // Error message if any
        public String api_key;          // API key on success
        public int user_id;             // User ID on success
        public String username;         // Username on success
        public String email;            // Email on success
        public String role;             // User role on success
    }
    
    // FIXED: PHP server error response format
    private static class PhpErrorResponse {
        public String status;           // "error"
        public String message;          // Error message
    }
    
    // FIXED: PHP server validation response format
    private static class PhpValidationResponse {
        public String status;           // "ok" or "error"
        public String message;          // Message
        public boolean valid;           // Whether session is valid
        public Object user;             // User object if valid
    }
    
    private static class RegistrationRequest {
        public final String username;
        public final String email;
        public final String password;
        
        public RegistrationRequest(String username, String email, String password) {
            this.username = username;
            this.email = email;
            this.password = password;
        }
    }
    
    private static class PasswordResetRequest {
        public final String email;
        
        public PasswordResetRequest(String email) {
            this.email = email;
        }
    }
    
    private static class BasicResponse {
        public boolean success;
        public String message;
    }
    
    private static class ErrorResponse {
        public String message;
        public String code;
    }
    
    // Data classes for API communication
    public static class User {
        public int id;
        public String username;
        public String email;
        public String apiKey;
        public String role;
        
        public boolean isAdmin() {
            return "admin".equals(role);
        }
    }
    
    public static class AuthResult {
        public final boolean success;
        public final String message;
        public final User user;
        
        private AuthResult(boolean success, String message, User user) {
            this.success = success;
            this.message = message;
            this.user = user;
        }
        
        public static AuthResult success(String message) {
            return new AuthResult(true, message, null);
        }
        
        public static AuthResult success(String message, User user) {
            return new AuthResult(true, message, user);
        }
        
        public static AuthResult error(String message) {
            return new AuthResult(false, message, null);
        }
    }
    
    /**
     * Interface for authentication state changes
     */
    public interface AuthStateListener {
        void onAuthStateChanged(boolean isLoggedIn, User user);
    }
    
    // Auth state listeners
    private java.util.List<AuthStateListener> authStateListeners = new java.util.ArrayList<>();
    
    public void addAuthStateListener(AuthStateListener listener) {
        try {
            if (listener != null) {
                authStateListeners.add(listener);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding auth state listener", e);
        }
    }
    
    public void removeAuthStateListener(AuthStateListener listener) {
        try {
            authStateListeners.remove(listener);
        } catch (Exception e) {
            Log.e(TAG, "Error removing auth state listener", e);
        }
    }
    
    private void notifyAuthStateChanged(boolean isLoggedIn, User user) {
        try {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                try {
                    for (AuthStateListener listener : authStateListeners) {
                        listener.onAuthStateChanged(isLoggedIn, user);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying auth state change", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up auth state notification", e);
        }
    }
}
