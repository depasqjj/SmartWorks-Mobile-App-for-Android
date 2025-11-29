package com.example.smartworks.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.smartworks.MainActivity;
import com.example.smartworks.R;
import com.example.smartworks.debug.AndroidLoginTester;
import com.example.smartworks.debug.SessionDebugger;

/**
 * Login Activity for SmartWorks App - Safe Version
 */
public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    
    private EditText etUsername;
    private EditText etPassword;
    private Button btnLogin;
    private Button btnRegister;
    private TextView tvForgotPassword;
    private ProgressBar progressBar;
    private android.widget.CheckBox cbRememberMe;
    
    private AuthenticationManager authManager;
    private static final String PREFS_REMEMBER = "RememberMePrefs";
    private static final String KEY_REMEMBER_ME = "remember_me";
    private static final String KEY_SAVED_USERNAME = "saved_username";
    private static final String KEY_SAVED_PASSWORD = "saved_password";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "LoginActivity onCreate started");
        
        // Debug: Log current session state
        SessionDebugger.logSessionData(this);
        
        try {
            setContentView(R.layout.activity_login);
            Log.d(TAG, "Layout set successfully");
            
            // Initialize views first
            initializeViews();
            Log.d(TAG, "Views initialized");
            
            // Then initialize AuthenticationManager
            authManager = AuthenticationManager.getInstance(this);
            Log.d(TAG, "AuthenticationManager initialized");
            
            // Check if already logged in (but only after views are ready)
            if (authManager.isLoggedIn()) {
                Log.d(TAG, "User already logged in, validating session");
                validateAndProceed();
            } else {
                Log.d(TAG, "User not logged in, showing login form");
                setupListeners();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error initializing login screen: " + e.getMessage(), Toast.LENGTH_LONG).show();
            
            // Try to initialize basic views at least
            try {
                initializeViews();
                setupListeners();
                Toast.makeText(this, "Login screen in fallback mode", Toast.LENGTH_SHORT).show();
            } catch (Exception fallbackError) {
                Log.e(TAG, "Even fallback initialization failed", fallbackError);
                finish();
            }
        }
    }
    
    private void initializeViews() {
        try {
            etUsername = findViewById(R.id.etUsername);
            etPassword = findViewById(R.id.etPassword);
            btnLogin = findViewById(R.id.btnLogin);
            btnRegister = findViewById(R.id.btnRegister);
            tvForgotPassword = findViewById(R.id.tvForgotPassword);
            progressBar = findViewById(R.id.progressBar);
            cbRememberMe = findViewById(R.id.cbRememberMe);
            
            // Check if all views were found
            if (etUsername == null || etPassword == null || btnLogin == null || 
                btnRegister == null || tvForgotPassword == null || progressBar == null || cbRememberMe == null) {
                throw new RuntimeException("Some views not found in layout");
            }
            
            // Load saved credentials if Remember Me was checked
            loadSavedCredentials();
            
            // Set up action bar
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("SmartWorks Login");
            }
            
            Log.d(TAG, "All views initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
            throw e;
        }
    }
    
    private void setupListeners() {
        try {
            btnLogin.setOnClickListener(v -> {
                Log.d(TAG, "Login button clicked");
                performLogin();
            });
            
            // Add debug button - Long press login button for debug test
            btnLogin.setOnLongClickListener(v -> {
                Log.d(TAG, "Debug login test triggered");
                String username = etUsername.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                
                if (!username.isEmpty() && !password.isEmpty()) {
                    Toast.makeText(this, "Running debug login test... Check logs", Toast.LENGTH_LONG).show();
                    
                    // Run both tests
                    AndroidLoginTester.quickTest(username, password);
                    
                    // Also test the actual AuthManager directly
                    Log.d(TAG, "Testing AuthManager login directly...");
                    authManager.login(username, password)
                        .thenAccept(result -> {
                            Log.d(TAG, "AuthManager test result: success=" + result.success + ", message=" + result.message);
                        })
                        .exceptionally(throwable -> {
                            Log.e(TAG, "AuthManager test failed", throwable);
                            return null;
                        });
                } else {
                    Toast.makeText(this, "Enter username and password first", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
            
            btnRegister.setOnClickListener(v -> {
                Log.d(TAG, "Register button clicked");
                showRegistrationActivity();
            });
            
            tvForgotPassword.setOnClickListener(v -> {
                Log.d(TAG, "Forgot password clicked");
                showForgotPasswordDialog();
            });
            
            // Handle enter key in password field
            etPassword.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    performLogin();
                    return true;
                }
                return false;
            });
            
            Log.d(TAG, "Listeners set up successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up listeners", e);
        }
    }
    
    private void validateAndProceed() {
        try {
            showProgress(true);
            
            authManager.validateSession()
                .thenAccept(valid -> runOnUiThread(() -> {
                    try {
                        showProgress(false);
                        if (valid) {
                            Log.d(TAG, "Session valid, proceeding to main activity");
                            proceedToMainActivity();
                        } else {
                            Log.d(TAG, "Session invalid, showing login form");
                            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
                            setupListeners(); // Enable login form
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in session validation callback", e);
                        showProgress(false);
                        setupListeners();
                    }
                }))
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        try {
                            showProgress(false);
                            Log.e(TAG, "Session validation failed", throwable);
                            Toast.makeText(this, "Unable to validate session", Toast.LENGTH_SHORT).show();
                            setupListeners();
                        } catch (Exception e) {
                            Log.e(TAG, "Error in session validation exception handler", e);
                        }
                    });
                    return null;
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Error starting session validation", e);
            showProgress(false);
            setupListeners();
        }
    }
    
    private void performLogin() {
        try {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            
            Log.d(TAG, "Attempting login for user: " + username);
            
            if (TextUtils.isEmpty(username)) {
                etUsername.setError("Username is required");
                etUsername.requestFocus();
                return;
            }
            
            if (TextUtils.isEmpty(password)) {
                etPassword.setError("Password is required");
                etPassword.requestFocus();
                return;
            }
            
            if (password.length() < 3) { // Reduced requirement for demo
                etPassword.setError("Password must be at least 3 characters");
                etPassword.requestFocus();
                return;
            }
            
            showProgress(true);
            
            authManager.login(username, password)
                .thenAccept(result -> runOnUiThread(() -> {
                    try {
                        showProgress(false);
                        
                        if (result.success) {
                            Log.d(TAG, "=== LOGIN SUCCESS DEBUG ===");
                            Log.d(TAG, "Login result: " + result.message);
                            Log.d(TAG, "User data: " + (result.user != null ? result.user.username : "null"));
                            Log.d(TAG, "API Key: " + (result.user != null ? result.user.apiKey : "null"));
                            Log.d(TAG, "AuthManager isLoggedIn BEFORE save: " + authManager.isLoggedIn());
                            
                            // Save credentials if Remember Me is checked
                            saveCredentialsIfRemembered(username, password);
                            
                            // Debug: Log session data after login success
                            SessionDebugger.logSessionData(this);
                            
                            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
                            
                            // Add a small delay to ensure data is saved
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                Log.d(TAG, "AuthManager isLoggedIn AFTER delay: " + authManager.isLoggedIn());
                                Log.d(TAG, "Current user: " + (authManager.getCurrentUser() != null ? authManager.getCurrentUser().username : "null"));
                                Log.d(TAG, "About to call proceedToMainActivity()");
                                proceedToMainActivity();
                            }, 1000); // 1 second delay
                        } else {
                            Log.d(TAG, "=== LOGIN FAILED DEBUG ===");
                            Log.d(TAG, "Login failed: " + result.message);
                            handleLoginError(result.message);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in login callback", e);
                        showProgress(false);
                        Toast.makeText(this, "Error processing login result", Toast.LENGTH_SHORT).show();
                    }
                }))
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        try {
                            showProgress(false);
                            Log.e(TAG, "Login failed with exception", throwable);
                            
                            // Enhanced error handling
                            String errorMsg = "Login failed";
                            if (throwable != null && throwable.getMessage() != null) {
                                errorMsg += ": " + throwable.getMessage();
                            } else {
                                errorMsg += " - please check your internet connection";
                            }
                            
                            Log.d(TAG, "Final error message: " + errorMsg);
                            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Log.e(TAG, "Error in login exception handler", e);
                        }
                    });
                    return null;
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Error starting login process", e);
            showProgress(false);
            Toast.makeText(this, "Error during login: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void handleLoginError(String errorMessage) {
        try {
            // Fix: Handle null or empty error messages
            String displayMessage = errorMessage;
            if (displayMessage == null || displayMessage.trim().isEmpty()) {
                displayMessage = "Login failed - please check your connection and try again";
            }
            Log.d(TAG, "Handling login error: " + displayMessage);
            Toast.makeText(this, displayMessage, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Error handling login error", e);
            // Fallback error display
            try {
                Toast.makeText(this, "Login failed - please try again", Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                Log.e(TAG, "Even fallback toast failed", e2);
            }
        }
    }
    
    private void showRegistrationActivity() {
        try {
            Intent intent = new Intent(this, RegisterActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting registration activity", e);
            Toast.makeText(this, "Registration not available", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showForgotPasswordDialog() {
        try {
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
            builder.setTitle("Reset Password");
            builder.setMessage("Enter your email address to receive password reset instructions:");
            
            // Create input layout
            android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            layout.setPadding(50, 40, 50, 10);
            
            final android.widget.EditText emailInput = new android.widget.EditText(this);
            emailInput.setHint("Enter your email address");
            emailInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            layout.addView(emailInput);
            
            builder.setView(layout);
            
            builder.setPositiveButton("Send Reset Email", (dialog, which) -> {
                String email = emailInput.getText().toString().trim();
                
                if (TextUtils.isEmpty(email)) {
                    Toast.makeText(this, "Please enter your email address", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                performPasswordReset(email);
            });
            
            builder.setNegativeButton("Cancel", null);
            builder.show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing forgot password dialog", e);
            Toast.makeText(this, "Unable to show password reset dialog", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void performPasswordReset(String email) {
        try {
            showProgress(true);
            
            authManager.requestPasswordReset(email)
                .thenAccept(result -> runOnUiThread(() -> {
                    try {
                        showProgress(false);
                        
                        if (result.success) {
                            showPasswordResetSuccessDialog(email);
                        } else {
                            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in password reset callback", e);
                        showProgress(false);
                    }
                }))
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        try {
                            showProgress(false);
                            Log.e(TAG, "Password reset failed with exception", throwable);
                            Toast.makeText(this, "Password reset failed: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Log.e(TAG, "Error in password reset exception handler", e);
                        }
                    });
                    return null;
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Error starting password reset", e);
            showProgress(false);
            Toast.makeText(this, "Error during password reset: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showPasswordResetSuccessDialog(String email) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Password Reset Sent")
            .setMessage("Password reset instructions have been sent to " + email + ". Please check your email and follow the instructions to reset your password.")
            .setPositiveButton("OK", null)
            .show();
    }
    
    private void proceedToMainActivity() {
        try {
            Log.d(TAG, "Proceeding to MainActivity");
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error proceeding to main activity", e);
            Toast.makeText(this, "Error opening main screen", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showProgress(boolean show) {
        try {
            if (progressBar != null) {
                if (show) {
                    progressBar.setVisibility(View.VISIBLE);
                    if (btnLogin != null) btnLogin.setEnabled(false);
                    if (btnRegister != null) btnRegister.setEnabled(false);
                    if (etUsername != null) etUsername.setEnabled(false);
                    if (etPassword != null) etPassword.setEnabled(false);
                } else {
                    progressBar.setVisibility(View.GONE);
                    if (btnLogin != null) btnLogin.setEnabled(true);
                    if (btnRegister != null) btnRegister.setEnabled(true);
                    if (etUsername != null) etUsername.setEnabled(true);
                    if (etPassword != null) etPassword.setEnabled(true);
                }
                Log.d(TAG, "Progress visibility set to: " + (show ? "VISIBLE" : "GONE"));
            } else {
                Log.w(TAG, "ProgressBar is null, cannot show progress");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing progress", e);
        }
    }
    
    /**
     * Save credentials to SharedPreferences if Remember Me is checked
     */
    private void saveCredentialsIfRemembered(String username, String password) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS_REMEMBER, MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            
            if (cbRememberMe.isChecked()) {
                editor.putBoolean(KEY_REMEMBER_ME, true);
                editor.putString(KEY_SAVED_USERNAME, username);
                editor.putString(KEY_SAVED_PASSWORD, password);
                Log.d(TAG, "Credentials saved for Remember Me");
            } else {
                // If not checked, clear any previously saved credentials
                editor.putBoolean(KEY_REMEMBER_ME, false);
                editor.remove(KEY_SAVED_USERNAME);
                editor.remove(KEY_SAVED_PASSWORD);
                Log.d(TAG, "Remember Me unchecked, cleared saved credentials");
            }
            
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving credentials", e);
        }
    }
    
    /**
     * Load saved credentials from SharedPreferences
     */
    private void loadSavedCredentials() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS_REMEMBER, MODE_PRIVATE);
            boolean rememberMe = prefs.getBoolean(KEY_REMEMBER_ME, false);
            
            if (rememberMe) {
                String savedUsername = prefs.getString(KEY_SAVED_USERNAME, "");
                String savedPassword = prefs.getString(KEY_SAVED_PASSWORD, "");
                
                if (!savedUsername.isEmpty()) {
                    etUsername.setText(savedUsername);
                    Log.d(TAG, "Loaded saved username");
                }
                
                if (!savedPassword.isEmpty()) {
                    etPassword.setText(savedPassword);
                    Log.d(TAG, "Loaded saved password");
                }
                
                cbRememberMe.setChecked(true);
                Log.d(TAG, "Remember Me was previously checked, credentials loaded");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading saved credentials", e);
        }
    }
    
    /**
     * Public static method to prefill credentials on logout
     * This should be called by MainActivity when user logs out
     */
    public static void prefillCredentialsOnLogout(android.content.Context context, String username) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_REMEMBER, MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            
            // Save the username for prefill (but don't enable Remember Me)
            editor.putString(KEY_SAVED_USERNAME, username);
            editor.putBoolean(KEY_REMEMBER_ME, false); // Don't auto-login
            editor.remove(KEY_SAVED_PASSWORD); // Clear password for security
            editor.apply();
            
            Log.d(TAG, "Username saved for prefill on next login");
        } catch (Exception e) {
            Log.e("LoginActivity", "Error saving prefill credentials", e);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "LoginActivity destroyed");
    }
    
    @Override
    public void onBackPressed() {
        try {
            // Prevent going back from login screen
            finishAffinity();
        } catch (Exception e) {
            Log.e(TAG, "Error handling back press", e);
            super.onBackPressed();
        }
    }
}