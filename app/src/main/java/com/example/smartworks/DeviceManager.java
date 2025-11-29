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
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class DeviceManager {
    private static final String TAG = "DeviceManager";

    // UUIDs from ESP32 code
    private static final UUID PROVISIONING_SERVICE_UUID = UUID.fromString("0000aadb-0000-1000-8000-00805f9b34fb");
    private static final UUID WIFI_CONFIG_CHARACTERISTIC_UUID = UUID.fromString("0000aadc-0000-1000-8000-00805f9b34fb");
    private static final UUID WIFI_STATUS_CHARACTERISTIC_UUID = UUID.fromString("0000aadd-0000-1000-8000-00805f9b34fb");
    private static final UUID TEMP_CHARACTERISTIC_UUID = UUID.fromString("0000aadf-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private Context context;
    private String deviceAddress;
    private DeviceCardView deviceCard;
    private BluetoothGatt bluetoothGatt;
    private Handler mainHandler;

    private BluetoothGattCharacteristic wifiStatusChar;
    private BluetoothGattCharacteristic tempChar;

    public DeviceManager(Context context, String deviceAddress, DeviceCardView deviceCard) {
        this.context = context;
        this.deviceAddress = deviceAddress;
        this.deviceCard = deviceCard;
        this.mainHandler = new Handler(Looper.getMainLooper());

        connectToDevice();
    }

    private void connectToDevice() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = adapter.getRemoteDevice(deviceAddress);

        deviceCard.updateStatus("Connecting");

        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied when connecting to device", e);
            deviceCard.updateStatus("Permission Error");
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            mainHandler.post(() -> {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to GATT server");
                    deviceCard.updateStatus("Connected");
                    try {
                        bluetoothGatt.discoverServices();
                    } catch (SecurityException e) {
                        Log.e(TAG, "Permission denied when discovering services", e);
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from GATT server");
                    deviceCard.updateStatus("Disconnected");
                }
            });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered");
                setupCharacteristics();
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristicRead(characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            handleCharacteristicRead(characteristic);
        }
    };

    private void setupCharacteristics() {
        BluetoothGattService service = bluetoothGatt.getService(PROVISIONING_SERVICE_UUID);
        if (service == null) {
            Log.e(TAG, "Provisioning service not found");
            return;
        }

        wifiStatusChar = service.getCharacteristic(WIFI_STATUS_CHARACTERISTIC_UUID);
        tempChar = service.getCharacteristic(TEMP_CHARACTERISTIC_UUID);

        // Enable notifications for WiFi status
        if (wifiStatusChar != null) {
            try {
                bluetoothGatt.setCharacteristicNotification(wifiStatusChar, true);
                BluetoothGattDescriptor descriptor = wifiStatusChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    bluetoothGatt.writeDescriptor(descriptor);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied when setting up WiFi status notifications", e);
            }
        }

        // Enable notifications for temperature
        if (tempChar != null) {
            try {
                bluetoothGatt.setCharacteristicNotification(tempChar, true);
                BluetoothGattDescriptor descriptor = tempChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    bluetoothGatt.writeDescriptor(descriptor);
                }

                // Read initial temperature
                bluetoothGatt.readCharacteristic(tempChar);
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied when setting up temperature notifications", e);
            }
        }
    }

    private void handleCharacteristicRead(BluetoothGattCharacteristic characteristic) {
        byte[] data = characteristic.getValue();
        if (data == null) return;

        String value = new String(data);

        mainHandler.post(() -> {
            if (characteristic.getUuid().equals(WIFI_STATUS_CHARACTERISTIC_UUID)) {
                handleWifiStatusUpdate(value);
            } else if (characteristic.getUuid().equals(TEMP_CHARACTERISTIC_UUID)) {
                handleTemperatureUpdate(value);
            }
        });
    }

    private void handleWifiStatusUpdate(String statusJson) {
        try {
            JSONObject status = new JSONObject(statusJson);
            String statusStr = status.optString("status", statusJson);

            if ("connected".equals(statusStr)) {
                deviceCard.updateStatus("Connected");
                String ip = status.optString("ip", null);
                int rssi = status.optInt("rssi", 0);
                deviceCard.updateNetworkInfo(ip, rssi);
            } else if ("connecting".equals(statusStr)) {
                deviceCard.updateStatus("Connecting");
            } else {
                deviceCard.updateStatus("WiFi Error");
            }
        } catch (JSONException e) {
            // Not JSON, treat as plain status
            deviceCard.updateStatus(statusJson);
        }
    }

    private void handleTemperatureUpdate(String tempJson) {
        try {
            JSONObject tempData = new JSONObject(tempJson);
            float tempF = (float) tempData.optDouble("temp_f", -999.0);
            deviceCard.updateTemperature(tempF);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing temperature data", e);
        }
    }

    public void refreshDevice() {
        if (bluetoothGatt != null && tempChar != null) {
            try {
                bluetoothGatt.readCharacteristic(tempChar);
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied when refreshing device", e);
            }
        }
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }

    public void cleanup() {
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied when closing GATT", e);
            }
            bluetoothGatt = null;
        }
    }
}