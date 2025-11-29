package com.example.smartworks;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WiFiConfigActivity extends AppCompatActivity {
    private static final String TAG = "WiFiConfigActivity";

    private TextView currentSSIDText;
    private TextView scanningText;
    private RadioGroup networksRadioGroup;
    private RadioButton manualEntryRadio;
    private EditText manualSSIDEdit;
    private EditText passwordEdit;
    private CheckBox showPasswordCheckbox;
    private Button refreshNetworksButton;
    private Button applySettingsButton;

    private String deviceName;
    private String deviceIP;
    private String currentSSID;
    private List<NetworkInfo> availableNetworks;
    private String selectedSSID = null;

    private static class NetworkInfo {
        String ssid;
        int rssi;
        String capabilities;
        
        NetworkInfo(String ssid, int rssi, String capabilities) {
            this.ssid = ssid;
            this.rssi = rssi;
            this.capabilities = capabilities;
        }
        
        boolean is24GHz() {
            // Most networks without 5G in name are 2.4GHz
            // This is a heuristic - could be improved with frequency info
            return !ssid.toLowerCase().contains("5g") && 
                   !ssid.toLowerCase().contains("_5g") &&
                   !ssid.toLowerCase().contains("-5g");
        }
        
        boolean isOpen() {
            return capabilities == null || capabilities.contains("[ESS]") && 
                   !capabilities.contains("WPA") && !capabilities.contains("WEP");
        }
        
        String getSecurityType() {
            if (capabilities == null) return "Unknown";
            if (capabilities.contains("WPA3")) return "WPA3";
            if (capabilities.contains("WPA2")) return "WPA2";
            if (capabilities.contains("WPA")) return "WPA";
            if (capabilities.contains("WEP")) return "WEP";
            return "Open";
        }
        
        String getSignalStrength() {
            if (rssi >= -50) return "Excellent";
            if (rssi >= -60) return "Good";
            if (rssi >= -70) return "Fair";
            if (rssi >= -80) return "Weak";
            return "Very Weak";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_config);

        // Setup action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("WiFi Settings");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Get device data from intent
        Intent intent = getIntent();
        deviceName = intent.getStringExtra("device_name");
        deviceIP = intent.getStringExtra("device_ip");
        currentSSID = intent.getStringExtra("current_ssid");

        initializeViews();
        setupClickListeners();
        
        // Start scanning immediately
        scanForNetworks();
    }

    private void initializeViews() {
        currentSSIDText = findViewById(R.id.currentSSIDText);
        scanningText = findViewById(R.id.scanningText);
        networksRadioGroup = findViewById(R.id.networksRadioGroup);
        manualEntryRadio = findViewById(R.id.manualEntryRadio);
        manualSSIDEdit = findViewById(R.id.manualSSIDEdit);
        passwordEdit = findViewById(R.id.passwordEdit);
        showPasswordCheckbox = findViewById(R.id.showPasswordCheckbox);
        refreshNetworksButton = findViewById(R.id.refreshNetworksButton);
        applySettingsButton = findViewById(R.id.applySettingsButton);

        // Set current SSID
        currentSSIDText.setText("Current: " + (currentSSID != null ? currentSSID : "Unknown"));
        
        availableNetworks = new ArrayList<>();
    }

    private void setupClickListeners() {
        refreshNetworksButton.setOnClickListener(v -> scanForNetworks());
        applySettingsButton.setOnClickListener(v -> applyWiFiSettings());
        
        // Handle manual entry toggle
        manualEntryRadio.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                manualSSIDEdit.setVisibility(View.VISIBLE);
                selectedSSID = null; // Will be set from manual entry
                // Clear other radio buttons
                for (int i = 0; i < networksRadioGroup.getChildCount(); i++) {
                    View child = networksRadioGroup.getChildAt(i);
                    if (child instanceof RadioButton) {
                        ((RadioButton) child).setChecked(false);
                    }
                }
                updateApplyButton();
            } else {
                manualSSIDEdit.setVisibility(View.GONE);
            }
        });
        
        // Handle show/hide password
        showPasswordCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                passwordEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                passwordEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            passwordEdit.setSelection(passwordEdit.getText().length()); // Move cursor to end
        });
        
        // Handle network selection from radio group
        networksRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId != -1) {
                manualEntryRadio.setChecked(false);
                manualSSIDEdit.setVisibility(View.GONE);
                
                // Find selected network
                RadioButton selectedRadio = findViewById(checkedId);
                if (selectedRadio != null) {
                    selectedSSID = selectedRadio.getTag().toString();
                    Log.d(TAG, "Selected network: " + selectedSSID);
                }
                updateApplyButton();
            }
        });
        
        // Monitor manual SSID entry
        manualSSIDEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateApplyButton();
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void scanForNetworks() {
        scanningText.setVisibility(View.VISIBLE);
        scanningText.setText("🔍 Scanning for 2.4GHz networks...");
        applySettingsButton.setEnabled(false);
        
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                // Start WiFi scan
                boolean scanStarted = wifiManager.startScan();

                if (scanStarted) {
                    // Wait a moment for scan to complete, then get results
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            List<ScanResult> scanResults = wifiManager.getScanResults();
                            processScanResults(scanResults);
                        } catch (Exception e) {
                            Log.e(TAG, "Error getting scan results", e);
                            showScanError("Error getting scan results: " + e.getMessage());
                        }
                    }, 3000); // Wait 3 seconds for scan to complete
                } else {
                    showScanError("Failed to start WiFi scan");
                }
            } else {
                showScanError("WiFi is not enabled");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scanning for networks", e);
            showScanError("Error scanning: " + e.getMessage());
        }
    }

    private void processScanResults(List<ScanResult> scanResults) {
        availableNetworks.clear();
        Set<String> seenSSIDs = new HashSet<>();
        
        // Process scan results
        for (ScanResult result : scanResults) {
            if (result.SSID != null && !result.SSID.isEmpty() && !seenSSIDs.contains(result.SSID)) {
                NetworkInfo network = new NetworkInfo(result.SSID, result.level, result.capabilities);
                
                // Filter for 2.4GHz networks (heuristic)
                if (network.is24GHz()) {
                    availableNetworks.add(network);
                    seenSSIDs.add(result.SSID);
                }
            }
        }
        
        // Sort by signal strength (strongest first)
        availableNetworks.sort((a, b) -> Integer.compare(b.rssi, a.rssi));
        
        displayNetworks();
    }
    
    private void displayNetworks() {
        networksRadioGroup.removeAllViews();
        
        if (availableNetworks.isEmpty()) {
            scanningText.setText("⚠️ No 2.4GHz networks found. Use manual entry below.");
            scanningText.setVisibility(View.VISIBLE);
        } else {
            scanningText.setVisibility(View.GONE);
            
            for (NetworkInfo network : availableNetworks) {
                RadioButton radioButton = new RadioButton(this);
                radioButton.setTag(network.ssid);
                radioButton.setTextSize(14);
                radioButton.setPadding(8, 12, 8, 12);
                radioButton.setTextColor(0xFF333333);
                
                // Create descriptive text
                String displayText = network.ssid;
                if (network.ssid.equals(currentSSID)) {
                    displayText += " (current)";
                }
                displayText += "\n";
                displayText += "📶 " + network.getSignalStrength() + " • " + network.getSecurityType();
                
                radioButton.setText(displayText);
                
                // Highlight current network
                if (network.ssid.equals(currentSSID)) {
                    radioButton.setBackgroundColor(0xFFE8F5E8);
                }
                
                networksRadioGroup.addView(radioButton);
            }
            
            Toast.makeText(this, "Found " + availableNetworks.size() + " 2.4GHz networks", Toast.LENGTH_SHORT).show();
        }
        
        updateApplyButton();
    }
    
    private void showScanError(String message) {
        scanningText.setText("⚠️ " + message + ". Use manual entry below.");
        scanningText.setVisibility(View.VISIBLE);
        updateApplyButton();
    }
    
    private void updateApplyButton() {
        boolean hasSelection = (networksRadioGroup.getCheckedRadioButtonId() != -1) || 
                              (manualEntryRadio.isChecked() && !manualSSIDEdit.getText().toString().trim().isEmpty());
        applySettingsButton.setEnabled(hasSelection);
    }

    private void applyWiFiSettings() {
        String networkSSID;
        String password = passwordEdit.getText().toString().trim();
        
        if (manualEntryRadio.isChecked()) {
            networkSSID = manualSSIDEdit.getText().toString().trim();
            if (networkSSID.isEmpty()) {
                Toast.makeText(this, "Please enter a network name", Toast.LENGTH_SHORT).show();
                return;
            }
        } else if (selectedSSID != null) {
            networkSSID = selectedSSID;
        } else {
            Toast.makeText(this, "Please select a network", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Remove " (current)" suffix if present
        networkSSID = networkSSID.replace(" (current)", "");
        
        // Make variables final for lambda expressions
        final String finalSSID = networkSSID;
        final String finalPassword = password;
        
        Log.d(TAG, "Applying WiFi settings: SSID=" + finalSSID + ", hasPassword=" + !finalPassword.isEmpty());

        if (finalPassword.isEmpty()) {
            // Show confirmation for open network
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Open Network")
                    .setMessage("This network appears to be open (no password). Continue?")
                    .setPositiveButton("Continue", (dialog, which) -> sendWiFiConfig(finalSSID, finalPassword))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            sendWiFiConfig(finalSSID, finalPassword);
        }
    }

    private void sendWiFiConfig(String ssid, String password) {
        // Show progress dialog
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setTitle("Updating WiFi Settings");
        progressDialog.setMessage("Sending new WiFi configuration to " + deviceName + "...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Send WiFi configuration to ESP32 device
        new Thread(() -> {
            boolean success = sendWiFiConfigToESP32(ssid, password);

            runOnUiThread(() -> {
                progressDialog.dismiss();

                if (success) {
                    Toast.makeText(this, "WiFi settings updated successfully!\nDevice may restart to apply changes.", Toast.LENGTH_LONG).show();

                    // Show warning about connection
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("WiFi Updated")
                            .setMessage("The device WiFi settings have been updated. The device may restart and may take a few minutes to reconnect to the new network.\n\nPlease wait and refresh the device list if needed.")
                            .setPositiveButton("OK", (dialog, which) -> finish())
                            .show();
                } else {
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Update Failed")
                            .setMessage("Failed to update WiFi settings. Please check:\n• Device is powered on\n• Device is accessible\n• Network credentials are correct")
                            .setPositiveButton("Retry", (dialog, which) -> sendWiFiConfig(ssid, password))
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });
        }).start();
    }

    private boolean sendWiFiConfigToESP32(String ssid, String password) {
        try {
            if (deviceIP == null || deviceIP.isEmpty()) {
                Log.e(TAG, "Device IP is not available");
                return false;
            }

            // Create the WiFi configuration JSON
            String configJson = String.format(
                    "{\"wifi_ssid\":\"%s\",\"wifi_password\":\"%s\",\"command\":\"update_wifi\"}",
                    ssid, password
            );

            Log.d(TAG, "Sending WiFi config to " + deviceIP + ": " + configJson);

            // Send POST request to ESP32
            java.net.URL url = new java.net.URL("http://" + deviceIP + "/config");
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);

            // Send JSON data
            try (java.io.OutputStream os = connection.getOutputStream()) {
                byte[] input = configJson.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "WiFi config response code: " + responseCode);

            if (responseCode == 200) {
                // Read response
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(connection.getInputStream()))) {
                    String response = reader.readLine();
                    Log.d(TAG, "WiFi config response: " + response);
                    return response != null && response.contains("success");
                }
            }

            connection.disconnect();
            return false;

        } catch (Exception e) {
            Log.e(TAG, "Error sending WiFi config to ESP32", e);
            return false;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
