// Improved ProvisionActivity.java with better permission handling
package com.example.smartworks;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProvisionActivity extends AppCompatActivity {
    private static final String TAG = "ProvisionActivity";
    private static final int WIFI_PERMISSION_REQUEST = 100;
    private static final int LOCATION_PERMISSION_REQUEST = 101;

    private String deviceName;
    private String deviceAddress;
    private BluetoothDevice bluetoothDevice;

    private WifiManager wifiManager;
    private ListView wifiNetworksList;
    private ProgressBar wifiProgressBar;
    private TextView statusText;
    private Button refreshButton;
    private Button provisionButton;

    // BLE Provisioning components
    private ProgressBar provisioningProgressBar;
    private TextView provisioningStatusText;
    private View wifiSelectionLayout;
    private View provisioningLayout;
    private BLEProvisioningManager bleManager;

    private ArrayAdapter<String> wifiAdapter;
    private List<ScanResult> availableNetworks;
    private ScanResult selectedNetwork;

    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provision);

        // Get device info from intent
        deviceName = getIntent().getStringExtra("device_name");
        deviceAddress = getIntent().getStringExtra("device_address");

        // Get Bluetooth device
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress);

        // Setup action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Provision: " + deviceName);
        }

        initializeViews();
        initializeBLE();

        // Check and request all permissions first
        if (checkAllPermissions()) {
            initializeWiFi();
        } else {
            requestAllPermissions();
        }
    }

    private void initializeViews() {
        // WiFi selection views
        wifiSelectionLayout = findViewById(R.id.wifiSelectionLayout);
        wifiNetworksList = findViewById(R.id.wifiNetworksList);
        wifiProgressBar = findViewById(R.id.wifiProgressBar);
        statusText = findViewById(R.id.wifiStatusText);
        refreshButton = findViewById(R.id.refreshWifiButton);
        provisionButton = findViewById(R.id.provisionButton);

        // Provisioning views
        provisioningLayout = findViewById(R.id.provisioningLayout);
        provisioningProgressBar = findViewById(R.id.provisioningProgressBar);
        provisioningStatusText = findViewById(R.id.provisioningStatusText);

        availableNetworks = new ArrayList<>();
        wifiAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice);
        wifiNetworksList.setAdapter(wifiAdapter);
        wifiNetworksList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        wifiNetworksList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < availableNetworks.size()) {
                    selectedNetwork = availableNetworks.get(position);
                    provisionButton.setEnabled(true);
                    provisionButton.setText("Provision with " + selectedNetwork.SSID);
                }
            }
        });

        refreshButton.setOnClickListener(v -> {
            if (checkAllPermissions()) {
                startWiFiScan();
            } else {
                requestAllPermissions();
            }
        });

        provisionButton.setOnClickListener(v -> showPasswordDialog());
        provisionButton.setEnabled(false);

        // Initially show WiFi selection, hide provisioning
        showWiFiSelection();
    }

    private boolean checkAllPermissions() {
        List<String> missingPermissions = new ArrayList<>();

        // Check WiFi permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCESS_WIFI_STATE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.CHANGE_WIFI_STATE);
        }

        // Check location permissions (required for WiFi scanning)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        // Check Bluetooth permissions for newer Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        Log.d(TAG, "Missing permissions: " + missingPermissions.toString());
        return missingPermissions.isEmpty();
    }

    private void requestAllPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        // WiFi permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_WIFI_STATE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CHANGE_WIFI_STATE);
        }

        // Location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        // Bluetooth permissions for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "Requesting permissions: " + permissionsToRequest.toString());
            requestPermissions(permissionsToRequest.toArray(new String[0]), WIFI_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == WIFI_PERMISSION_REQUEST) {
            boolean allGranted = true;
            List<String> deniedPermissions = new ArrayList<>();

            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    deniedPermissions.add(permissions[i]);
                }
            }

            if (allGranted) {
                Log.d(TAG, "All permissions granted");
                initializeWiFi();
            } else {
                Log.e(TAG, "Some permissions denied: " + deniedPermissions.toString());

                String message = "The following permissions are required:\n\n";
                for (String permission : deniedPermissions) {
                    switch (permission) {
                        case Manifest.permission.ACCESS_WIFI_STATE:
                            message += "• WiFi State Access (to check WiFi status)\n";
                            break;
                        case Manifest.permission.CHANGE_WIFI_STATE:
                            message += "• WiFi Control (to enable WiFi if needed)\n";
                            break;
                        case Manifest.permission.ACCESS_FINE_LOCATION:
                            message += "• Location Access (required for WiFi/BLE scanning)\n";
                            break;
                        case Manifest.permission.BLUETOOTH_SCAN:
                            message += "• Bluetooth Scan (to find ESP32 devices)\n";
                            break;
                        case Manifest.permission.BLUETOOTH_CONNECT:
                            message += "• Bluetooth Connect (to communicate with ESP32)\n";
                            break;
                    }
                }

                new AlertDialog.Builder(this)
                        .setTitle("Permissions Required")
                        .setMessage(message + "\nPlease grant these permissions to continue.")
                        .setPositiveButton("Grant Permissions", (dialog, which) -> requestAllPermissions())
                        .setNegativeButton("Cancel", (dialog, which) -> finish())
                        .show();
            }
        }
    }

    private void initializeWiFi() {
        try {
            wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

            if (wifiManager == null) {
                Toast.makeText(this, "WiFi not available on this device", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            // Check if WiFi is enabled
            if (!wifiManager.isWifiEnabled()) {
                new AlertDialog.Builder(this)
                        .setTitle("WiFi Disabled")
                        .setMessage("WiFi must be enabled to scan for networks. Enable WiFi now?")
                        .setPositiveButton("Enable", (dialog, which) -> {
                            try {
                                wifiManager.setWifiEnabled(true);
                                // Wait a moment for WiFi to enable, then scan
                                handler.postDelayed(() -> {
                                    if (checkAllPermissions()) {
                                        startWiFiScan();
                                    }
                                }, 2000);
                            } catch (SecurityException e) {
                                Log.e(TAG, "Permission denied when enabling WiFi", e);
                                Toast.makeText(this, "Permission denied when enabling WiFi", Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> finish())
                        .show();
            } else {
                // WiFi is enabled, start scanning
                startWiFiScan();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception in initializeWiFi", e);
            Toast.makeText(this, "WiFi permission error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            requestAllPermissions();
        }
    }

    private void initializeBLE() {
        bleManager = new BLEProvisioningManager(this);
    }

    private void showWiFiSelection() {
        wifiSelectionLayout.setVisibility(View.VISIBLE);
        provisioningLayout.setVisibility(View.GONE);
    }

    private void showProvisioning() {
        wifiSelectionLayout.setVisibility(View.GONE);
        provisioningLayout.setVisibility(View.VISIBLE);
    }

    private void startWiFiScan() {
        try {
            if (!wifiManager.isWifiEnabled()) {
                statusText.setText("WiFi is disabled. Please enable WiFi.");
                return;
            }

            wifiProgressBar.setVisibility(View.VISIBLE);
            statusText.setText("Scanning for 2.4GHz WiFi networks...");
            refreshButton.setEnabled(false);
            wifiAdapter.clear();
            availableNetworks.clear();
            selectedNetwork = null;
            provisionButton.setEnabled(false);

            // Register receiver for scan results
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            registerReceiver(wifiScanReceiver, intentFilter);

            // Start scan
            boolean success = wifiManager.startScan();
            if (!success) {
                statusText.setText("Failed to start WiFi scan");
                wifiProgressBar.setVisibility(View.GONE);
                refreshButton.setEnabled(true);
            }

            // Timeout after 10 seconds
            handler.postDelayed(() -> {
                try {
                    unregisterReceiver(wifiScanReceiver);
                } catch (IllegalArgumentException e) {
                    // Receiver not registered, ignore
                }
                if (availableNetworks.isEmpty()) {
                    statusText.setText("No 2.4GHz networks found. Tap refresh to try again.");
                    wifiProgressBar.setVisibility(View.GONE);
                    refreshButton.setEnabled(true);
                }
            }, 10000);

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception during WiFi scan", e);
            statusText.setText("Permission error during WiFi scan");
            wifiProgressBar.setVisibility(View.GONE);
            refreshButton.setEnabled(true);
            requestAllPermissions();
        }
    }

    private BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                processScanResults();
            } else {
                statusText.setText("WiFi scan failed");
                wifiProgressBar.setVisibility(View.GONE);
                refreshButton.setEnabled(true);
            }

            try {
                unregisterReceiver(this);
            } catch (IllegalArgumentException e) {
                // Already unregistered, ignore
            }
        }
    };

    private void processScanResults() {
        try {
            List<ScanResult> scanResults = wifiManager.getScanResults();
            Set<String> seenSSIDs = new HashSet<>();
            List<ScanResult> filtered24GHzNetworks = new ArrayList<>();

            for (ScanResult result : scanResults) {
                // Filter for 2.4GHz networks (channels 1-14, frequencies 2412-2484 MHz)
                boolean is24GHz = result.frequency >= 2412 && result.frequency <= 2484;

                // Skip if not 2.4GHz, hidden network, or already seen
                if (!is24GHz || result.SSID == null || result.SSID.isEmpty() || seenSSIDs.contains(result.SSID)) {
                    continue;
                }

                seenSSIDs.add(result.SSID);
                filtered24GHzNetworks.add(result);
            }

            // Sort by signal strength (stronger first)
            Collections.sort(filtered24GHzNetworks, new Comparator<ScanResult>() {
                @Override
                public int compare(ScanResult a, ScanResult b) {
                    return Integer.compare(b.level, a.level); // Higher level = stronger signal
                }
            });

            availableNetworks.clear();
            availableNetworks.addAll(filtered24GHzNetworks);

            // Update UI
            wifiAdapter.clear();
            for (ScanResult network : filtered24GHzNetworks) {
                String security = getSecurityType(network);
                String signalStrength = getSignalStrengthText(network.level);
                String displayText = String.format("%s\n%s • %s • Ch %d",
                        network.SSID, security, signalStrength, getChannel(network.frequency));
                wifiAdapter.add(displayText);
            }

            wifiAdapter.notifyDataSetChanged();
            wifiProgressBar.setVisibility(View.GONE);
            refreshButton.setEnabled(true);

            if (availableNetworks.isEmpty()) {
                statusText.setText("No 2.4GHz WiFi networks found.\nESP32 devices only support 2.4GHz networks.");
            } else {
                statusText.setText(String.format("Found %d 2.4GHz network(s). Select one to provision.",
                        availableNetworks.size()));
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception during scan result processing", e);
            statusText.setText("Permission error when reading WiFi scan results");
            wifiProgressBar.setVisibility(View.GONE);
            refreshButton.setEnabled(true);
        }
    }

    private String getSecurityType(ScanResult result) {
        String capabilities = result.capabilities;
        if (capabilities.contains("WPA3")) {
            return "WPA3";
        } else if (capabilities.contains("WPA2")) {
            return "WPA2";
        } else if (capabilities.contains("WPA")) {
            return "WPA";
        } else if (capabilities.contains("WEP")) {
            return "WEP";
        } else {
            return "Open";
        }
    }

    private String getSignalStrengthText(int level) {
        if (level >= -50) return "Excellent";
        else if (level >= -60) return "Good";
        else if (level >= -70) return "Fair";
        else return "Weak";
    }

    private int getChannel(int frequency) {
        if (frequency >= 2412 && frequency <= 2484) {
            return (frequency - 2412) / 5 + 1;
        }
        return 0;
    }

    private void showPasswordDialog() {
        if (selectedNetwork == null) return;

        String security = getSecurityType(selectedNetwork);

        if (security.equals("Open")) {
            // No password needed for open networks
            startProvisioning(selectedNetwork.SSID, "");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("WiFi Password");
        builder.setMessage("Enter password for: " + selectedNetwork.SSID);

        // Create input field
        final EditText passwordInput = new EditText(this);
        passwordInput.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setHint("WiFi Password");

        // Create show/hide password checkbox
        final CheckBox showPassword = new CheckBox(this);
        showPassword.setText("Show password");
        showPassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                passwordInput.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                passwordInput.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            passwordInput.setSelection(passwordInput.getText().length());
        });

        // Create vertical layout
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        layout.addView(passwordInput);
        layout.addView(showPassword);
        builder.setView(layout);

        builder.setPositiveButton("Provision", (dialog, which) -> {
            String password = passwordInput.getText().toString().trim();
            if (password.isEmpty() && !security.equals("Open")) {
                Toast.makeText(this, "Password cannot be empty for secured networks", Toast.LENGTH_SHORT).show();
                showPasswordDialog(); // Show dialog again
            } else {
                startProvisioning(selectedNetwork.SSID, password);
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void startProvisioning(String ssid, String password) {
        Log.d(TAG, "Starting BLE provisioning for SSID: " + ssid);

        // Switch to provisioning view
        showProvisioning();

        // Start BLE provisioning
        bleManager.startProvisioning(bluetoothDevice, ssid, password, new BLEProvisioningManager.ProvisioningCallback() {
            @Override
            public void onConnected() {
                Log.d(TAG, "BLE connected");
            }

            @Override
            public void onDisconnected() {
                Log.d(TAG, "BLE disconnected");
            }

            @Override
            public void onProvisioningStarted() {
                Log.d(TAG, "Provisioning started");
                provisioningProgressBar.setVisibility(View.VISIBLE);
                provisioningStatusText.setText("Starting provisioning...");
            }

            @Override
            public void onProvisioningProgress(String message) {
                Log.d(TAG, "Provisioning progress: " + message);
                provisioningStatusText.setText(message);
            }

            @Override
            public void onProvisioningSuccess() {
                Log.d(TAG, "Provisioning successful");
                provisioningProgressBar.setVisibility(View.GONE);
                provisioningStatusText.setText("✅ Provisioning completed successfully!\n\nYour ESP32 is now connected to: " + ssid);

                // Show success and return result
                handler.postDelayed(() -> {
                    try {
                        Toast.makeText(ProvisionActivity.this, "ESP32 provisioned successfully!", Toast.LENGTH_LONG).show();

                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("device_name", deviceName);
                        resultIntent.putExtra("device_address", deviceAddress);
                        resultIntent.putExtra("wifi_ssid", ssid);

                        Log.d(TAG, "Setting result with: name=" + deviceName + ", address=" + deviceAddress + ", ssid=" + ssid);

                        setResult(RESULT_OK, resultIntent);
                        finish();
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting result", e);
                        Toast.makeText(ProvisionActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }, 1000); // Reduced delay to 1 second
            }

            @Override
            public void onProvisioningFailed(String error) {
                Log.e(TAG, "Provisioning failed: " + error);
                provisioningProgressBar.setVisibility(View.GONE);
                provisioningStatusText.setText("❌ Provisioning failed:\n" + error + "\n\nTap back to try again.");

                Toast.makeText(ProvisionActivity.this, "Provisioning failed: " + error, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Provisioning error: " + error);
                provisioningProgressBar.setVisibility(View.GONE);
                provisioningStatusText.setText("❌ Error occurred:\n" + error + "\n\nTap back to try again.");

                Toast.makeText(ProvisionActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // If currently in provisioning view, go back to WiFi selection
            if (provisioningLayout.getVisibility() == View.VISIBLE) {
                bleManager.disconnect();
                showWiFiSelection();
                return true;
            } else {
                onBackPressed();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // If currently in provisioning view, go back to WiFi selection
        if (provisioningLayout.getVisibility() == View.VISIBLE) {
            bleManager.disconnect();
            showWiFiSelection();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered, ignore
        }

        if (bleManager != null) {
            bleManager.disconnect();
        }
    }
}