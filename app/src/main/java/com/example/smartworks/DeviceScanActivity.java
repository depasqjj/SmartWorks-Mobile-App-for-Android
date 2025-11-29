package com.example.smartworks;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class DeviceScanActivity extends AppCompatActivity {
    private static final String TAG = "DeviceScanActivity";
    private static final long SCAN_PERIOD = 20000; // 20 seconds
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int BLUETOOTH_ENABLE_REQUEST = 2;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner leScanner;
    private Handler handler;
    private boolean scanning;

    private ListView devicesList;
    private ProgressBar progressBar;
    private TextView statusText;
    private Button rescanButton;

    private ArrayAdapter<String> devicesAdapter;
    private List<BluetoothDevice> foundDevices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "DeviceScanActivity onCreate started");

        try {
            setContentView(R.layout.activity_device_scan);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle("Scan for Devices");
            }

            initializeViews();
            initializeBluetooth();

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Failed to initialize scanner: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initializeViews() {
        devicesList = findViewById(R.id.devicesList);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        rescanButton = findViewById(R.id.rescanButton);

        foundDevices = new ArrayList<>();
        devicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        devicesList.setAdapter(devicesAdapter);

        devicesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < foundDevices.size()) {
                    BluetoothDevice device = foundDevices.get(position);
                    selectDevice(device);
                }
            }
        });

        if (rescanButton != null) {
            rescanButton.setOnClickListener(v -> startScanning());
        }

        statusText.setText("Tap 'Scan' to search for ESP32 devices");
    }

    private void initializeBluetooth() {
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null) {
                Log.e(TAG, "Bluetooth not supported");
                showError("Bluetooth not supported on this device");
                return;
            }

            if (!bluetoothAdapter.isEnabled()) {
                Log.d(TAG, "Requesting Bluetooth enable");
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, BLUETOOTH_ENABLE_REQUEST);
                return;
            }

            handler = new Handler();
            Log.d(TAG, "Bluetooth initialization successful");

            // Start permission check and scanning process
            checkPermissionsAndStartScan();

        } catch (Exception e) {
            Log.e(TAG, "Error initializing Bluetooth", e);
            showError("Bluetooth initialization failed: " + e.getMessage());
        }
    }

    private void checkPermissionsAndStartScan() {
        Log.d(TAG, "Checking permissions");
        
        if (checkAndRequestPermissions()) {
            Log.d(TAG, "All permissions granted, starting scan");
            startScanning();
        } else {
            Log.d(TAG, "Requesting permissions");
            statusText.setText("Requesting Bluetooth permissions...");
        }
    }

    private boolean checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        // Check permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
                Log.d(TAG, "Need BLUETOOTH_SCAN permission");
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
                Log.d(TAG, "Need BLUETOOTH_CONNECT permission");
            }
        } else {
            // Android 11 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
                Log.d(TAG, "Need ACCESS_FINE_LOCATION permission");
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
                Log.d(TAG, "Need ACCESS_COARSE_LOCATION permission");
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            Log.d(TAG, "Requesting permissions: " + permissionsNeeded);
            ActivityCompat.requestPermissions(this, 
                permissionsNeeded.toArray(new String[0]), 
                PERMISSION_REQUEST_CODE);
            return false;
        }

        Log.d(TAG, "All permissions already granted");
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            
            for (int i = 0; i < permissions.length; i++) {
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                Log.d(TAG, "Permission " + permissions[i] + ": " + (granted ? "GRANTED" : "DENIED"));
                
                if (!granted) {
                    allGranted = false;
                    
                    // Check if user denied permanently
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                        Log.w(TAG, "Permission permanently denied: " + permissions[i]);
                    }
                }
            }

            if (allGranted) {
                Log.d(TAG, "All permissions granted, starting scan");
                startScanning();
            } else {
                Log.e(TAG, "Some permissions denied");
                showPermissionError();
            }
        }
    }

    private void showPermissionError() {
        String message = "Bluetooth permissions are required to scan for devices.\n\n" +
                        "Please grant the requested permissions and try again.";
        
        statusText.setText(message);
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("This app needs Bluetooth permissions to scan for ESP32 devices.\n\n" +
                           "Would you like to grant permissions now?")
                .setPositiveButton("Grant Permissions", (dialog, which) -> {
                    checkPermissionsAndStartScan();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    finish();
                })
                .show();
    }

    private void startScanning() {
        Log.d(TAG, "Starting BLE scan");
        
        if (scanning) {
            Log.d(TAG, "Already scanning");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth not enabled");
            showError("Please enable Bluetooth and try again");
            return;
        }

        // Check location services for older Android versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            boolean locationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!locationEnabled) {
                Log.w(TAG, "Location services not enabled");
                showLocationDialog();
                return;
            }
        }

        try {
            leScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (leScanner == null) {
                Log.e(TAG, "BLE scanner is null");
                showError("BLE scanning not available");
                return;
            }

            // Clear previous results
            foundDevices.clear();
            devicesAdapter.clear();
            devicesAdapter.notifyDataSetChanged();

            // Show progress
            progressBar.setVisibility(View.VISIBLE);
            statusText.setText("Scanning for ESP32 devices...\nMake sure your ESP32 is in provisioning mode.");
            if (rescanButton != null) {
                rescanButton.setText("Scanning...");
                rescanButton.setEnabled(false);
            }

            // Configure scan settings
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .build();

            scanning = true;
            
            // Start scanning
            leScanner.startScan(null, settings, scanCallback);
            Log.d(TAG, "BLE scan started successfully");

            // Stop scanning after timeout
            handler.postDelayed(() -> {
                Log.d(TAG, "Scan timeout reached");
                stopScanning();
            }, SCAN_PERIOD);

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception during scan", e);
            showError("Permission error: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error starting scan", e);
            showError("Scan failed: " + e.getMessage());
        }
    }

    private void showLocationDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Location Services Required")
                .setMessage("Android requires location services to be enabled for Bluetooth scanning.\n\n" +
                           "Would you like to enable location services?")
                .setPositiveButton("Enable", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void stopScanning() {
        Log.d(TAG, "Stopping BLE scan");
        
        if (!scanning) {
            return;
        }

        scanning = false;
        progressBar.setVisibility(View.GONE);
        
        if (rescanButton != null) {
            rescanButton.setText("Scan Again");
            rescanButton.setEnabled(true);
        }

        try {
            if (leScanner != null) {
                leScanner.stopScan(scanCallback);
                Log.d(TAG, "BLE scan stopped");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception stopping scan", e);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping scan", e);
        }

        updateScanResults();
    }

    private void updateScanResults() {
        int deviceCount = foundDevices.size();
        Log.d(TAG, "Scan completed. Found " + deviceCount + " devices");

        if (deviceCount == 0) {
            statusText.setText("No ESP32 devices found.\n\n" +
                              "Make sure your ESP32 is:\n" +
                              "• Powered on\n" +
                              "• In provisioning mode\n" +
                              "• Within Bluetooth range");
        } else {
            statusText.setText("Found " + deviceCount + " device(s).\n\n" +
                              "Tap a device to start provisioning.");
        }
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            try {
                BluetoothDevice device = result.getDevice();
                
                // Avoid duplicates
                if (foundDevices.contains(device)) {
                    return;
                }

                String deviceName = getDeviceName(device);
                String address = device.getAddress();
                int rssi = result.getRssi();

                Log.d(TAG, "Found device: " + deviceName + " (" + address + ") RSSI: " + rssi);

                // Add device to list
                foundDevices.add(device);
                
                runOnUiThread(() -> {
                    String displayName = (deviceName != null ? deviceName : "Unknown Device") + 
                                       "\n" + address + 
                                       "\nRSSI: " + rssi + " dBm";
                    devicesAdapter.add(displayName);
                    devicesAdapter.notifyDataSetChanged();
                    
                    statusText.setText("Found " + foundDevices.size() + " device(s)...\n" +
                                      "Scanning continues...");
                });

            } catch (Exception e) {
                Log.e(TAG, "Error processing scan result", e);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            String errorMsg = getScanErrorMessage(errorCode);
            Log.e(TAG, "Scan failed with error code: " + errorCode + " - " + errorMsg);
            
            runOnUiThread(() -> {
                scanning = false;
                progressBar.setVisibility(View.GONE);
                if (rescanButton != null) {
                    rescanButton.setText("Try Again");
                    rescanButton.setEnabled(true);
                }
                showError("Scan failed: " + errorMsg);
            });
        }
    };

    private String getDeviceName(BluetoothDevice device) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                    == PackageManager.PERMISSION_GRANTED) {
                    return device.getName();
                }
            } else {
                return device.getName();
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Permission denied getting device name", e);
        }
        return null;
    }

    private String getScanErrorMessage(int errorCode) {
        switch (errorCode) {
            case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                return "Scan already started";
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                return "App registration failed";
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                return "BLE scanning not supported";
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                return "Internal Bluetooth error";
            default:
                return "Unknown error (code " + errorCode + ")";
        }
    }

    private void selectDevice(BluetoothDevice device) {
        stopScanning();

        String deviceName = getDeviceName(device);
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = "ESP32-Device";
        }

        Log.d(TAG, "Selected device: " + deviceName + " (" + device.getAddress() + ")");

        // Start ProvisionActivity for proper WiFi configuration
        Intent intent = new Intent(this, ProvisionActivity.class);
        intent.putExtra("device_name", deviceName);
        intent.putExtra("device_address", device.getAddress());
        startActivityForResult(intent, 1);
    }

    private void showError(String message) {
        statusText.setText("Error: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode + ", data=" + (data != null));
        
        if (requestCode == 1) { // ProvisionActivity result
            if (resultCode == RESULT_OK && data != null) {
                // Provisioning was successful, pass result back to MainActivity
                Log.d(TAG, "Provisioning successful, returning to MainActivity");
                setResult(RESULT_OK, data);
                finish();
            } else {
                // Provisioning failed or was cancelled
                Log.d(TAG, "Provisioning failed or cancelled (resultCode=" + resultCode + ")");
                Toast.makeText(this, "Provisioning was not completed", Toast.LENGTH_SHORT).show();
                // Stay on scan activity to allow retry
            }
        } else if (requestCode == BLUETOOTH_ENABLE_REQUEST) {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                Log.d(TAG, "Bluetooth enabled by user");
                checkPermissionsAndStartScan();
            } else {
                Log.e(TAG, "User denied Bluetooth enable");
                showError("Bluetooth is required for device scanning");
                finish();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "DeviceScanActivity destroyed");
        stopScanning();
    }
}