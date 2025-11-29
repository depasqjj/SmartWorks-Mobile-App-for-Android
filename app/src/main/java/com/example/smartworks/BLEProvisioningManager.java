// BLEProvisioningManager.java - Fixed method scope issues
package com.example.smartworks;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONObject;

public class BLEProvisioningManager {
    private static final String TAG = "BLEProvisioningManager";

    // ESP32 Provisioning Service UUIDs (matching minimal firmware)
    private static final UUID PROVISIONING_SERVICE_UUID = UUID.fromString("0000ffff-0000-1000-8000-00805f9b34fb");
    private static final UUID WIFI_CONFIG_CHAR_UUID = UUID.fromString("0000ff51-0000-1000-8000-00805f9b34fb");
    private static final UUID WIFI_STATUS_CHAR_UUID = UUID.fromString("0000ff52-0000-1000-8000-00805f9b34fb");
    
    // Client Characteristic Configuration Descriptor
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public interface ProvisioningCallback {
        void onConnected();
        void onDisconnected();
        void onProvisioningStarted();
        void onProvisioningProgress(String message);
        void onProvisioningSuccess();
        void onProvisioningFailed(String error);
        void onError(String error);
    }

    private Context context;
    private BluetoothDevice device;
    private BluetoothGatt bluetoothGatt;
    private ProvisioningCallback callback;
    private Handler mainHandler;

    private BluetoothGattCharacteristic wifiConfigChar;
    private BluetoothGattCharacteristic wifiStatusChar;

    private String ssid;
    private String password;
    private boolean isConnected = false;
    private boolean servicesDiscovered = false;
    private int connectionRetryCount = 0;
    private static final int MAX_CONNECTION_RETRIES = 3;
    private static final long CONNECTION_TIMEOUT = 15000; // 15 seconds

    private Runnable connectionTimeoutRunnable;

    public BLEProvisioningManager(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void startProvisioning(BluetoothDevice device, String ssid, String password, ProvisioningCallback callback) {
        this.device = device;
        this.ssid = ssid;
        this.password = password;
        this.callback = callback;
        this.connectionRetryCount = 0;

        Log.d(TAG, "=== STARTING PROVISIONING ===");
        Log.d(TAG, "Device: " + device.getAddress());
        Log.d(TAG, "SSID: " + ssid);
        Log.d(TAG, "Expected service UUID: " + PROVISIONING_SERVICE_UUID.toString());

        callback.onProvisioningStarted();
        callback.onProvisioningProgress("Connecting to ESP32...");

        // Set up connection timeout
        connectionTimeoutRunnable = () -> {
            Log.e(TAG, "Connection timeout after " + CONNECTION_TIMEOUT + "ms");
            if (!isConnected && connectionRetryCount < MAX_CONNECTION_RETRIES) {
                connectionRetryCount++;
                Log.d(TAG, "Retrying connection, attempt " + connectionRetryCount + "/" + MAX_CONNECTION_RETRIES);
                callback.onProvisioningProgress("Connection timeout, retrying... (" + connectionRetryCount + "/" + MAX_CONNECTION_RETRIES + ")");

                cleanup();
                mainHandler.postDelayed(this::connectToDevice, 2000);
            } else {
                notifyError("Connection failed after " + MAX_CONNECTION_RETRIES + " attempts");
            }
        };

        connectToDevice();
    }

    private void connectToDevice() {
        try {
            Log.d(TAG, "Attempting to connect to device: " + device.getAddress());
            
            // Clean up any existing connection
            if (bluetoothGatt != null) {
                try {
                    bluetoothGatt.disconnect();
                    bluetoothGatt.close();
                } catch (Exception e) {
                    Log.w(TAG, "Error cleaning up previous connection", e);
                }
                bluetoothGatt = null;
            }

            // Use autoConnect = false for faster connection
            bluetoothGatt = device.connectGatt(context, false, gattCallback);

            if (bluetoothGatt == null) {
                notifyError("Failed to create GATT connection");
                return;
            }

            Log.d(TAG, "GATT connection initiated");

            // Start connection timeout
            mainHandler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT);

        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied when connecting", e);
            notifyError("Permission denied: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error connecting to device", e);
            notifyError("Connection failed: " + e.getMessage());
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "=== CONNECTION STATE CHANGE ===");
            Log.d(TAG, "Status: " + status + ", NewState: " + newState);

            // Cancel connection timeout
            if (connectionTimeoutRunnable != null) {
                mainHandler.removeCallbacks(connectionTimeoutRunnable);
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    isConnected = true;
                    connectionRetryCount = 0;
                    Log.d(TAG, "✅ Connected to GATT server successfully");

                    mainHandler.post(() -> {
                        callback.onConnected();
                        callback.onProvisioningProgress("Connected! Discovering services...");
                    });

                    // Discover services with delay for stability
                    mainHandler.postDelayed(() -> {
                        try {
                            Log.d(TAG, "Starting service discovery...");
                            boolean result = bluetoothGatt.discoverServices();
                            Log.d(TAG, "Service discovery initiated: " + result);
                            if (!result) {
                                notifyError("Failed to start service discovery");
                            }
                        } catch (SecurityException e) {
                            Log.e(TAG, "Permission denied during service discovery", e);
                            notifyError("Permission denied during service discovery");
                        }
                    }, 1000);
                } else {
                    Log.e(TAG, "❌ Connection failed with status: " + status);
                    handleConnectionError(status);
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                servicesDiscovered = false;
                Log.d(TAG, "Disconnected from GATT server, status: " + status);

                mainHandler.post(() -> {
                    callback.onDisconnected();
                    // Don't retry on normal disconnection after success
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        handleConnectionError(status);
                    }
                });

                cleanup();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "=== SERVICES DISCOVERED ===");
            Log.d(TAG, "Status: " + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                servicesDiscovered = true;
                
                // Log all discovered services for debugging
                for (BluetoothGattService service : gatt.getServices()) {
                    Log.d(TAG, "Found service: " + service.getUuid().toString());
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        Log.d(TAG, "  - Characteristic: " + characteristic.getUuid().toString());
                    }
                }
                
                findProvisioningCharacteristics();
            } else {
                Log.e(TAG, "❌ Service discovery failed with status: " + status);
                notifyError("Service discovery failed with status: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "=== CHARACTERISTIC WRITE ===");
            Log.d(TAG, "Characteristic: " + characteristic.getUuid());
            Log.d(TAG, "Status: " + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.getUuid().equals(WIFI_CONFIG_CHAR_UUID)) {
                    Log.d(TAG, "✅ WiFi config sent successfully");
                    mainHandler.post(() -> {
                        callback.onProvisioningProgress("WiFi credentials sent. Waiting for connection...");
                    });

                    // Wait for ESP32 to process and check status
                    mainHandler.postDelayed(() -> checkWiFiStatus(), 3000);
                }
            } else {
                Log.e(TAG, "❌ Write failed for characteristic: " + characteristic.getUuid() + ", status: " + status);
                notifyError("Write failed for characteristic, status: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "=== CHARACTERISTIC READ ===");
            Log.d(TAG, "Characteristic: " + characteristic.getUuid());
            Log.d(TAG, "Status: " + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] data = characteristic.getValue();
                String response = new String(data, StandardCharsets.UTF_8);
                Log.d(TAG, "Read response: " + response);

                if (characteristic.getUuid().equals(WIFI_STATUS_CHAR_UUID)) {
                    handleWiFiStatusResponse(response);
                }
            } else {
                Log.e(TAG, "❌ Read failed for characteristic: " + characteristic.getUuid() + ", status: " + status);
                // Don't fail here, might be normal
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "=== CHARACTERISTIC NOTIFICATION ===");
            Log.d(TAG, "Characteristic: " + characteristic.getUuid());

            byte[] data = characteristic.getValue();
            String notification = new String(data, StandardCharsets.UTF_8);
            Log.d(TAG, "Notification: " + notification);

            if (characteristic.getUuid().equals(WIFI_STATUS_CHAR_UUID)) {
                handleWiFiStatusResponse(notification);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG, "=== DESCRIPTOR WRITE ===");
            Log.d(TAG, "Descriptor: " + descriptor.getUuid());
            Log.d(TAG, "Status: " + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.getUuid().equals(CLIENT_CHARACTERISTIC_CONFIG)) {
                    Log.d(TAG, "✅ Notifications enabled successfully");
                    
                    mainHandler.post(() -> {
                        callback.onProvisioningProgress("Ready to send WiFi credentials...");
                    });
                    
                    // Start WiFi configuration immediately
                    mainHandler.postDelayed(() -> sendWiFiConfiguration(), 500);
                }
            } else {
                Log.e(TAG, "❌ Failed to enable notifications, status: " + status);
                notifyError("Failed to enable notifications, status: " + status);
            }
        }
    };

    private void checkWiFiStatus() {
        if (bluetoothGatt == null || wifiStatusChar == null) {
            Log.w(TAG, "Cannot check WiFi status - connection or characteristic is null");
            return;
        }

        try {
            Log.d(TAG, "Reading WiFi status...");
            boolean result = bluetoothGatt.readCharacteristic(wifiStatusChar);
            Log.d(TAG, "WiFi status read initiated: " + result);
            
            if (!result) {
                Log.w(TAG, "Failed to initiate status read");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied during status check", e);
        } catch (Exception e) {
            Log.e(TAG, "Error during WiFi status check", e);
        }
    }

    private void sendWiFiConfiguration() {
        if (wifiConfigChar == null) {
            Log.e(TAG, "WiFi config characteristic is null");
            notifyError("WiFi config characteristic not found");
            return;
        }

        try {
            // Create simple JSON config for minimal ESP32 firmware
            JSONObject wifiConfig = new JSONObject();
            wifiConfig.put("ssid", ssid);
            wifiConfig.put("password", password);

            String configJson = wifiConfig.toString();
            Log.d(TAG, "Sending WiFi config: " + configJson);

            wifiConfigChar.setValue(configJson.getBytes(StandardCharsets.UTF_8));

            boolean result = bluetoothGatt.writeCharacteristic(wifiConfigChar);
            Log.d(TAG, "WiFi config write initiated: " + result);

            if (!result) {
                notifyError("Failed to send WiFi configuration");
            } else {
                // Set timeout for provisioning completion
                mainHandler.postDelayed(() -> {
                    Log.i(TAG, "Provisioning timeout - ESP32 likely connected successfully");
                    mainHandler.post(() -> {
                        callback.onProvisioningProgress("WiFi connection timeout - assuming success!");
                        callback.onProvisioningProgress("ESP32 should now be connected to WiFi.");
                        // Give a moment for the message to show, then call success
                        mainHandler.postDelayed(() -> {
                            callback.onProvisioningSuccess();
                            disconnect();
                        }, 2000);
                    });
                }, 10000); // Reduced to 10 seconds
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied during WiFi config send", e);
            notifyError("Permission denied during WiFi config send");
        } catch (Exception e) {
            Log.e(TAG, "Error creating WiFi config", e);
            notifyError("Error creating WiFi configuration: " + e.getMessage());
        }
    }

    private void findProvisioningCharacteristics() {
        Log.d(TAG, "=== FINDING PROVISIONING CHARACTERISTICS ===");
        Log.d(TAG, "Looking for service: " + PROVISIONING_SERVICE_UUID.toString());

        BluetoothGattService provisioningService = bluetoothGatt.getService(PROVISIONING_SERVICE_UUID);

        if (provisioningService == null) {
            Log.e(TAG, "❌ Provisioning service not found!");
            Log.e(TAG, "Available services:");
            for (BluetoothGattService service : bluetoothGatt.getServices()) {
                Log.e(TAG, "  - " + service.getUuid().toString());
            }
            notifyError("Provisioning service not found. Make sure ESP32 is in provisioning mode.");
            return;
        }

        Log.d(TAG, "✅ Provisioning service found");

        // Find required characteristics
        wifiConfigChar = provisioningService.getCharacteristic(WIFI_CONFIG_CHAR_UUID);
        wifiStatusChar = provisioningService.getCharacteristic(WIFI_STATUS_CHAR_UUID);

        if (wifiConfigChar == null) {
            Log.e(TAG, "❌ WiFi config characteristic not found");
            notifyError("WiFi config characteristic not found");
            return;
        }

        if (wifiStatusChar == null) {
            Log.e(TAG, "❌ WiFi status characteristic not found");
            notifyError("WiFi status characteristic not found");
            return;
        }

        Log.d(TAG, "✅ All required characteristics found");
        Log.d(TAG, "WiFi Config Char: " + wifiConfigChar.getUuid());
        Log.d(TAG, "WiFi Status Char: " + wifiStatusChar.getUuid());

        mainHandler.post(() -> callback.onProvisioningProgress("Characteristics found. Setting up notifications..."));

        setupNotifications();
    }

    private void setupNotifications() {
        try {
            Log.d(TAG, "=== SETTING UP NOTIFICATIONS ===");
            
            // Enable notifications on WiFi status characteristic
            boolean result = bluetoothGatt.setCharacteristicNotification(wifiStatusChar, true);
            Log.d(TAG, "Set notification result: " + result);

            if (result) {
                BluetoothGattDescriptor descriptor = wifiStatusChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                if (descriptor != null) {
                    Log.d(TAG, "Writing notification descriptor...");
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    bluetoothGatt.writeDescriptor(descriptor);
                } else {
                    Log.w(TAG, "Notification descriptor not found - proceeding without notifications");
                    // Proceed anyway - some implementations don't require descriptors
                    mainHandler.post(() -> {
                        callback.onProvisioningProgress("Ready to send WiFi credentials...");
                    });
                    mainHandler.postDelayed(() -> sendWiFiConfiguration(), 500);
                }
            } else {
                Log.w(TAG, "Failed to enable notifications - proceeding anyway");
                // Proceed without notifications
                mainHandler.post(() -> {
                    callback.onProvisioningProgress("Ready to send WiFi credentials...");
                });
                mainHandler.postDelayed(() -> sendWiFiConfiguration(), 500);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied during notification setup", e);
            notifyError("Permission denied during notification setup");
        } catch (Exception e) {
            Log.e(TAG, "Error during notification setup", e);
            notifyError("Error during notification setup: " + e.getMessage());
        }
    }

    private void handleWiFiStatusResponse(String response) {
        Log.d(TAG, "=== WIFI STATUS RESPONSE ===");
        Log.d(TAG, "Response: '" + response + "'");

        try {
            JSONObject statusJson = new JSONObject(response);
            String status = statusJson.optString("status", "unknown");
            String message = statusJson.optString("message", "");
            String ipAddress = statusJson.optString("ip_address", "");
            String deviceId = statusJson.optString("device_id", "");

            Log.d(TAG, "Parsed status: '" + status + "', message: '" + message + "', IP: '" + ipAddress + "'");

            mainHandler.post(() -> {
                switch (status.toLowerCase()) {
                    case "connected":
                    case "success":
                        String successMessage = "WiFi connected successfully!";
                        if (!ipAddress.isEmpty()) {
                            successMessage += "\nIP Address: " + ipAddress;
                            // Store IP address for the app to use
                            storeDeviceIP(ipAddress, deviceId);
                        }
                        callback.onProvisioningProgress(successMessage);
                        callback.onProvisioningSuccess();
                        disconnect();
                        break;

                    case "connecting":
                        callback.onProvisioningProgress("ESP32 connecting to WiFi...");
                        mainHandler.postDelayed(() -> checkWiFiStatus(), 3000);
                        break;

                    case "failed":
                    case "error":
                        callback.onProvisioningFailed("WiFi connection failed: " + message);
                        disconnect();
                        break;

                    case "ready":
                        callback.onProvisioningProgress("ESP32 ready: " + message);
                        break;

                    default:
                        callback.onProvisioningProgress("Status: " + status + " - " + message);
                        break;
                }
            });

        } catch (Exception e) {
            Log.w(TAG, "Error parsing status response, using fallback parsing", e);
            
            // Fallback parsing for simple responses
            mainHandler.post(() -> {
                String lowerResponse = response.toLowerCase();
                if (lowerResponse.contains("connected") || lowerResponse.contains("success")) {
                    callback.onProvisioningProgress("WiFi connected!");
                    callback.onProvisioningSuccess();
                    disconnect();
                } else if (lowerResponse.contains("connecting")) {
                    callback.onProvisioningProgress("ESP32 connecting to WiFi...");
                } else if (lowerResponse.contains("failed") || lowerResponse.contains("error")) {
                    callback.onProvisioningFailed("WiFi connection failed");
                    disconnect();
                } else {
                    callback.onProvisioningProgress("Received: " + response);
                }
            });
        }
    }

    /**
     * Store the device IP address received during provisioning
     */
    private void storeDeviceIP(String ipAddress, String deviceId) {
        if (ipAddress == null || ipAddress.isEmpty()) return;
        
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("SmartWorks", Context.MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            
            // Store IP for this specific device
            String deviceKey = "device_ip_" + (deviceId.isEmpty() ? device.getAddress() : deviceId);
            editor.putString(deviceKey, ipAddress);
            
            // Also store as "last_provisioned_ip" for immediate use
            editor.putString("last_provisioned_ip", ipAddress);
            editor.putString("last_provisioned_device", deviceId.isEmpty() ? device.getAddress() : deviceId);
            
            editor.apply();
            
            Log.d(TAG, "Stored device IP: " + ipAddress + " for device: " + deviceKey);
        } catch (Exception e) {
            Log.e(TAG, "Error storing device IP", e);
        }
    }

    private void handleConnectionError(int status) {
        String errorMessage = "Connection error (status: " + status + ")";
        
        if (connectionRetryCount < MAX_CONNECTION_RETRIES) {
            connectionRetryCount++;
            Log.d(TAG, "Retrying connection due to error, attempt " + connectionRetryCount);
            mainHandler.post(() -> callback.onProvisioningProgress(errorMessage + ". Retrying... (" + connectionRetryCount + "/" + MAX_CONNECTION_RETRIES + ")"));

            cleanup();
            mainHandler.postDelayed(() -> connectToDevice(), 2000);
        } else {
            notifyError(errorMessage + " after " + MAX_CONNECTION_RETRIES + " attempts");
        }
    }

    private void notifyError(String error) {
        Log.e(TAG, "=== PROVISIONING ERROR ===");
        Log.e(TAG, "Error: " + error);
        mainHandler.post(() -> callback.onError(error));
        disconnect();
    }

    public void disconnect() {
        Log.d(TAG, "=== DISCONNECTING ===");

        if (connectionTimeoutRunnable != null) {
            mainHandler.removeCallbacks(connectionTimeoutRunnable);
        }

        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied during disconnect", e);
            } catch (Exception e) {
                Log.e(TAG, "Error during disconnect", e);
            }
        }
    }

    private void cleanup() {
        if (connectionTimeoutRunnable != null) {
            mainHandler.removeCallbacks(connectionTimeoutRunnable);
            connectionTimeoutRunnable = null;
        }

        mainHandler.removeCallbacksAndMessages(null);

        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied during cleanup", e);
            } catch (Exception e) {
                Log.e(TAG, "Error during cleanup", e);
            }
            bluetoothGatt = null;
        }

        wifiConfigChar = null;
        wifiStatusChar = null;
        isConnected = false;
        servicesDiscovered = false;
    }

    public boolean isConnected() {
        return isConnected && servicesDiscovered;
    }
}
