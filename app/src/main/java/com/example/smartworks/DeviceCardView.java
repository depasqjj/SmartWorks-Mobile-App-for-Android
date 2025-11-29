package com.example.smartworks;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DeviceCardView extends LinearLayout {
    private TextView deviceNameText;
    private TextView statusText;
    private TextView temperatureText;
    private TextView temperatureStatusText;
    private TextView ipAddressText;
    private ImageButton configButton;  // Changed from removeButton

    // Signal bar views
    private LinearLayout signalBars;
    private View signalBar1, signalBar2, signalBar3, signalBar4, signalBar5;

    private String deviceName;
    private String deviceAddress;
    private OnClickListener onConfigClickListener;  // Changed from onRemoveClickListener

    public DeviceCardView(Context context) {
        super(context);
        init();
    }

    public DeviceCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.device_card, this, true);

        deviceNameText = findViewById(R.id.deviceName);
        statusText = findViewById(R.id.statusText);  // Updated ID
        temperatureText = findViewById(R.id.temperature);
        temperatureStatusText = findViewById(R.id.temperatureStatus);
        ipAddressText = findViewById(R.id.ipAddress);
        configButton = findViewById(R.id.configButton);  // Changed from removeButton

        // Initialize signal bar views
        signalBars = findViewById(R.id.signalBars);
        signalBar1 = findViewById(R.id.signalBar1);
        signalBar2 = findViewById(R.id.signalBar2);
        signalBar3 = findViewById(R.id.signalBar3);
        signalBar4 = findViewById(R.id.signalBar4);
        signalBar5 = findViewById(R.id.signalBar5);

        configButton.setOnClickListener(v -> {
            if (onConfigClickListener != null) {
                onConfigClickListener.onClick(v);
            }
        });
    }

    public void setDeviceInfo(String name, String address) {
        this.deviceName = name;
        this.deviceAddress = address;
        deviceNameText.setText(name);
    }

    public void updateStatus(String status) {
        statusText.setText(status);

        // Update status color based on status text
        if (status.contains("Online")) {
            statusText.setTextColor(0xFF4CAF50); // Green
        } else if (status.contains("Connecting") || status.contains("Loading")) {
            statusText.setTextColor(0xFFFFC107); // Yellow
        } else {
            statusText.setTextColor(0xFFF44336); // Red
        }
    }

    public void updateTemperature(float tempF) {
        if (tempF == -999.0f) {
            temperatureText.setText("Temperature: --°F");
            temperatureStatusText.setText("Sensor: Error");
        } else {
            temperatureText.setText(String.format(Locale.US, "Temperature: %.1f°F", tempF));

            // Get user preferences for thresholds
            SharedPreferences prefs = getContext().getSharedPreferences("SmartWorksApp", Context.MODE_PRIVATE);
            float highThreshold = prefs.getFloat("high_threshold", 85.0f);
            float lowThreshold = prefs.getFloat("low_threshold", 60.0f);

            if (tempF >= highThreshold) {
                temperatureStatusText.setText("Sensor: Hot");
                temperatureText.setTextColor(0xFFF44336); // Red
            } else if (tempF <= lowThreshold) {
                temperatureStatusText.setText("Sensor: Cold");
                temperatureText.setTextColor(0xFF2196F3); // Blue
            } else {
                temperatureStatusText.setText("Sensor: OK");
                temperatureText.setTextColor(0xFF4CAF50); // Green
            }
        }
    }

    public void updateTemperatureCelsius(float tempC) {
        if (tempC == -999.0f) {
            temperatureText.setText("Temperature: --°C");
            temperatureStatusText.setText("Sensor: Error");
        } else {
            temperatureText.setText(String.format(Locale.US, "Temperature: %.1f°C", tempC));
            temperatureStatusText.setText("Sensor: OK");
            temperatureText.setTextColor(0xFF4CAF50); // Green
        }
    }

    public void updateNetworkInfo(String ipAddress, int rssi) {
        ipAddressText.setText("IP: " + (ipAddress != null ? ipAddress : "Discovering..."));

        // Update signal bars based on RSSI
        updateSignalBars(rssi);
    }

    private void updateSignalBars(int rssi) {
        // Reset all bars to gray (inactive)
        int inactiveColor = 0xFF999999; // Gray
        signalBar1.setBackgroundColor(inactiveColor);
        signalBar2.setBackgroundColor(inactiveColor);
        signalBar3.setBackgroundColor(inactiveColor);
        signalBar4.setBackgroundColor(inactiveColor);
        signalBar5.setBackgroundColor(inactiveColor);

        // Determine signal strength from RSSI
        int signalStrength = convertRSSIToStrength(rssi);

        // Colors for different signal levels
        int excellentColor = 0xFF4CAF50; // Green
        int goodColor = 0xFF8BC34A;      // Light Green
        int fairColor = 0xFFFFC107;      // Yellow
        int weakColor = 0xFFFF9800;      // Orange
        int veryWeakColor = 0xFFF44336;  // Red

        // Set appropriate bars based on signal strength (1-5)
        switch (signalStrength) {
            case 5: // Excellent (-50 dBm or better)
                signalBar1.setBackgroundColor(excellentColor);
                signalBar2.setBackgroundColor(excellentColor);
                signalBar3.setBackgroundColor(excellentColor);
                signalBar4.setBackgroundColor(excellentColor);
                signalBar5.setBackgroundColor(excellentColor);
                break;
            case 4: // Good (-50 to -60 dBm)
                signalBar1.setBackgroundColor(goodColor);
                signalBar2.setBackgroundColor(goodColor);
                signalBar3.setBackgroundColor(goodColor);
                signalBar4.setBackgroundColor(goodColor);
                break;
            case 3: // Fair (-60 to -70 dBm)
                signalBar1.setBackgroundColor(fairColor);
                signalBar2.setBackgroundColor(fairColor);
                signalBar3.setBackgroundColor(fairColor);
                break;
            case 2: // Weak (-70 to -80 dBm)
                signalBar1.setBackgroundColor(weakColor);
                signalBar2.setBackgroundColor(weakColor);
                break;
            case 1: // Very Weak (-80 dBm or worse)
                signalBar1.setBackgroundColor(veryWeakColor);
                break;
            case 0: // No signal/offline
            default:
                // All bars remain gray (inactive)
                break;
        }
    }

    private int convertRSSIToStrength(int rssi) {
        if (rssi == 0) return 0;    // No signal
        if (rssi >= -50) return 5;  // Excellent
        if (rssi >= -60) return 4;  // Good
        if (rssi >= -70) return 3;  // Fair
        if (rssi >= -80) return 2;  // Weak
        return 1; // Very weak
    }

    public void setOnConfigClickListener(OnClickListener listener) {
        this.onConfigClickListener = listener;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }
}