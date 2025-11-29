package com.example.smartworks;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class DeviceConfigActivity extends AppCompatActivity {
    private static final String TAG = "DeviceConfigActivity";

    // UI Components
    private EditText deviceNameEdit;
    private TextView currentVersionText;
    private TextView displayTypeText;
    private Spinner locationSpinner;
    private Switch deviceOnOffSwitch;
    private EditText highTempEdit;
    private EditText lowTempEdit;
    private Spinner powerOnStateSpinner;
    private Spinner tempUnitsSpinner;
    private Button wifiSettingsButton;
    private Button saveButton;
    private Button deleteDeviceButton;

    // Device data
    private String deviceName;
    private String deviceAddress;
    private String deviceIP;
    private String wifiSSID;
    private int devicePosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_config);

        // Setup action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Device Configuration");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Get device data from intent
        Intent intent = getIntent();
        deviceName = intent.getStringExtra("device_name");
        deviceAddress = intent.getStringExtra("device_address");
        deviceIP = intent.getStringExtra("device_ip");
        wifiSSID = intent.getStringExtra("wifi_ssid");
        devicePosition = intent.getIntExtra("device_position", -1);

        initializeViews();
        setupSpinners();
        loadDeviceSettings();
        setupClickListeners();
    }

    private void initializeViews() {
        deviceNameEdit = findViewById(R.id.deviceNameEdit);
        currentVersionText = findViewById(R.id.currentVersionText);
        displayTypeText = findViewById(R.id.displayTypeText);
        locationSpinner = findViewById(R.id.locationSpinner);
        deviceOnOffSwitch = findViewById(R.id.deviceOnOffSwitch);
        highTempEdit = findViewById(R.id.highTempEdit);
        lowTempEdit = findViewById(R.id.lowTempEdit);
        powerOnStateSpinner = findViewById(R.id.powerOnStateSpinner);
        tempUnitsSpinner = findViewById(R.id.tempUnitsSpinner);
        wifiSettingsButton = findViewById(R.id.wifiSettingsButton);
        saveButton = findViewById(R.id.saveButton);
        deleteDeviceButton = findViewById(R.id.deleteDeviceButton);
    }

    private void setupSpinners() {
        // Location spinner
        String[] locations = {
                "Kitchen", "Living Room", "Dining Room", "Family Room",
                "Bedroom", "Garage", "Exterior Building", "Other"
        };
        ArrayAdapter<String> locationAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, locations);
        locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        locationSpinner.setAdapter(locationAdapter);

        // Power on state spinner
        String[] powerStates = {"On", "Off", "Last State"};
        ArrayAdapter<String> powerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, powerStates);
        powerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        powerOnStateSpinner.setAdapter(powerAdapter);

        // Temperature units spinner
        String[] tempUnits = {"Fahrenheit", "Celsius"};
        ArrayAdapter<String> tempAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, tempUnits);
        tempAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tempUnitsSpinner.setAdapter(tempAdapter);
    }

    private void loadDeviceSettings() {
        // Set device name
        deviceNameEdit.setText(deviceName);

        // Set current version
        currentVersionText.setText("0.0.4");

        // Set display type
        displayTypeText.setText("Thermostat");

        // Load saved preferences
        SharedPreferences prefs = getSharedPreferences("SmartWorks_" + deviceName, Context.MODE_PRIVATE);
        
        Log.d(TAG, "Loading preferences for device: " + deviceName);
        logAllPreferences(prefs);

        // Set location
        String savedLocation = prefs.getString("location", "Other");
        setSpinnerSelection(locationSpinner, savedLocation);

        // Set device on/off
        deviceOnOffSwitch.setChecked(prefs.getBoolean("device_enabled", true));

        // Set temperature thresholds
        float highTemp = prefs.getFloat("high_temp", 85.0f);
        float lowTemp = prefs.getFloat("low_temp", 65.0f);
        
        Log.d(TAG, "Loading temperatures: high=" + highTemp + ", low=" + lowTemp);
        
        // Format temperature values properly (remove trailing .0 if integer)
        String highTempStr = (highTemp == (int)highTemp) ? String.valueOf((int)highTemp) : String.valueOf(highTemp);
        String lowTempStr = (lowTemp == (int)lowTemp) ? String.valueOf((int)lowTemp) : String.valueOf(lowTemp);
        
        highTempEdit.setText(highTempStr);
        lowTempEdit.setText(lowTempStr);
        
        // Add focus change listeners to clear fields when clicked
        highTempEdit.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                highTempEdit.setText(""); // Clear the field
            }
        });
        
        lowTempEdit.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                lowTempEdit.setText(""); // Clear the field
            }
        });

        // Set power on state
        String powerOnState = prefs.getString("power_on_state", "Last State");
        setSpinnerSelection(powerOnStateSpinner, powerOnState);

        // Set temperature units
        String tempUnits = prefs.getString("temp_units", "Fahrenheit");
        setSpinnerSelection(tempUnitsSpinner, tempUnits);
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
        int position = adapter.getPosition(value);
        if (position >= 0) {
            spinner.setSelection(position);
        }
    }

    private void setupClickListeners() {
        saveButton.setOnClickListener(v -> saveDeviceSettings());

        deleteDeviceButton.setOnClickListener(v -> showDeleteConfirmation());

        wifiSettingsButton.setOnClickListener(v -> openWiFiSettings());
    }

    private void saveDeviceSettings() {
        try {
            // Get values from UI
            String newDeviceName = deviceNameEdit.getText().toString().trim();
            String location = locationSpinner.getSelectedItem().toString();
            boolean deviceEnabled = deviceOnOffSwitch.isChecked();
            String highTempStr = highTempEdit.getText().toString().trim();
            String lowTempStr = lowTempEdit.getText().toString().trim();
            String powerOnState = powerOnStateSpinner.getSelectedItem().toString();
            String tempUnits = tempUnitsSpinner.getSelectedItem().toString();

            Log.d(TAG, "Saving settings: name=" + newDeviceName + ", highTemp=" + highTempStr + ", lowTemp=" + lowTempStr);

            // Validate inputs
            if (newDeviceName.isEmpty()) {
                Toast.makeText(this, "Device name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            // Handle temperature values - use defaults if empty
            float highTemp = 85.0f; // default
            float lowTemp = 65.0f;  // default
            
            try {
                if (!highTempStr.isEmpty()) {
                    highTemp = Float.parseFloat(highTempStr);
                }
                if (!lowTempStr.isEmpty()) {
                    lowTemp = Float.parseFloat(lowTempStr);
                }

                if (highTemp <= lowTemp) {
                    Toast.makeText(this, "High temperature must be greater than low temperature", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter valid temperature values", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.d(TAG, "Parsed temperatures: high=" + highTemp + ", low=" + lowTemp);

            // Save preferences using the original device name as key (not the new name)
            SharedPreferences prefs = getSharedPreferences("SmartWorks_" + deviceName, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            editor.putString("device_name", newDeviceName);
            editor.putString("location", location);
            editor.putBoolean("device_enabled", deviceEnabled);
            editor.putFloat("high_temp", highTemp);
            editor.putFloat("low_temp", lowTemp);
            editor.putString("power_on_state", powerOnState);
            editor.putString("temp_units", tempUnits);

            boolean success = editor.commit();

            Log.d(TAG, "Save result: " + success);
            
            if (success) {
                // Verify the save by reading back
                float savedHigh = prefs.getFloat("high_temp", -1);
                float savedLow = prefs.getFloat("low_temp", -1);
                Log.d(TAG, "Verification - saved high=" + savedHigh + ", low=" + savedLow);
                
                Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show();

                // If device name changed, update the main device list
                if (!newDeviceName.equals(deviceName)) {
                    updateDeviceNameInMainList(newDeviceName);
                }

                // Return to main activity
                finish();
            } else {
                Toast.makeText(this, "Failed to save settings", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error saving device settings", e);
            Toast.makeText(this, "Error saving settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateDeviceNameInMainList(String newDeviceName) {
        // Create intent with result data
        Intent resultIntent = new Intent();
        resultIntent.putExtra("updated_device_name", newDeviceName);
        resultIntent.putExtra("original_device_name", deviceName);
        resultIntent.putExtra("device_position", devicePosition);
        setResult(RESULT_OK, resultIntent);
    }

    private void showDeleteConfirmation() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Delete Device")
                .setMessage("Are you sure you want to delete '" + deviceName + "'? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteDevice())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteDevice() {
        // Show loading state
        deleteDeviceButton.setEnabled(false);
        deleteDeviceButton.setText("Deleting...");

        // Get API service
        com.example.smartworks.auth.AuthenticationManager authManager = com.example.smartworks.auth.AuthenticationManager.getInstance(this);
        com.example.smartworks.api.SmartWorksApiService apiService = com.example.smartworks.api.SmartWorksApiService.getInstance(authManager);

        // Call API to delete device
        // deviceAddress holds the device_id (e.g. ESP32-Pool-Monitor...)
        apiService.deleteDevice(deviceAddress).thenAccept(result -> {
            runOnUiThread(() -> {
                if (result.success) {
                    try {
                        // Remove device preferences locally
                        SharedPreferences prefs = getSharedPreferences("SmartWorks_" + deviceName, Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.clear();
                        editor.apply();

                        // Remove from main device list locally (optional but good for immediate feedback)
                        SharedPreferences mainPrefs = getSharedPreferences("SmartWorks", Context.MODE_PRIVATE);
                        java.util.Set<String> deviceSet = new java.util.HashSet<>(mainPrefs.getStringSet("provisioned_devices", new java.util.HashSet<>()));
                        deviceSet.removeIf(deviceString -> deviceString.startsWith(deviceName + "\n") || deviceString.equals(deviceName));
                        
                        SharedPreferences.Editor mainEditor = mainPrefs.edit();
                        mainEditor.putStringSet("provisioned_devices", deviceSet);
                        mainEditor.apply();

                        Toast.makeText(this, "Device deleted successfully", Toast.LENGTH_SHORT).show();

                        // Return to main activity with delete result
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("device_deleted", true);
                        resultIntent.putExtra("device_position", devicePosition);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error cleaning up local data", e);
                        // Still finish since server delete was successful
                        finish();
                    }
                } else {
                    deleteDeviceButton.setEnabled(true);
                    deleteDeviceButton.setText("Delete Device");
                    Toast.makeText(this, "Failed to delete: " + result.message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void openWiFiSettings() {
        // The current firmware does not support changing WiFi via HTTP for security/power reasons.
        // The user must factory reset and re-provision.
        new android.app.AlertDialog.Builder(this)
                .setTitle("Change WiFi Network")
                .setMessage("To change the WiFi network, you must reset the device:\n\n" +
                        "1. Hold the button on the device for 10 seconds until the LED flashes fast.\n" +
                        "2. Delete this device from the app.\n" +
                        "3. Go to the main screen and tap '+' to add it again.")
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void logAllPreferences(SharedPreferences prefs) {
        Log.d(TAG, "All preferences:");
        for (String key : prefs.getAll().keySet()) {
            Object value = prefs.getAll().get(key);
            Log.d(TAG, "  " + key + " = " + value);
        }
    }
}
