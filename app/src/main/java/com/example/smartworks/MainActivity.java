package com.example.smartworks;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.smartworks.api.SmartWorksApiService;
import com.example.smartworks.auth.AuthenticationManager;
import com.example.smartworks.auth.LoginActivity;
import com.example.smartworks.debug.SessionDebugger;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MainActivity with proper device management and provisioning
 */
public class MainActivity extends AppCompatActivity implements AuthenticationManager.AuthStateListener {
    private static final String TAG = "MainActivity";
    private static final int DEVICE_SCAN_REQUEST = 1;
    private static final int DEVICE_CONFIG_REQUEST = 2;

    private ListView devicesList;
    private View emptyStateText;
    private FloatingActionButton addDeviceFab;
    private SwipeRefreshLayout swipeRefreshLayout;

    private DeviceAdapter devicesAdapter;
    private List<DeviceAdapter.DeviceInfo> provisionedDevices;
    private ExecutorService executorService;
    private boolean isRefreshing = false;

    // Authentication
    private AuthenticationManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "MainActivity onCreate started");

        try {
            // Initialize authentication manager
            authManager = AuthenticationManager.getInstance(this);

            // Check if user is logged in
            Log.d(TAG, "Checking if user is logged in...");
            SessionDebugger.logSessionData(this);
            
            if (!authManager.isLoggedIn()) {
                Log.d(TAG, "User not logged in - redirecting to login");
                redirectToLogin();
                return;
            } else {
                Log.d(TAG, "User is logged in - proceeding with MainActivity");
            }

            setContentView(R.layout.activity_main);
            Log.d(TAG, "Layout set successfully");

            // Initialize executor service for background tasks
            executorService = Executors.newSingleThreadExecutor();

            // Setup action bar with user info
            setupActionBar();

            initializeViews();
            Log.d(TAG, "Views initialized successfully");

            // Data loading will be handled in onResume() -> validateSessionAndRefresh()
            Log.d(TAG, "Waiting for onResume to load data");

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error initializing app: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Validate session when app comes to foreground
        if (authManager.isLoggedIn()) {
            validateSessionAndRefresh();
        } else {
            redirectToLogin();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        if (devicesAdapter != null) {
            devicesAdapter.cleanup();
        }
        if (authManager != null) {
            authManager.removeAuthStateListener(this);
        }
    }

    private void setupActionBar() {
        if (getSupportActionBar() != null) {
            AuthenticationManager.User currentUser = authManager.getCurrentUser();
            String title = "SmartWorks";
            if (currentUser != null) {
                title += " - " + currentUser.username;
            }
            getSupportActionBar().setTitle(title);
        }
    }

    private void redirectToLogin() {
        Log.d(TAG, "Redirecting to login screen");
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void validateSessionAndRefresh() {
        authManager.validateSession()
                .thenAccept(valid -> runOnUiThread(() -> {
                    if (!valid) {
                        Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show();
                        redirectToLogin();
                    } else {
                        // Session is valid, refresh device data
                        loadProvisionedDevicesFromLocal();
                    }
                }))
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        Log.e(TAG, "Session validation failed", throwable);
                        Toast.makeText(this, "Unable to validate session", Toast.LENGTH_SHORT).show();
                    });
                    return null;
                });
    }

    private void initializeViews() {
        try {
            devicesList = findViewById(R.id.devicesList);
            emptyStateText = findViewById(R.id.emptyStateText);
            addDeviceFab = findViewById(R.id.addDeviceFab);
            swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

            if (devicesList == null || emptyStateText == null || addDeviceFab == null || swipeRefreshLayout == null) {
                throw new RuntimeException("Failed to find required views in layout");
            }

            // Initialize devices list
            provisionedDevices = new ArrayList<>();
            devicesAdapter = new DeviceAdapter(this, provisionedDevices);

            // Set device config listener
            devicesAdapter.setOnDeviceConfigListener((device, position) -> {
                Intent intent = new Intent(MainActivity.this, DeviceConfigActivity.class);
                intent.putExtra("device_name", device.name);
                intent.putExtra("device_address", device.address);
                intent.putExtra("device_ip", device.ipAddress);
                intent.putExtra("wifi_ssid", device.wifiSSID);
                intent.putExtra("firmware_version", device.firmwareVersion); // ADDED
                intent.putExtra("device_position", position);
                startActivityForResult(intent, DEVICE_CONFIG_REQUEST);
            });

            devicesList.setAdapter(devicesAdapter);

            // Set up SwipeRefreshLayout
            swipeRefreshLayout.setColorSchemeResources(
                    android.R.color.holo_blue_bright,
                    android.R.color.holo_green_light,
                    android.R.color.holo_orange_light,
                    android.R.color.holo_red_light
            );
            swipeRefreshLayout.setOnRefreshListener(() -> {
                Log.d(TAG, "Pull-to-refresh triggered");
                loadDevicesFromServer();
            });

            // Set up FAB click listener - Show options menu
            addDeviceFab.setOnClickListener(v -> {
                Log.d(TAG, "Add device button clicked");
                showAddDeviceOptions();
            });

            // Set up device list item click listener
            devicesList.setOnItemClickListener((parent, view, position, id) -> {
                try {
                    DeviceAdapter.DeviceInfo device = provisionedDevices.get(position);

                    // Get IP address from adapter
                    String ipAddress = devicesAdapter.getDeviceIP(position);
                    if (ipAddress != null && !ipAddress.equals("Not Found") && !ipAddress.equals("Discovering...")) {
                        // Open web interface
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW);
                        browserIntent.setData(android.net.Uri.parse("http://" + ipAddress));
                        startActivity(browserIntent);
                    } else {
                        // Show manual IP entry dialog
                        showManualIPDialog(device, position);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling device click", e);
                    Toast.makeText(MainActivity.this, "Error opening device: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

            // Set up device list long click for device management
            devicesList.setOnItemLongClickListener((parent, view, position, id) -> {
                try {
                    DeviceAdapter.DeviceInfo device = provisionedDevices.get(position);
                    showDeviceManagementDialog(device, position);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Error handling device long click", e);
                    return false;
                }
            });

            updateEmptyState();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
            Toast.makeText(this, "Error setting up interface: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Add refresh option to the action bar menu
        menu.add(0, 1, 0, "Refresh")
                .setIcon(android.R.drawable.ic_popup_sync)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        // Add find devices option
        menu.add(0, 2, 1, "Find Devices")
                .setIcon(android.R.drawable.ic_menu_search)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        // Add Pool Monitor option
        menu.add(0, 3, 2, "Pool Monitor")
                .setIcon(android.R.drawable.ic_menu_view)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        // Add Settings option
        menu.add(0, 4, 3, "Settings")
                .setIcon(android.R.drawable.ic_menu_preferences)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        // Add Logout option
        menu.add(0, 5, 4, "Logout")
                .setIcon(android.R.drawable.ic_menu_close_clear_cancel)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1: // Refresh
                loadDevicesFromServer();
                return true;
            case 2: // Find Devices
                forceIPRediscovery();
                return true;
            case 3: // Pool Monitor
                Intent poolIntent = new Intent(this, PoolMonitorActivity.class);
                startActivity(poolIntent);
                return true;
            case 4: // Settings
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case 5: // Logout
                showLogoutConfirmation();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showLogoutConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    performLogout();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        authManager.logout();
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        redirectToLogin();
    }

    private void forceIPRediscovery() {
        if (isRefreshing) {
            Toast.makeText(this, "Refresh already in progress...", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Force IP rediscovery triggered");

        if (provisionedDevices.isEmpty()) {
            Toast.makeText(this, "No devices to find", Toast.LENGTH_SHORT).show();
            showProgress(false);
            return;
        }

        isRefreshing = true;
        showProgress(true);

        // Show rediscovery message
        Toast.makeText(this, "Searching for devices on network...", Toast.LENGTH_LONG).show();

        // Force IP rediscovery in adapter
        if (devicesAdapter != null) {
            devicesAdapter.forceIPRediscovery();
        }

        // Set a timer for completion
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isRefreshing = false;
            showProgress(false);
            Toast.makeText(MainActivity.this, "Device search completed", Toast.LENGTH_SHORT).show();
        }, 10000); // 10 seconds for IP discovery
    }

    private void loadProvisionedDevicesFromLocal() {
        // Try loading from server first for real-time data
        loadDevicesFromServer();
    }

    /**
     * Load devices from server API with real-time status
     * Falls back to local storage if server is unavailable
     */
    private void loadDevicesFromServer() {
        Log.d(TAG, "Loading devices from server...");
        
        if (!authManager.isLoggedIn()) {
            Log.d(TAG, "Not logged in, skipping server load");
            loadDevicesFromLocalStorageOnly();
            return;
        }

        // Show loading state
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }

        // Initialize API service
        SmartWorksApiService apiService = SmartWorksApiService.getInstance(authManager);

        apiService.getUserDevices()
                .thenAccept(result -> runOnUiThread(() -> {
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }

                    if (result.success && result.data != null) {
                        // Success - update device list from server
                        provisionedDevices.clear();

                        for (SmartWorksApiService.Device serverDevice : result.data) {
                            // Convert server Device to DeviceAdapter.DeviceInfo
                            DeviceAdapter.DeviceInfo deviceInfo = new DeviceAdapter.DeviceInfo(
                                    serverDevice.friendlyName,
                                    serverDevice.deviceId,
                                    serverDevice.wifiSsid
                            );

                            // Set data from server
                            deviceInfo.ipAddress = serverDevice.ipAddress;
                            deviceInfo.temperature = serverDevice.temperature != null ?
                                    String.format("%.1f", serverDevice.temperature) : "N/A";
                            deviceInfo.status = serverDevice.statusMessage != null ?
                                    serverDevice.statusMessage : serverDevice.status;
                            deviceInfo.rssi = serverDevice.rssi;
                            deviceInfo.firmwareVersion = serverDevice.firmwareVersion; // ADDED

                            provisionedDevices.add(deviceInfo);
                        }

                        if (devicesAdapter != null) {
                            devicesAdapter.notifyDataSetChanged();
                        }

                        updateEmptyState();
                        Log.d(TAG, "Loaded " + provisionedDevices.size() + " devices from server");

                        // Cache to local storage for offline use
                        cacheDevicesToLocal(result.data);

                    } else {
                        // Server failed - fall back to local storage
                        Log.w(TAG, "Failed to load from server: " + result.message);
                        
                        // Show error to user so they know why data might be stale
                        if (result.message != null && !result.message.isEmpty()) {
                            Toast.makeText(this, "Sync failed: " + result.message, Toast.LENGTH_SHORT).show();
                        }
                        
                        if (result.message != null && (result.message.contains("Authentication required") || result.message.contains("Invalid authentication"))) {
                            Toast.makeText(this, "Session expired. Please Log Out and Log In again.", Toast.LENGTH_LONG).show();
                        } else {
                            Log.w(TAG, "Falling back to local storage");
                            loadDevicesFromLocalStorageOnly();
                        }
                    }
                }))
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        if (swipeRefreshLayout != null) {
                            swipeRefreshLayout.setRefreshing(false);
                        }
                        Log.e(TAG, "Error loading devices from server", throwable);
                        Toast.makeText(this, "Loading from cache - server unavailable", Toast.LENGTH_SHORT).show();
                        loadDevicesFromLocalStorageOnly();
                    });
                    return null;
                });
    }

    /**
     * Load devices only from local SharedPreferences (offline mode)
     */
    private void loadDevicesFromLocalStorageOnly() {
        try {
            // Load devices from SharedPreferences
            SharedPreferences prefs = getSharedPreferences("SmartWorks", Context.MODE_PRIVATE);
            Set<String> deviceSet = prefs.getStringSet("provisioned_devices", new HashSet<>());

            provisionedDevices.clear();
            if (deviceSet != null) {
                for (String deviceString : deviceSet) {
                    DeviceAdapter.DeviceInfo device = DeviceAdapter.DeviceInfo.fromStoredString(deviceString, this);
                    provisionedDevices.add(device);
                }
            }

            if (devicesAdapter != null) {
                devicesAdapter.notifyDataSetChanged();
            }

            Log.d(TAG, "Loaded " + provisionedDevices.size() + " devices from local storage");
            updateEmptyState();
        } catch (Exception e) {
            Log.e(TAG, "Error loading devices from local storage", e);
            Toast.makeText(this, "Error loading saved devices", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Cache server device data to local storage for offline access
     */
    private void cacheDevicesToLocal(List<SmartWorksApiService.Device> devices) {
        try {
            SharedPreferences prefs = getSharedPreferences("SmartWorks", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            Set<String> deviceSet = new HashSet<>();

            for (SmartWorksApiService.Device device : devices) {
                String deviceInfo = device.friendlyName;
                if (device.deviceId != null && !device.deviceId.trim().isEmpty()) {
                    deviceInfo += "\n" + device.deviceId;
                }
                if (device.wifiSsid != null && !device.wifiSsid.trim().isEmpty()) {
                    deviceInfo += "\nRouter: " + device.wifiSsid;
                }
                if (device.ipAddress != null && !device.ipAddress.trim().isEmpty()) {
                    deviceInfo += "\nIP: " + device.ipAddress;
                }
                if (device.temperature != null) {
                    deviceInfo += "\nTemperature: " + String.format("%.1f", device.temperature);
                }
                if (device.statusMessage != null) {
                    deviceInfo += "\nStatus: " + device.statusMessage;
                }
                if (device.firmwareVersion != null) { // ADDED
                    deviceInfo += "\nVersion: " + device.firmwareVersion;
                }

                deviceSet.add(deviceInfo);

                // Also cache IP separately for quick lookup
                if (device.ipAddress != null) {
                    editor.putString("device_ip_" + device.friendlyName, device.ipAddress);
                }
            }

            editor.putStringSet("provisioned_devices", deviceSet);
            editor.apply();

            Log.d(TAG, "Cached " + devices.size() + " devices to local storage");
        } catch (Exception e) {
            Log.e(TAG, "Error caching devices to local storage", e);
        }
    }

    private void updateEmptyState() {
        if (provisionedDevices.isEmpty()) {
            devicesList.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
        } else {
            devicesList.setVisibility(View.VISIBLE);
            emptyStateText.setVisibility(View.GONE);
        }
    }

    private void startDeviceScan() {
        Log.d(TAG, "Starting device scan activity");
        Intent intent = new Intent(this, DeviceScanActivity.class);
        startActivityForResult(intent, DEVICE_SCAN_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode + ", data=" + (data != null));

        try {
            if (requestCode == DEVICE_SCAN_REQUEST) {
                if (resultCode == RESULT_OK) {
                    if (data != null) {
                        handleProvisioningResult(data);
                    } else {
                        Log.w(TAG, "Provisioning successful but no data received");
                        Toast.makeText(this, "Device provisioned but details not available", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.d(TAG, "Provisioning was cancelled or failed (resultCode=" + resultCode + ")");
                }
            } else if (requestCode == DEVICE_CONFIG_REQUEST) {
                if (resultCode == RESULT_OK && data != null) {
                    handleDeviceConfigResult(data);
                } else {
                    // Reload devices to make sure we have latest data
                    loadProvisionedDevicesFromLocal();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onActivityResult", e);
            Toast.makeText(this, "Error processing result: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleProvisioningResult(Intent data) {
        try {
            String deviceName = data.getStringExtra("device_name");
            String deviceAddress = data.getStringExtra("device_address");
            String wifiSSID = data.getStringExtra("wifi_ssid");

            Log.d(TAG, "Processing provisioning result: name='" + deviceName + "', address='" + deviceAddress + "', ssid='" + wifiSSID + "'");

            // IMPROVED: Get the IP address stored during BLE provisioning
            SharedPreferences prefs = getSharedPreferences("SmartWorks", Context.MODE_PRIVATE);
            String deviceIP = prefs.getString("last_provisioned_ip", "");
            
            Log.d(TAG, "Retrieved provisioned IP: " + deviceIP);

            // Save device locally with discovered IP
            if (deviceAddress != null && !deviceAddress.trim().isEmpty()) {
                saveProvisionedDeviceLocally(deviceName != null ? deviceName : "ESP32 Device", deviceAddress, wifiSSID, deviceIP);
            } else {
                Toast.makeText(this, "Invalid device information received", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling provisioning result", e);
            Toast.makeText(this, "Error adding device: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveProvisionedDeviceLocally(String deviceName, String deviceAddress, String wifiSSID, String ipAddress) {
        try {
            SharedPreferences prefs = getSharedPreferences("SmartWorks", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            Set<String> deviceSet = new HashSet<>(prefs.getStringSet("provisioned_devices", new HashSet<>()));

            String deviceInfo = deviceName;
            if (deviceAddress != null && !deviceAddress.trim().isEmpty()) {
                deviceInfo += "\n" + deviceAddress;
            }
            if (wifiSSID != null && !wifiSSID.trim().isEmpty()) {
                deviceInfo += "\nRouter: " + wifiSSID;
            }
            // IMPROVED: Store the actual IP address from provisioning
            if (ipAddress != null && !ipAddress.trim().isEmpty()) {
                deviceInfo += "\nIP: " + ipAddress;
            }
            deviceInfo += "\nTemperature: Loading...";
            deviceInfo += "\nStatus: Online";

            deviceSet.removeIf(existing -> existing.startsWith(deviceName + "\n") || existing.equals(deviceName));
            deviceSet.add(deviceInfo);

            editor.putStringSet("provisioned_devices", deviceSet);
            
            // IMPORTANT: Also store IP with device-specific key for DeviceAdapter
            if (ipAddress != null && !ipAddress.trim().isEmpty()) {
                editor.putString("device_ip_" + deviceName, ipAddress);
            }
            
            editor.apply();

            // Add to current list
            DeviceAdapter.DeviceInfo newDevice = new DeviceAdapter.DeviceInfo(deviceName, deviceAddress, wifiSSID);
            if (ipAddress != null && !ipAddress.trim().isEmpty()) {
                newDevice.ipAddress = ipAddress; // Set the IP address directly
            }
            provisionedDevices.add(newDevice);

            if (devicesAdapter != null) {
                devicesAdapter.notifyDataSetChanged();
            }
            updateEmptyState();

            String successMessage = "Device '" + deviceName + "' added successfully!";
            if (ipAddress != null && !ipAddress.trim().isEmpty()) {
                successMessage += "\nIP: " + ipAddress;
            }
            Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
            Log.d(TAG, "Device saved to local storage with IP: " + ipAddress);

        } catch (Exception e) {
            Log.e(TAG, "Error saving device locally", e);
            Toast.makeText(this, "Error saving device", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleDeviceConfigResult(Intent data) {
        // Handle device configuration changes
        if (data.getBooleanExtra("device_deleted", false)) {
            loadProvisionedDevicesFromLocal();
            Toast.makeText(this, "Device deleted successfully", Toast.LENGTH_SHORT).show();
        } else if (data.hasExtra("updated_device_name")) {
            loadProvisionedDevicesFromLocal();
            Toast.makeText(this, "Device updated successfully", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeviceManagementDialog(DeviceAdapter.DeviceInfo device, int position) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Manage Device: " + device.name);
        builder.setMessage("What would you like to do with this device?");

        builder.setPositiveButton("Configure IP", (dialog, which) -> {
            showManualIPDialog(device, position);
        });

        builder.setNegativeButton("Delete Device", (dialog, which) -> {
            deleteDeviceConfirmation(device, position);
        });

        builder.setNeutralButton("Send Command", (dialog, which) -> {
            showCommandDialog(device);
        });

        builder.show();
    }

    private void deleteDeviceConfirmation(DeviceAdapter.DeviceInfo device, int position) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Device")
                .setMessage("Are you sure you want to delete \"" + device.name + "\"?\n\nThis will remove it from your account.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteDeviceLocally(device, position);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteDeviceLocally(DeviceAdapter.DeviceInfo device, int position) {
        try {
            provisionedDevices.remove(position);
            if (devicesAdapter != null) {
                devicesAdapter.notifyDataSetChanged();
            }
            updateEmptyState();

            // Remove from local storage too
            removeDeviceFromLocalStorage(device);

            Toast.makeText(this, "Device \"" + device.name + "\" deleted successfully", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Device deleted: " + device.name);

        } catch (Exception e) {
            Log.e(TAG, "Error deleting device", e);
            Toast.makeText(this, "Error deleting device: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void removeDeviceFromLocalStorage(DeviceAdapter.DeviceInfo device) {
        try {
            SharedPreferences prefs = getSharedPreferences("SmartWorks", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            Set<String> deviceSet = new HashSet<>(prefs.getStringSet("provisioned_devices", new HashSet<>()));
            deviceSet.removeIf(deviceString -> deviceString.startsWith(device.name + "\n") || deviceString.equals(device.name));

            editor.putStringSet("provisioned_devices", deviceSet);
            editor.apply();

        } catch (Exception e) {
            Log.e(TAG, "Error removing device from local storage", e);
        }
    }

    private void showCommandDialog(DeviceAdapter.DeviceInfo device) {
        String[] commands = {"Turn On", "Turn Off", "Get Status", "Reset"};

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Send Command to " + device.name)
                .setItems(commands, (dialog, which) -> {
                    String command;
                    switch (which) {
                        case 0: command = "turn_on"; break;
                        case 1: command = "turn_off"; break;
                        case 2: command = "get_status"; break;
                        case 3: command = "reset"; break;
                        default: return;
                    }

                    sendCommandToDevice(device, command);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendCommandToDevice(DeviceAdapter.DeviceInfo device, String command) {
        Toast.makeText(this, "Sending " + command + " to " + device.name, Toast.LENGTH_SHORT).show();
        // Command functionality can be implemented later
    }

    private void showManualIPDialog(DeviceAdapter.DeviceInfo device, int position) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Connect to " + device.name);
        builder.setMessage("Enter the IP address of your device:");

        final android.widget.EditText ipInput = new android.widget.EditText(this);
        ipInput.setHint("192.168.1.100");
        ipInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        layout.addView(ipInput);
        builder.setView(layout);

        builder.setPositiveButton("Test & Connect", (dialog, which) -> {
            String ipAddress = ipInput.getText().toString().trim();
            if (!ipAddress.isEmpty()) {
                testAndSetIP(ipAddress, device, position);
            } else {
                Toast.makeText(this, "Please enter an IP address", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void testAndSetIP(String ipAddress, DeviceAdapter.DeviceInfo device, int position) {
        executorService.execute(() -> {
            boolean success = false;
            try {
                java.net.URL url = new java.net.URL("http://" + ipAddress + "/data");
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("GET");
                int responseCode = connection.getResponseCode();
                connection.disconnect();

                if (responseCode == 200) {
                    success = true;
                    device.ipAddress = ipAddress;
                    device.status = "Manual IP Set";
                    device.temperature = "Loading...";

                    if (devicesAdapter != null) {
                        devicesAdapter.updateStoredDeviceIP(device, ipAddress);
                        devicesAdapter.fetchDeviceData(device, position);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Manual IP test failed: " + e.getMessage());
            }

            final boolean finalSuccess = success;
            runOnUiThread(() -> {
                if (finalSuccess) {
                    Toast.makeText(MainActivity.this, "IP address verified! Fetching device data...", Toast.LENGTH_SHORT).show();
                    if (devicesAdapter != null) {
                        devicesAdapter.notifyDataSetChanged();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Could not connect to " + ipAddress + ". Please check the IP address and device status.", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void showAddDeviceOptions() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Add Device");
        builder.setMessage("How would you like to add a device?");
        
        builder.setPositiveButton("📱 Scan for Devices", (dialog, which) -> {
            try {
                startDeviceScan();
            } catch (Exception e) {
                Log.e(TAG, "Error starting device scan", e);
                Toast.makeText(MainActivity.this, "Error starting device scan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("🌐 Add by IP Address", (dialog, which) -> {
            showAddManualDeviceDialog();
        });
        
        builder.setNeutralButton("Cancel", null);
        builder.show();
    }
    
    private void showAddManualDeviceDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Add Device by IP Address");
        builder.setMessage("Enter your ESP32 device information:");
        
        // Create input layout
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        // Device name input
        android.widget.TextView nameLabel = new android.widget.TextView(this);
        nameLabel.setText("Device Name:");
        nameLabel.setTextSize(14);
        layout.addView(nameLabel);
        
        final android.widget.EditText nameInput = new android.widget.EditText(this);
        nameInput.setHint("Pool Monitor");
        nameInput.setText("Pool Monitor");
        layout.addView(nameInput);
        
        // IP address input
        android.widget.TextView ipLabel = new android.widget.TextView(this);
        ipLabel.setText("\nIP Address:");
        ipLabel.setTextSize(14);
        layout.addView(ipLabel);
        
        final android.widget.EditText ipInput = new android.widget.EditText(this);
        ipInput.setHint("192.168.1.100 (Enter your ESP32's IP)");
        // FIXED: No hardcoded IP - let user enter their device's actual IP
        ipInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(ipInput);
        
        // WiFi SSID input (optional)
        android.widget.TextView wifiLabel = new android.widget.TextView(this);
        wifiLabel.setText("\nWiFi Network (optional):");
        wifiLabel.setTextSize(14);
        layout.addView(wifiLabel);
        
        final android.widget.EditText wifiInput = new android.widget.EditText(this);
        wifiInput.setHint("Your WiFi Name");
        layout.addView(wifiInput);
        
        builder.setView(layout);
        
        builder.setPositiveButton("Add Device", (dialog, which) -> {
            String deviceName = nameInput.getText().toString().trim();
            String ipAddress = ipInput.getText().toString().trim();
            String wifiSSID = wifiInput.getText().toString().trim();
            
            if (deviceName.isEmpty()) {
                deviceName = "ESP32 Device";
            }
            
            if (ipAddress.isEmpty()) {
                Toast.makeText(this, "Please enter an IP address", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Test connection and add device
            addManualDevice(deviceName, ipAddress, wifiSSID);
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void addManualDevice(String deviceName, String ipAddress, String wifiSSID) {
        // Show progress
        Toast.makeText(this, "Testing connection to " + ipAddress + "...", Toast.LENGTH_SHORT).show();
        
        executorService.execute(() -> {
            boolean success = false;
            String errorMessage = "";
            
            try {
                // Test connection to the device
                java.net.URL url = new java.net.URL("http://" + ipAddress + "/data");
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("GET");
                
                int responseCode = connection.getResponseCode();
                connection.disconnect();
                
                if (responseCode == 200) {
                    success = true;
                } else {
                    errorMessage = "HTTP " + responseCode;
                }
                
            } catch (java.net.ConnectException e) {
                errorMessage = "Connection refused - check IP address";
            } catch (java.net.SocketTimeoutException e) {
                errorMessage = "Connection timeout";
            } catch (Exception e) {
                errorMessage = "Connection failed: " + e.getMessage();
            }
            
            final boolean finalSuccess = success;
            final String finalError = errorMessage;
            
            runOnUiThread(() -> {
                if (finalSuccess) {
                    // Add device to the list
                    saveProvisionedDeviceManually(deviceName, ipAddress, wifiSSID);
                    Toast.makeText(this, "✅ Device '" + deviceName + "' added successfully!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "❌ Failed to connect to device: " + finalError, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
    
    private void saveProvisionedDeviceManually(String deviceName, String ipAddress, String wifiSSID) {
        try {
            SharedPreferences prefs = getSharedPreferences("SmartWorks", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            Set<String> deviceSet = new HashSet<>(prefs.getStringSet("provisioned_devices", new HashSet<>()));

            // Create device info string
            String deviceInfo = deviceName;
            deviceInfo += "\nIP: " + ipAddress;
            if (wifiSSID != null && !wifiSSID.trim().isEmpty()) {
                deviceInfo += "\nRouter: " + wifiSSID;
            }
            deviceInfo += "\nTemperature: Loading...";
            deviceInfo += "\nStatus: Online";

            // Remove any existing device with same name
            deviceSet.removeIf(existing -> existing.startsWith(deviceName + "\n") || existing.equals(deviceName));
            deviceSet.add(deviceInfo);

            editor.putStringSet("provisioned_devices", deviceSet);
            
            // Store IP with device-specific key for DeviceAdapter
            editor.putString("device_ip_" + deviceName, ipAddress);
            
            editor.apply();

            // Add to current list
            DeviceAdapter.DeviceInfo newDevice = new DeviceAdapter.DeviceInfo(deviceName, "", wifiSSID);
            newDevice.ipAddress = ipAddress; // Set the IP address directly
            provisionedDevices.add(newDevice);

            if (devicesAdapter != null) {
                devicesAdapter.notifyDataSetChanged();
            }
            updateEmptyState();

            Log.d(TAG, "Manual device added: " + deviceName + " at " + ipAddress);

        } catch (Exception e) {
            Log.e(TAG, "Error saving manual device", e);
            Toast.makeText(this, "Error saving device", Toast.LENGTH_SHORT).show();
        }
    }

    private void showProgress(boolean show) {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(show);
        }
    }

    // AuthStateListener implementation
    @Override
    public void onAuthStateChanged(boolean isLoggedIn, AuthenticationManager.User user) {
        if (!isLoggedIn) {
            redirectToLogin();
        } else {
            setupActionBar();
            loadProvisionedDevicesFromLocal();
        }
    }
}